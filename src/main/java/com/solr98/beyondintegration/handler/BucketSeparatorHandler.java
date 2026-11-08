package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.feeder.ThirstBridge;
import com.solr98.beyondintegration.init.ModFluids;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertPlan;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertRule;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.StorageRequest;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.UnifiedStorageRules;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 桶入网自动分离规则：在含流体的容器（桶/水瓶）进入维度网络时，
 * 把内容流体拆入网络流体存储、空容器（如空桶 / 玻璃瓶）作为物品存入网络，原容器本身不再写入。
 * <p>
 * 水类容器带 Thirst 水纯度标签时，会拆成<b>对应纯度的水流体</b>（本模组 4 档水），实现与
 * 终端取水（{@code BdBucketFluidSlotMixin}）的反向操作。
 * <p>
 * 受 {@code bucket_separator_enabled} 配置限制：关闭时完全不处理。
 * 容量不足时由事务回退（整单位不成立 → 原样退回请求），不会丢失物品。
 */
public final class BucketSeparatorHandler implements InsertRule
{
    public static final ResourceLocation ID = new ResourceLocation("beyond_integration", "bucket_separator");

    /**
     * 注册为插入规则。谓词只做"可能是容器"的便宜判断，细节留到 {@link #plan}。
     */
    public static void register()
    {
        UnifiedStorageRules.insertMatching(ID, BucketSeparatorHandler::matches, new BucketSeparatorHandler());
        // 入网流体纯度规范化：带纯度的原版水 → 对应档位的 BI 流体（与拆桶产出保持同一种表示）
        WaterFluidNormalizeRule.register();
    }

    private static boolean matches(IStackKey<?> key)
    {
        if (!CommandConfig.bucketSeparatorEnabled()) return false;
        if (!(key instanceof ItemStackKey itemKey)) return false;
        ItemStack stack = itemKey.getReadOnlyStack();
        if (stack.isEmpty()) return false;
        if (isExcluded(stack)) return false;
        if (stack.getItem() instanceof BucketItem) return true;
        if (BucketFluidHelper.isWaterBottle(stack)) return true;
        // 非"仅限桶"模式下，其他模组容器也可能带流体
        return !CommandConfig.bucketSeparatorBucketOnly();
    }

    @Override
    public @Nullable InsertPlan plan(StorageRequest request)
    {
        if (!CommandConfig.bucketSeparatorEnabled()) return null;
        if (request.net() == null) return null;
        if (!(request.key() instanceof ItemStackKey itemKey)) return null;

        ItemStack stack = itemKey.copyStackWithCount(1);
        if (stack.isEmpty()) return null;

        // 仅限桶物品：开启时非 BucketItem（如水瓶/其他模组容器）不参与拆分
        if (CommandConfig.bucketSeparatorBucketOnly() && !(stack.getItem() instanceof BucketItem))
        {
            return null;
        }

        // 排除项：命中的物品不拆分（如森罗酒馆的果汁桶，避免把瓶装酒倒出）
        if (isExcluded(stack)) return null;

        // 容器内流体（无流体则放行：普通物品/空桶等；牛奶桶按标签/注册名匹配网络中的牛奶流体）
        FluidStack fluid = BucketFluidHelper.getContainedFluid(request.net().getUnifiedStorage(), stack);
        ItemStack empty = BucketFluidHelper.emptyContainerOf(stack);

        // 水瓶（药水）没有流体 capability：特判为水 + 玻璃瓶
        if (fluid.isEmpty() && BucketFluidHelper.isWaterBottle(stack))
        {
            fluid = new FluidStack(Fluids.WATER, (int) BucketFluidHelper.MB_PER_BOTTLE);
            empty = new ItemStack(Items.GLASS_BOTTLE);
        }

        if (fluid.isEmpty() || fluid.getAmount() <= 0) return null;
        if (empty.isEmpty()) return null;

        // 水类容器带纯度标签时，映射回对应纯度的水流体（反向操作）
        Fluid keyFluid = fluid.getFluid();
        if (keyFluid == Fluids.WATER)
        {
            int purity = ThirstBridge.get().readPurity(stack);
            if (purity >= 0)
            {
                Fluid tier = ModFluids.tierFluid(purity);
                if (tier != null) keyFluid = tier;
            }
        }

        FluidStackKey fluidKey = new FluidStackKey(new FluidStack(keyFluid, fluid.getAmount()));
        ItemStackKey emptyKey = new ItemStackKey(empty.copyWithCount(1));

        // 每个容器拆成"流体 + 空容器"，自身不存入；零头由事务退回
        return InsertPlan.convert(1)
                .add(fluidKey, fluid.getAmount())
                .add(emptyKey, 1);
    }

    /**
     * 是否命中"桶入网自动分离"排除项（{@code bd_tweaks.bucket_separator_excluded_items}）。
     * 条目为物品注册名，支持省略命名空间（自动补 {@code minecraft:}）与 {@code namespace:*} 通配。
     */
    private static boolean isExcluded(ItemStack stack)
    {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        String full = id.toString();
        String namespace = id.getNamespace();
        for (String raw : CommandConfig.bucketSeparatorExcludedItems())
        {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (entry.endsWith(":*"))
            {
                if (namespace.equals(entry.substring(0, entry.length() - 2))) return true;
                continue;
            }
            if (!entry.contains(":")) entry = "minecraft:" + entry;
            if (entry.equals(full)) return true;
        }
        return false;
    }
}
