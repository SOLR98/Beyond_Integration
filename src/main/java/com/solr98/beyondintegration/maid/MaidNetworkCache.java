package com.solr98.beyondintegration.maid;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆网络缓存。
 * 以实体 UUID 为键缓存其绑定的网络 ID，避免每次查找时重复扫描
 * 女仆饰物/背包中的终端物品（本地缓存，非线程安全）。
 */
public class MaidNetworkCache {
    /** 缓存表：实体 UUID -> 网络 ID。 */
    static final Map<UUID, Integer> cachedNetId = new HashMap<>();

    /** 根据实体查询其绑定的维度网络，无缓存或网络已删除时返回 null。 */
    public static DimensionsNet get(LivingEntity entity) {
        Integer netId = cachedNetId.get(entity.getUUID());
        if (netId == null) return null;
        return DimensionsNet.getNetFromId(netId);
    }

    /** 缓存实体 UUID 与其绑定的网络 ID。 */
    public static void put(UUID uuid, int netId) {
        cachedNetId.put(uuid, netId);
    }

    /** 移除实体的网络缓存（例如网络变更或实体死亡时）。 */
    public static void remove(UUID uuid) {
        cachedNetId.remove(uuid);
    }
}
