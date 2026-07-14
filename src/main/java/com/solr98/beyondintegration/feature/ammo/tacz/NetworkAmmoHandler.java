package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.tacz.guns.api.TimelessAPI;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.resources.ResourceLocation;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class NetworkAmmoHandler {

    public static boolean hasNetworkAmmo(LivingEntity shooter, ItemStack gun) {
        ServerPlayer player = findServerPlayer(shooter);
        if (player != null) {
            int count = TaczAmmoExtractor.countAmmoInNetwork(gun, player);
            if (count > 0) return true;
        }

        DimensionsNet net = findTerminalOnEntity(shooter);
        if (net != null && TaczAmmoExtractor.countAmmoInNetwork(gun, net) > 0) return true;

        if (ModList.get().isLoaded("touhou_little_maid")) {
            net = MaidNetworkHelper.findTerminal(shooter);
            if (net != null && TaczAmmoExtractor.countAmmoInNetwork(gun, net) > 0) return true;
        }

        return false;
    }

    public static int consumeFromNetwork(LivingEntity shooter, ItemStack gun, int needed) {
        if (needed <= 0) return 0;

        ServerPlayer player = findServerPlayer(shooter);
        if (player != null) {
            int taken = TaczAmmoExtractor.consumeAmmoDirectly(gun, needed, player);
            if (taken > 0) return taken;
        }

        DimensionsNet net = findTerminalOnEntity(shooter);
        if (net != null) {
            int taken = TaczAmmoExtractor.consumeAmmoDirectly(gun, needed, net);
            if (taken > 0) return taken;
        }

        if (ModList.get().isLoaded("touhou_little_maid")) {
            net = MaidNetworkHelper.findTerminal(shooter);
            if (net != null) {
                int taken = TaczAmmoExtractor.consumeAmmoDirectly(gun, needed, net);
                if (taken > 0) return taken;
            }
        }

        return 0;
    }

    @Nullable
    private static ServerPlayer findServerPlayer(LivingEntity shooter) {
        return shooter instanceof ServerPlayer sp ? sp : null;
    }

    @Nullable
    public static DimensionsNet findTerminalOnEntity(LivingEntity entity) {
        Optional<IItemHandler> opt = entity.getCapability(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).resolve();
        if (opt.isEmpty()) return null;
        IItemHandler inv = opt.get();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) return net;
            }
        }
        return null;
    }

    @Nullable
    public static DimensionsNet findTerminalInHandler(IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) return net;
            }
        }
        return null;
    }

    @Nullable
    public static ResourceLocation getAmmoId(ItemStack gun) {
        if (gun.isEmpty()) return null;
        Optional<com.tacz.guns.resource.index.CommonGunIndex> opt = TimelessAPI.getCommonGunIndex(
                com.tacz.guns.api.item.IGun.getIGunOrNull(gun).getGunId(gun));
        return opt.map(index -> index.getGunData().getAmmoId()).orElse(null);
    }
}
