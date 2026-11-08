package com.solr98.beyondintegration.client.compat;

import com.solr98.beyondintegration.network.TudMappingSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TUD 映射表客户端缓存（服务端下发）。
 * <p>解决 TUD 自身不下发映射数据（JSON 只在本地 {@code config/tacz_unidict}）导致
 * "客户端解析枪的弹药与服务端不一致"的问题：镜像可用性判断与客户端弹药解析都以这张表为准。
 */
public final class TudMappingClient {

    private static volatile long tableVersion = -1L;
    /** gunId → 弹药列表（顺序与 availableAmmo 一致，NBT 索引取值） */
    private static final Map<ResourceLocation, List<TudMappingSyncPacket.Ammo>> MAP = new HashMap<>();

    private TudMappingClient() {}

    /** 应用服务端下发的映射表（full = 整表覆盖，否则合并 + removed 删除）。 */
    public static void apply(long version, boolean full,
                             List<TudMappingSyncPacket.Entry> entries,
                             List<ResourceLocation> removed) {
        if (full) {
            MAP.clear();
        }
        if (entries != null) {
            for (TudMappingSyncPacket.Entry entry : entries) {
                if (entry == null || entry.gunId() == null) continue;
                MAP.put(entry.gunId(), List.copyOf(entry.ammo()));
            }
        }
        if (removed != null) {
            for (ResourceLocation id : removed) {
                if (id != null) MAP.remove(id);
            }
        }
        tableVersion = version;
    }

    /** 映射表是否已同步（镜像可用性的前置条件之一）。 */
    public static boolean synced() {
        return tableVersion >= 0L;
    }

    /** 当前映射表版本（调试/状态显示用）。 */
    public static long version() {
        return tableVersion;
    }

    /**
     * 按枪 NBT {@code TudCurrentAmmo} 索引取当前弹药；未同步/该枪无映射返回 {@code null}。
     */
    public static TudMappingSyncPacket.Ammo lookup(ResourceLocation gunId, ItemStack gun) {
        if (gunId == null) return null;
        List<TudMappingSyncPacket.Ammo> list = MAP.get(gunId);
        if (list == null || list.isEmpty()) return null;
        int index = 0;
        CompoundTag tag = gun == null ? null : gun.getTag();
        if (tag != null && tag.contains("TudCurrentAmmo")) {
            index = tag.getInt("TudCurrentAmmo");
        }
        if (index < 0 || index >= list.size()) {
            index = 0;
        }
        return list.get(index);
    }

    /** 清空（登出/重连时调用）。 */
    public static void clear() {
        MAP.clear();
        tableVersion = -1L;
    }
}
