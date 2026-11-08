package com.solr98.beyondintegration.feature.ammo.sw;

import com.solr98.beyondintegration.core.log.BiLog;
import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * SW 网络弹药从「虚拟计数」到「弹药物品存储」的一次性迁移。
 * <p>
 * 旧版把 SW 弹药记在 {@link NetworkAmmoData} 的 {@code networkAmmo} 段（弹药类型 → 发数）里；
 * 现在弹药就是网络里的散装弹药物品。迁移按记录逐个网络处理：把每个虚拟条目按
 * {@link Ammo#serializationName} 折算成散装弹药物品插入该网络的统一存储，
 * {@code __infinite__} 折算为对应数量的创造弹药箱，然后清空记录。
 * <p>
 * 执行时机：服务器启动完成（存档已加载、BD 网络可正常读写）。
 */
public final class SwAmmoMigration {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SwAmmoMigration() {
    }

    /** 迁移所有残留的虚拟弹药记录（幂等：迁移后记录清空，再次调用无操作）。 */
    public static void migrate(MinecraftServer server) {
        if (server == null) return;
        if (!ModList.get().isLoaded("superbwarfare")) return;

        var netIds = NetworkAmmoData.legacySuperbAmmoNetIds();
        if (netIds.isEmpty()) return;

        LOGGER.info(BiLog.t("[BI][SW弹药迁移] 开始：共 {} 个网络仍有旧版虚拟弹药记录", "[BI][SW ammo migration] start: {} network(s) still hold legacy virtual ammo records"), netIds.size());

        int migratedNets = 0;
        int skippedNets = 0;
        long migratedEntries = 0;
        long migratedRounds = 0;

        for (int netId : netIds) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net == null) {
                skippedNets++;
                LOGGER.warn(BiLog.t("[BI][SW弹药迁移] 网络 #{} 当前不可用，保留其旧记录，等下次启动再迁移", "[BI][SW ammo migration] network #{} is unavailable; keeping its legacy records for the next startup"), netId);
                continue;
            }

            Map<String, Long> legacy = new HashMap<>(
                    NetworkAmmoData.getOrCreate(netId).getSuperbAmmo());
            int entries = 0;
            long rounds = 0;

            for (var entry : legacy.entrySet()) {
                String ammoKey = entry.getKey();
                long amount = entry.getValue();
                if (amount <= 0L) {
                    LOGGER.warn(BiLog.t("[BI][SW弹药迁移] 网络 #{} 的 {} 记录数量为 {}，已跳过", "[BI][SW ammo migration] network #{} record {} has amount {}; skipped"),
                            netId, ammoKey, amount);
                    continue;
                }

                String itemName;
                String result;
                if (SwAmmoStorage.INFINITE_KEY.equals(ammoKey)) {
                    // 无限弹药 → 对应数量的创造弹药箱
                    long boxes = Math.min(amount, Long.MAX_VALUE / 2L);
                    var rest = net.getUnifiedStorage()
                            .insert(SwAmmoStorage.creativeBoxKey(), boxes, false);
                    long added = boxes - rest.amount();
                    itemName = BiLog.t("创造弹药箱", "Creative Ammo Box");
                    result = BiLog.t("写入 " + added + "/" + boxes + " 个", "wrote " + added + "/" + boxes);
                } else {
                    Ammo ammo = Ammo.getType(ammoKey);
                    if (ammo == null) {
                        LOGGER.warn(BiLog.t("[BI][SW弹药迁移] 网络 #{} 的弹药类型 '{}' 无法识别，丢弃 {} 发", "[BI][SW ammo migration] network #{} has unknown ammo type '{}'; discarded {} round(s)"),
                                netId, ammoKey, amount);
                        continue;
                    }
                    var rest = net.getUnifiedStorage()
                            .insert(SwAmmoStorage.keyOf(ammo), amount, false);
                    long added = amount - rest.amount();
                    itemName = String.valueOf(BuiltInRegistries.ITEM.getKey(ammo.getItem()));
                    result = BiLog.t("写入 " + added + "/" + amount + " 个", "wrote " + added + "/" + amount);
                }

                entries++;
                rounds += amount;
                LOGGER.info(BiLog.t("[BI][SW弹药迁移] 网络 #{} {}：{} 发 → {}（{}）", "[BI][SW ammo migration] network #{} {}: {} round(s) -> {} ({})"),
                        netId, ammoKey, amount, result, itemName);
            }

            NetworkAmmoData.getOrCreate(netId).getSuperbAmmo().clear();
            migratedNets++;
            migratedEntries += entries;
            migratedRounds += rounds;
            LOGGER.info(BiLog.t("[BI][SW弹药迁移] 网络 #{} 处理完毕：{} 种弹药，共 {} 发已转为物品", "[BI][SW ammo migration] network #{} done: {} ammo type(s), {} round(s) converted to items"),
                    netId, entries, rounds);
        }

        // 记录已清空即落盘（网络不可用的记录保留，等下次启动再试）
        if (migratedNets > 0) {
            NetworkAmmoData.markDirty();
            LOGGER.info(BiLog.t("[BI][SW弹药迁移] 全部完成：{} 个网络，{} 种弹药，共 {} 发；跳过 {} 个不可用网络", "[BI][SW ammo migration] all done: {} network(s), {} ammo type(s), {} round(s); {} unavailable network(s) skipped"),
                    migratedNets, migratedEntries, migratedRounds, skippedNets);
        } else {
            LOGGER.warn(BiLog.t("[BI][SW弹药迁移] 没有可迁移的网络（全部不可用），下次启动再试", "[BI][SW ammo migration] no network to migrate (all unavailable); will retry on next startup"));
        }
    }
}
