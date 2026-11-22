package com.solr98.beyondintegration.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端请求节流：防止客户端（或改过的客户端）高频重复发“请求型”包，
 * 把全量扫描/构造放大成服务端负载。
 *
 * <p>按「玩家 + 请求类型」记录上次放行的 tick，冷却期内直接丢弃。
 * 键名使用稳定字符串（不再依赖包类名，便于重命名）。
 */
public final class RequestThrottle {

    private static final Map<UUID, Map<String, Long>> LAST = new HashMap<>();

    private RequestThrottle() {}

    /**
     * 是否放行本次请求，并记录放行时间。
     *
     * @param player        请求玩家
     * @param key           请求类型键
     * @param cooldownTicks 冷却（tick），0 = 不限制
     */
    public static boolean allow(ServerPlayer player, String key, int cooldownTicks) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        long now = player.getServer().getTickCount();
        Map<String, Long> perPlayer = LAST.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        long last = perPlayer.getOrDefault(key, Long.MIN_VALUE / 2);
        if (cooldownTicks > 0 && now - last < cooldownTicks) {
            return false;
        }
        perPlayer.put(key, now);
        return true;
    }

    /** 玩家退出时清理。 */
    public static void clear(UUID id) {
        LAST.remove(id);
    }

    /** 服务器停止时清理。 */
    public static void clearAll() {
        LAST.clear();
    }
}
