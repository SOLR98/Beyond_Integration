package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * 网络感知弹药处理器（服务端/客户端通用入口）
 * 服务端：从玩家网络按主网络优先顺序消耗弹药，并记录弹药归属网络；
 * 客户端：通过 TaczAmmoCache 判断弹药是否可用，并对女仆网络做兜底判断。
 */
public class NetworkAwareAmmoHandler {

    /**
     * 从玩家主网络消耗弹药（仅主网络；扣弹后立即推送快照给玩家，HUD 实时更新）
     *
     * @return 实际消耗数量；0 表示无可用弹药
     */
    public static int consumeFromNetworks(ServerPlayer player, ItemStack gun, int needed) {
        if (needed <= 0) return 0;
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (primary == null) return 0;
        int taken = TaczAmmoExtractor.consumeAmmoDirectly(gun, needed, primary);
        if (taken > 0) {
            PlayerNetUsageTracker.record(player.getUUID(), primary.getId());
            TaczAmmoPollingService.pushSnapshotToPlayer(player, primary.getId());
        }
        return taken;
    }

    /**
     * 判断射击者是否可用网络弹药（服务端查网络/终端/女仆，客户端查本地缓存并触发请求）
     */
    public static boolean hasAvailable(LivingEntity shooter, ItemStack gun) {
        if (shooter == null) return false;

        // 服务端：检查玩家网络弹药、实体终端与女仆终端
        if (!shooter.level().isClientSide) {
            ServerPlayer sp = shooter instanceof ServerPlayer s ? s : null;
            if (sp != null && NetworkAmmoHandler.hasNetworkAmmo(sp, gun)) return true;

            if (ModList.get().isLoaded("touhou_little_maid")) {
                var maidNet = MaidNetworkHelper.findTerminal(shooter);
                if (maidNet != null && hasAmmoInNet(gun, maidNet)) return true;
            }
            return false;
        }

        // 客户端：优先使用本地缓存，无缓存时发送快速请求并乐观判定可用
        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(gun);
        if (ammoId == null) return false;

        if (TaczAmmoCache.hasData(ammoId)) {
            return TaczAmmoCache.getCount(ammoId) > 0;
        }

        TaczAmmoCache.requestQuick(ammoId);
        return false;
    }

    /**
     * 检查指定网络中是否有该枪械的弹药
     */
    private static boolean hasAmmoInNet(ItemStack gun, DimensionsNet net) {
        return TaczAmmoExtractor.countAmmoInNetwork(gun, net) > 0;
    }
}
