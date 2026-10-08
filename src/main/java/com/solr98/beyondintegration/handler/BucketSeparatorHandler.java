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
import net.neoforged.neoforge.fluids.FluidStack;
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

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (!com.solr98.beyondintegration.CommandConfig.bucketSeparatorEnabled()) return pass(tryInsert);
        if (net == null) return pass(tryInsert);
        if (!(tryInsert.key() instanceof ItemStackKey itemKey)) return pass(tryInsert);

        ItemStack stack = itemKey.copyStackWithCount(1);
        if (stack.isEmpty()) return pass(tryInsert);

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

        // 容量预检：任一放不下则保持原样插入
        if (!storage.insert(fluidKey, fluidTotal, true).isEmpty()) return pass(tryInsert);
        if (!storage.insert(emptyKey, count, true).isEmpty()) return pass(tryInsert);

        storage.insert(fluidKey, fluidTotal, false);
        storage.insert(emptyKey, count, false);
        net.setDirty();

        // 已拆解完毕：返回空键表示本次插入无需写入原始容器
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(new ItemStackKey(ItemStack.EMPTY), 0), false);
    }

    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo pass(KeyAmount tryInsert) {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
    }
}
