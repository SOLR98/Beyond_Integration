package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;

public class NetworkAwareAmmoHandler {

    public static int consumeFromNetworks(ServerPlayer player, ItemStack gun, int needed) {
        if (needed <= 0) return 0;
        List<DimensionsNet> nets = DimensionsNet.getAllNetFromPlayer(player);
        for (DimensionsNet net : nets) {
            int taken = TaczAmmoExtractor.consumeAmmoDirectly(gun, needed, net);
            if (taken > 0) return taken;
        }
        return 0;
    }

    public static boolean hasAvailable(LivingEntity shooter, ItemStack gun) {
        if (shooter == null) return false;

        if (!shooter.level().isClientSide) {
            ServerPlayer sp = shooter instanceof ServerPlayer s ? s : null;
            if (sp != null && NetworkAmmoHandler.hasNetworkAmmo(sp, gun)) return true;

            if (ModList.get().isLoaded("touhou_little_maid")) {
                var maidNet = MaidNetworkHelper.findTerminal(shooter);
                if (maidNet != null && hasAmmoInNet(gun, maidNet)) return true;
            }
            return false;
        }

        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(gun);
        if (ammoId == null) return false;

        if (TaczAmmoCache.hasData(ammoId)) {
            return TaczAmmoCache.getCount(ammoId) > 0;
        }

        TaczAmmoCache.requestQuick(ammoId);
        return true;
    }

    private static boolean hasAmmoInNet(ItemStack gun, DimensionsNet net) {
        return TaczAmmoExtractor.countAmmoInNetwork(gun, net) > 0;
    }
}
