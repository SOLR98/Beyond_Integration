package com.solr98.beyondintegration.feature.netconfig;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * 维度网络方块“配置 NBT”提取（1.21.1）：从方块实体取出配置并剔除内容/计时/经验/网络绑定。
 */
public final class NetedBlockConfigNbt {

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

    public static CompoundTag build(BlockEntity blockEntity, Provider registries) {
        CompoundTag data = blockEntity.saveWithoutMetadata(registries);
        for (String key : DATA_KEYS) data.remove(key);
        data.putInt("netId", -1);
        return data;
    }

    public static void applyToStack(ItemStack stack, BlockEntity blockEntity, Provider registries) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) return;
        CompoundTag data = build(blockEntity, registries);
        if (data.isEmpty()) return;
        BlockItem.setBlockEntityData(stack, blockEntity.getType(), data);
    }

    public static List<String> dataKeys() {
        return DATA_KEYS;
    }
}
