package com.solr98.beyondintegration.feature.ammo.common;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络弹药附加数据（SavedData 存档持久化）
 * 以网络 ID 为键保存 SW 虚拟弹药、TACZ 创造弹药箱与附魔分离开关，
 * 随服务器世界存档读写，并提供静态实例访问与脏标记接口。
 */
public class NetworkAmmoData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 存档数据条目名称 */
    private static final String NAME = "beyond_integration_attachments";
    /** 网络ID -> 附加数据（并发安全的静态缓存） */
    private static final Map<Integer, Attachment> data = new ConcurrentHashMap<>();

    /** 全局单例实例 */
    private static NetworkAmmoData instance;

    /**
     * 初始化/加载存档数据（不存在则新建）
     * 新建/加载前清空静态缓存，防止切换世界/存档时残留旧数据被写入新存档。
     */
    public static void initialize(ServerLevel level) {
        data.clear();
        instance = level.getDataStorage().computeIfAbsent(NetworkAmmoData::load, NetworkAmmoData::new, NAME);
    }

    /**
     * 标记数据已修改（触发自动保存）
     */
    public static void markDirty() {
        if (instance != null) instance.setDirty();
    }

    /** 空构造：供 SavedData 创建新实例使用 */
    public NetworkAmmoData() {}

    /**
     * 从 NBT 反序列化加载数据（加载前清空静态缓存，避免跨存档数据残留）
     */
    public static NetworkAmmoData load(CompoundTag tag) {
        // 静态 map 跨存档残留清理：新存档加载前清空，避免旧存档数据污染
        data.clear();
        CompoundTag networks = tag.getCompound("Networks");
        for (String key : networks.getAllKeys()) {
            int netId;
            try {
                netId = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                // 损坏/被篡改的存档键：跳过而非崩溃（保证世界可加载）
                LOGGER.warn("NetworkAmmoData: skipping corrupted net key '{}'", key);
                continue;
            }
            CompoundTag netTag = networks.getCompound(key);
            Attachment att = new Attachment();
            if (netTag.contains("SuperbAmmo")) {
                CompoundTag ammoTag = netTag.getCompound("SuperbAmmo");
                for (String ak : ammoTag.getAllKeys()) {
                    att.superbAmmo.put(ak, ammoTag.getLong(ak));
                }
            }
            if (netTag.contains("TaczCreativeTypes")) {
                CompoundTag ctTag = netTag.getCompound("TaczCreativeTypes");
                for (String tk : ctTag.getAllKeys()) {
                    att.taczCreativeTypeCounts.put(tk, ctTag.getInt(tk));
                }
            }
            att.enchantSeparation = netTag.contains("EnchantSeparation") && netTag.getBoolean("EnchantSeparation");
            data.put(netId, att);
        }
        if (instance == null) instance = new NetworkAmmoData();
        return instance;
    }

    /**
     * 将内存数据序列化写入 NBT
     */
    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag networks = new CompoundTag();
        for (var entry : data.entrySet()) {
            CompoundTag netTag = new CompoundTag();
            CompoundTag ammoTag = new CompoundTag();
            for (var e : entry.getValue().superbAmmo.entrySet()) {
                ammoTag.putLong(e.getKey(), e.getValue());
            }
            netTag.put("SuperbAmmo", ammoTag);
            CompoundTag ctTag = new CompoundTag();
            for (var e : entry.getValue().taczCreativeTypeCounts.entrySet()) {
                ctTag.putInt(e.getKey(), e.getValue());
            }
            netTag.put("TaczCreativeTypes", ctTag);
            netTag.putBoolean("EnchantSeparation", entry.getValue().enchantSeparation);
            networks.put(String.valueOf(entry.getKey()), netTag);
        }
        tag.put("Networks", networks);
        return tag;
    }

    /**
     * 获取指定网络的附加数据（不存在则创建新条目）
     */
    public static Attachment getOrCreate(int netId) {
        return data.computeIfAbsent(netId, k -> new Attachment());
    }

    /**
     * 删除指定网络的附加数据
     */
    public static void remove(int netId) {
        data.remove(netId);
    }

    /**
     * 单个网络附加的弹药数据
     */
    public static class Attachment {
        /** SW 虚拟弹药表（弹药类型 -> 数量） */
        private final Map<String, Long> superbAmmo = new HashMap<>();
        /** TACZ 创造弹药箱记录（弹药ID -> 数量） */
        private final Map<String, Integer> taczCreativeTypeCounts = new HashMap<>();
        /** 附魔分离开关（默认开启） */
        private boolean enchantSeparation = true;

        /** 获取 SW 虚拟弹药表 */
        public Map<String, Long> getSuperbAmmo() { return superbAmmo; }
        /** 整体替换 SW 虚拟弹药表 */
        public void setSuperbAmmo(Map<String, Long> map) {
            superbAmmo.clear();
            superbAmmo.putAll(map);
        }
        /** 获取 TACZ 创造弹药箱记录 */
        public Map<String, Integer> getTaczCreativeTypeCounts() { return taczCreativeTypeCounts; }
        /** 是否启用附魔分离 */
        public boolean isEnchantSeparation() { return enchantSeparation; }
        /** 设置附魔分离开关 */
        public void setEnchantSeparation(boolean v) { this.enchantSeparation = v; }
    }
}
