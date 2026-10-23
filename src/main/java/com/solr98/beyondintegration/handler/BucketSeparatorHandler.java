package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

/**
 * 桶入网自动分离处理器：在含流体的容器（桶/水瓶）进入维度网络统一存储前拦截，
 * 把内容流体拆入网络流体存储、空容器（如空桶 / 玻璃瓶）作为物品存入网络，原容器本身不再写入。
 * <p>
 * 水类容器带 Thirst 水纯度标签时，会拆成<b>对应纯度的水流体</b>（本模组 4 档水），实现与
 * 终端取水（{@code BdBucketFluidSlotMixin}）的反向操作。
 * <p>
 * 受 {@code bucket_separator_enabled} 配置限制：关闭时完全不处理。
 * 容量预检不通过时保持原样插入，避免物品丢失。
 */
public class BucketSeparatorHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    /**
     * 防重入：本处理器在 {@code beforeInsert} 内自行 {@code storage.insert(...)} 写入拆分产物时，
     * 插入会再次触发拦截链并回到本处理器 → 死循环（{@code StackOverflowError}）。
     * 置真期间本处理器直接放行，避免递归。
     */
    private static final ThreadLocal<Boolean> beyond$separating =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (beyond$separating.get()) return pass(tryInsert); // 自身拆分插入的产物，不再处理
        // 拦截链重入上限（低于附魔书）：达上限即放行，防止过深/环形递归
        if (!ReentrancyGuard.enter("bucket", com.solr98.beyondintegration.CommandConfig.handlerMaxReentrancy())) {
            return pass(tryInsert);
        }
        try {
            return beyond$split(originalInsert, tryInsert, net);
        } finally {
            ReentrancyGuard.exit("bucket");
        }
    }

    private @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beyond$split(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (!com.solr98.beyondintegration.CommandConfig.bucketSeparatorEnabled()) return pass(tryInsert);
        if (net == null) return pass(tryInsert);
        if (!(tryInsert.key() instanceof ItemStackKey itemKey)) return pass(tryInsert);

        ItemStack stack = itemKey.copyStackWithCount(1);
        if (stack.isEmpty()) return pass(tryInsert);

        // 仅限桶物品：开启时非 BucketItem（如水瓶/其他模组容器）不参与拆分
        if (com.solr98.beyondintegration.CommandConfig.bucketSeparatorBucketOnly()
                && !(stack.getItem() instanceof net.minecraft.world.item.BucketItem)) {
            return pass(tryInsert);
        }

        // 排除项：命中的物品不拆分（如森罗酒馆的果汁桶，避免把瓶装酒倒出）
        if (isExcluded(stack)) return pass(tryInsert);

        // 容器内流体（无流体则放行：普通物品/空桶等；牛奶桶按标签/注册名匹配网络中的牛奶流体）
        FluidStack fluid = BucketFluidHelper.getContainedFluid(net.getUnifiedStorage(), stack);
        ItemStack empty = BucketFluidHelper.emptyContainerOf(stack);

        // 水瓶（药水）没有流体 capability：特判为水 + 玻璃瓶
        if (fluid.isEmpty() && BucketFluidHelper.isWaterBottle(stack)) {
            fluid = new FluidStack(Fluids.WATER, (int) BucketFluidHelper.MB_PER_BOTTLE);
            empty = new ItemStack(Items.GLASS_BOTTLE);
        }

        if (fluid.isEmpty() || fluid.getAmount() <= 0) return pass(tryInsert);
        if (empty.isEmpty()) return pass(tryInsert);

        long count = originalInsert.amount();
        if (count <= 0) return pass(tryInsert);

        var storage = net.getUnifiedStorage();

        // 水类容器带纯度标签时，映射回对应纯度的水流体（反向操作）
        Fluid keyFluid = fluid.getFluid();
        if (keyFluid == Fluids.WATER) {
            int purity = com.solr98.beyondintegration.feature.feeder.ThirstBridge.get().readPurity(stack);
            if (purity >= 0) {
                Fluid tier = com.solr98.beyondintegration.init.ModFluids.tierFluid(purity);
                if (tier != null) keyFluid = tier;
            }
        }

        FluidStackKey fluidKey = new FluidStackKey(new FluidStack(keyFluid, 1));
        ItemStackKey emptyKey = new ItemStackKey(empty.copyWithCount(1));
        long fluidTotal = (long) fluid.getAmount() * count;

        // 拆分产物的写入会再次触发拦截链：置防重入守卫，避免本处理器递归
        beyond$separating.set(Boolean.TRUE);
        try {
            // 容量预检：任一放不下则保持原样插入
            if (!storage.insert(fluidKey, fluidTotal, true).isEmpty()) return pass(tryInsert);
            if (!storage.insert(emptyKey, count, true).isEmpty()) return pass(tryInsert);

            storage.insert(fluidKey, fluidTotal, false);
            storage.insert(emptyKey, count, false);
        } finally {
            beyond$separating.set(Boolean.FALSE);
        }
        net.setDirty();

        // 已拆解完毕：返回空键表示本次插入无需写入原始容器
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(new ItemStackKey(ItemStack.EMPTY), 0), false);
    }

    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo pass(KeyAmount tryInsert) {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
    }

    /**
     * 是否命中"桶入网自动分离"排除项（{@code bd_tweaks.bucket_separator_excluded_items}）。
     * 条目为物品注册名，支持省略命名空间（自动补 {@code minecraft:}）与 {@code namespace:*} 通配。
     */
    private static boolean isExcluded(ItemStack stack) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        String full = id.toString();
        String namespace = id.getNamespace();
        for (String raw : com.solr98.beyondintegration.CommandConfig.bucketSeparatorExcludedItems()) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (entry.endsWith(":*")) {
                if (namespace.equals(entry.substring(0, entry.length() - 2))) return true;
                continue;
            }
            if (!entry.contains(":")) entry = "minecraft:" + entry;
            if (entry.equals(full)) return true;
        }
        return false;
    }
}
