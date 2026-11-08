package com.solr98.beyondintegration.feature.netconfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * 维度网络方块“配置 NBT”提取：从方块实体取出配置（标记槽/过滤 + 方块模式），
 * 剔除内容缓冲、计时、经验与网络绑定，得到可写入掉落物/手上物品的 {@code BlockEntityTag}。
 */
public final class NetedBlockConfigNbt {

    /** 不保留的数据键（内容缓冲 / 计时 / 经验 / 网络绑定） */
    private static final List<String> DATA_KEYS = List.of(
            "netId",
            "inventory",
            "input_storage_slots",
            "output_storage_slots",
            "fuel_storage_slots",
            "fuel_return_slots",
            "lit_time",
            "lit_duration",
            "cook_time",
            "cook_time_total",
            "stored_experience",
            "step_tick");

    private NetedBlockConfigNbt() {}

    /** 构建仅含配置的 NBT（含 {@code netId=-1}）。 */
    public static CompoundTag build(BlockEntity blockEntity) {
        CompoundTag data = blockEntity.saveWithoutMetadata();
        for (String key : DATA_KEYS) data.remove(key);
        data.putInt("netId", -1);
        return data;
    }

    /** 把方块配置写入物品（覆盖已有 {@code BlockEntityTag}）。 */
    public static void applyToStack(ItemStack stack, BlockEntity blockEntity) {
        if (stack == null || stack.isEmpty()) return;
        CompoundTag data = build(blockEntity);
        if (data.isEmpty()) return;
        stack.getOrCreateTag().put("BlockEntityTag", data);
    }

    // 供 mixin 使用同一键表
    public static List<String> dataKeys() {
        return DATA_KEYS;
    }
}
