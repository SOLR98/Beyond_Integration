package com.solr98.beyondintegration.feature.ammo.tacz;

import com.mojang.logging.LogUtils;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * TACZ 1.2.0 弹药库方块（{@code tacz:ammo_storage}）插入拦截处理器。
 * <p>弹药库方块物品带 {@code BlockEntityTag.Entries}（每条目 = {@code Stack} 样本 + {@code Count} 总数）：
 * 入网时按条目把弹药物品入账网络（数量 × 方块数），空弹药库方块原样入账，避免整库被当作单一方块物品保存。
 * <p>使用注册名判断而非直接引用 TACZ 1.2.0 新类，保证低版本 TACZ（1.1.x，无该方块）不触发类加载。
 */
public class AmmoStorageExtractHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 方块实体 NBT 键（与 AmmoStorageInventory.save/load 一致）。 */
    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final String ENTRIES_TAG = "Entries";
    private static final String STACK_TAG = "Stack";
    private static final String COUNT_TAG = "Count";

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            DimensionsNet net) {

        if (!com.solr98.beyondintegration.handler.ReentrancyGuard.enter(
                "tacz_ammo_storage", com.solr98.beyondintegration.CommandConfig.handlerMaxReentrancy())) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }
        try {
            return beyond$extract(originalInsert, tryInsert, net);
        } finally {
            com.solr98.beyondintegration.handler.ReentrancyGuard.exit("tacz_ammo_storage");
        }
    }

    private @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beyond$extract(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            DimensionsNet net) {

        if (!(tryInsert.key() instanceof ItemStackKey itemStackKey)) {
            return passthrough(tryInsert);
        }

        ItemStack stack = itemStackKey.copyStackWithCount(1);
        if (stack.isEmpty() || !isAmmoStorage(stack) || net == null) {
            return passthrough(tryInsert);
        }

        long stackCount = originalInsert.amount();
        if (stackCount <= 0) {
            return passthrough(tryInsert);
        }

        CompoundTag blockEntityTag = stack.getTagElement(BLOCK_ENTITY_TAG);
        if (blockEntityTag == null) {
            // 空弹药库（无方块实体数据）：按普通方块物品放行
            return passthrough(tryInsert);
        }

        ListTag entries = blockEntityTag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        if (entries.isEmpty()) {
            return passthrough(tryInsert);
        }

        // 1) 按条目把弹药入网：条目总数 × 方块物品数量
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ItemStack sample = ItemStack.of(entry.getCompound(STACK_TAG));
            long count = entry.getLong(COUNT_TAG);
            if (sample.isEmpty() || count <= 0L) continue;

            long total = saturatedMultiply(count, stackCount);
            ItemStackKey ammoKey = new ItemStackKey(sample.copyWithCount(1));
            long left = net.getUnifiedStorage().insert(ammoKey, total, false).amount();
            if (left > 0L) {
                LOGGER.warn("AmmoStorageExtract: network capacity insufficient, {} ammo ({}) could not be stored",
                        left, ForgeRegistries.ITEMS.getKey(sample.getItem()));
            }
        }

        // 2) 空弹药库方块物品入网（移除方块实体数据）
        ItemStack emptyBlock = stack.copyWithCount(1);
        emptyBlock.removeTagKey(BLOCK_ENTITY_TAG);
        ItemStackKey emptyKey = new ItemStackKey(emptyBlock);
        long blockLeft = net.getUnifiedStorage().insert(emptyKey, stackCount, false).amount();
        if (blockLeft > 0L) {
            LOGGER.warn("AmmoStorageExtract: network slot capacity insufficient, {} empty ammo storages could not be stored",
                    blockLeft);
        }

        net.setDirty();

        // 吞掉原插入
        ItemStackKey emptyKeyAmount = new ItemStackKey(ItemStack.EMPTY);
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(emptyKeyAmount, 0), false);
    }

    /** 弹药库方块实体 ID（BlockEntityTag.id，所有容量变体共用）。 */
    private static final String AMMO_STORAGE_BE_ID = "tacz:ammo_storage";

    /** 是否为 TACZ 弹药库方块物品（按注册名/方块实体 ID 判断，避免低版本 TACZ 的类加载）。 */
    private static boolean isAmmoStorage(ItemStack stack) {
        // 物品注册名：tacz:ammo_storage / tacz:ammo_storage_gold / tacz:ammo_storage_diamond
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && "tacz".equals(id.getNamespace()) && id.getPath().startsWith("ammo_storage")) {
            return true;
        }
        // 退路：BlockEntityTag.id 为 tacz:ammo_storage（覆盖变体物品）
        CompoundTag blockEntityTag = stack.getTagElement(BLOCK_ENTITY_TAG);
        return blockEntityTag != null
                && blockEntityTag.contains("id", Tag.TAG_STRING)
                && AMMO_STORAGE_BE_ID.equals(blockEntityTag.getString("id"));
    }

    /** 饱和乘法（避免 long 溢出）。 */
    private static long saturatedMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) return 0L;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo passthrough(KeyAmount tryInsert) {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
    }
}
