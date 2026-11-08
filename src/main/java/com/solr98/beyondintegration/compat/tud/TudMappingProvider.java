package com.solr98.beyondintegration.compat.tud;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.TudMappingSyncPacket;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 TUD 映射表构建与下发（仅 TUD 环境生效）。
 * <p>数据源：遍历 {@link TimelessAPI#getAllCommonGunIndex()}，对每把枪调用 TUD 的
 * {@code AmmoManager.getCurrentAmmoFromToml}（TOML 类型映射，优先）或
 * {@code AmmoManager.getGunData}（JSON 的 availableAmmo 列表），
 * 得到"枪 → 弹药列表"的最终结果（已过 {@code AmmoData.getAmmo()} 解析）。
 * <p>表构建一次并缓存；版本 = 内容哈希。客户端收到后即可复现解析，无需本地 TUD 配置。
 */
public final class TudMappingProvider {

    /** 已下发版本：玩家 UUID → 版本 */
    private static final Map<UUID, Long> SENT = new ConcurrentHashMap<>();

    private static volatile long tableVersion = -1L;
    private static volatile List<TudMappingSyncPacket.Entry> table = List.of();

    private TudMappingProvider() {}

    /** 为在线玩家补齐映射表（已下发同版本者跳过；服务端 tick 调用，成本 = 玩家数 + 一次 map 查）。 */
    public static void pushPending(net.minecraft.server.MinecraftServer server) {
        if (server == null || !TudAmmoCompat.isLoaded()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            push(player);
        }
    }

    /** 为玩家下发映射表（未构建则先构建；已是最新版本则跳过）。 */
    public static void push(ServerPlayer player) {
        if (player == null || !TudAmmoCompat.isLoaded()) return;
        List<TudMappingSyncPacket.Entry> entries = table;
        long version = tableVersion;
        if (entries.isEmpty() || version < 0L) {
            build();
            entries = table;
            version = tableVersion;
        }
        if (entries.isEmpty()) return;
        if (SENT.getOrDefault(player.getUUID(), Long.MIN_VALUE) == version) return;
        SENT.put(player.getUUID(), version);
        PacketHandler.sendToPlayer(player, new TudMappingSyncPacket(version, true, entries, List.of()));
    }

    /** 内容哈希（按 gunId 排序，顺序稳定）。 */
    public static long version() {
        return tableVersion;
    }

    /** 重建映射表（TUD 配置/数据重载后调用）。 */
    public static synchronized void rebuild() {
        table = List.of();
        tableVersion = -1L;
        SENT.clear();
        build();
    }

    private static void build() {
        List<TudMappingSyncPacket.Entry> entries = new ArrayList<>();
        if (!TudAmmoCompat.isLoaded()) {
            table = List.of();
            tableVersion = -1L;
            return;
        }
        for (var gunEntry : TimelessAPI.getAllCommonGunIndex()) {
            ResourceLocation gunId = gunEntry.getKey();
            if (gunId == null) continue;
            ItemStack gunStack = new ItemStack(ModItems.MODERN_KINETIC_GUN.get());
            IGun iGun = IGun.getIGunOrNull(gunStack);
            if (iGun == null) continue;
            iGun.setGunId(gunStack, gunId);
            List<TudMappingSyncPacket.Ammo> ammo = TudAmmoCompat.resolveAmmoList(gunId, gunStack);
            if (!ammo.isEmpty()) {
                entries.add(new TudMappingSyncPacket.Entry(gunId, ammo));
            }
        }
        table = List.copyOf(entries);
        tableVersion = TudMappingSyncPacket.hashOf(entries);
    }

    /** 玩家登出时清理下发记录。 */
    public static void remove(UUID uuid) {
        if (uuid != null) SENT.remove(uuid);
    }

    /** 服务器停止时清空。 */
    public static void clear() {
        SENT.clear();
        table = List.of();
        tableVersion = -1L;
    }
}
