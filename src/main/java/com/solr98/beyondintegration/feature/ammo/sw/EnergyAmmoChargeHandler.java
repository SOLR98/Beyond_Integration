package com.solr98.beyondintegration.feature.ammo.sw;

import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * SW 能量弹药（AmmoConsumeType.ENERGY，ammo 配置 "fe"/"rf"/"energy"）网络充电服务。
 *
 * SW 0.8.9.1 能量弹药策略（EnergyAmmoStrategy）以手持武器自身的 FE 槽为弹药；
 * 本服务按配置间隔从玩家主网络的能量库存（EnergyStackKey）抽取 FE，
 * 持续/间隔充入手持能量武器，实现"网络供能"（与载具网络充电同思路）。
 * 服务端 tick 驱动，独立开关与间隔配置，不受弹药轮询开关影响。
 */
public final class EnergyAmmoChargeHandler {

    private EnergyAmmoChargeHandler() {}

    /** 服务端 tick：遍历在线玩家，按间隔为手持能量武器从主网络充电 */
    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (!CommandConfig.energyAmmoChargeEnabled()) return;
        int interval = CommandConfig.energyAmmoChargeInterval();
        if (interval <= 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.tickCount % interval != 0) continue;
            chargeMainHand(player);
        }
    }

    /** 为主手能量弹药武器充电：网络 FE → 武器 IEnergyStorage（tick 轮询与射击消耗触发点共用） */
    public static void chargeMainHand(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        GunData data = GunData.from(stack);
        if (data == null) return;
        var consumer = data.selectedAmmoConsumer();
        if (consumer == null || consumer.getType() != AmmoConsumer.AmmoConsumeType.ENERGY) return;

        var energyOpt = stack.getCapability(ForgeCapabilities.ENERGY).resolve();
        if (energyOpt.isEmpty()) return;
        IEnergyStorage storage = energyOpt.get();
        int needed = storage.getMaxEnergyStored() - storage.getEnergyStored();
        if (needed <= 0 || !storage.canReceive()) return;

        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return;

        int want = Math.min(needed, CommandConfig.energyAmmoChargeRate());
        if (want <= 0) return;

        long got = net.getUnifiedStorage().extract(EnergyStackKey.INSTANCE, want, false, false).amount();
        if (got <= 0) return;

        storage.receiveEnergy((int) Math.min(got, Integer.MAX_VALUE), false);
        net.setDirty();
    }
}
