package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 桶入网自动分离处理器：在含流体的容器（桶）进入维度网络统一存储前拦截，
 * 把内容流体拆入网络流体存储、空容器（如空桶）作为物品存入网络，原容器本身不再写入。
 * <p>
 * 容量预检不通过（网络放不下全部流体/空容器）时保持原样插入，避免物品丢失；
 * 空容器由 {@code getCraftingRemainingItem} 推导（原版桶回退为 {@code minecraft:bucket}），
 * 无空容器的物品不处理。
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
        if (fluid.isEmpty() || fluid.getAmount() <= 0) return pass(tryInsert);

        // 空容器：优先合成剩余物；原版桶/牛奶桶回退为空桶
        ItemStack empty = BucketFluidHelper.emptyContainerOf(stack);
        if (empty.isEmpty()) return pass(tryInsert);

        long count = originalInsert.amount();
        if (count <= 0) return pass(tryInsert);

        var storage = net.getUnifiedStorage();
        FluidStackKey fluidKey = new FluidStackKey(fluid.copyWithAmount(1));
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
