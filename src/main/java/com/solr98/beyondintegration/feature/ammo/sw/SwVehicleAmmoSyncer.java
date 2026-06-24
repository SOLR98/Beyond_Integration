package com.solr98.beyondintegration.feature.ammo.sw;

import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.core.constants.ModConstants;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class SwVehicleAmmoSyncer {
    private final Map<UUID, PlayerSnapshot> lastSnapshots = new WeakHashMap<>();
    private final Map<UUID, Long> lastSyncTime = new WeakHashMap<>();

    private record PlayerSnapshot(Map<String, Long> ammo, long energy) {}

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sync(player);
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    private void sync(ServerPlayer p) {
        if (!(p.getVehicle() instanceof VehicleEntity vehicle)) return;
        int netId = VehicleNetStorage.getBoundNetId(vehicle.getUUID());
        if (netId < 0) return;
        var net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            VehicleNetStorage.unbindVehicle(vehicle.getUUID());
            return;
        }
        if (!(net instanceof SuperbAmmoAccessor acc)) return;

        var ammo = new HashMap<>(acc.getSuperbAmmo());
        var storage = net.getUnifiedStorage();
        if (storage == null) return;
        long energy = storage.getStackByKey(EnergyStackKey.INSTANCE).amount();

        for (int seat = 0; seat < vehicle.getMaxPassengers(); seat++) {
            GunData data = vehicle.getGunData(seat);
            if (data == null) continue;
            AmmoConsumer consumer = data.selectedAmmoConsumer();
            if (consumer == null || consumer.getType() != AmmoConsumer.AmmoConsumeType.ITEM) continue;
            String ammoStr = consumer.getAmmo();
            if (ammoStr == null || ammoStr.isEmpty()) continue;
            ammoStr = ammoStr.strip();
            int space = ammoStr.indexOf(' ');
            if (space > 0) ammoStr = ammoStr.substring(space + 1).strip();
            if (ammoStr.startsWith("@") || ammoStr.startsWith("#")) ammoStr = ammoStr.substring(1);
            String itemKey = "ITEM:" + ammoStr;
            if (ammo.containsKey(itemKey)) continue;
            var rl = ResourceLocation.tryParse(ammoStr);
            if (rl == null) continue;
            ItemStack ref = new ItemStack(BuiltInRegistries.ITEM.get(rl));
            if (ref.isEmpty()) continue;
            long count = net.getUnifiedStorage().getStackByKey(new ItemStackKey(ref)).amount();
            if (count > 0) ammo.put(itemKey, count);
        }

        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        long now = System.currentTimeMillis();
        UUID vehUuid = vehicle.getUUID();
        PlayerSnapshot current = new PlayerSnapshot(new HashMap<>(ammo), energy);
        PlayerSnapshot last = lastSnapshots.get(vehUuid);
        Long lastForce = lastSyncTime.get(vehUuid);
        if (current.equals(last) && lastForce != null && now - lastForce < ModConstants.SYNC_INTERVAL_FORCE) return;
        lastSnapshots.put(vehUuid, current);
        lastSyncTime.put(vehUuid, now);

        boolean enchantSep = !(net instanceof EnchantSeparationAccessor ea) || ea.beyond$isEnchantSeparationEnabled();
        PacketDistributor.sendToPlayer(p, new SuperbAmmoStatusResponsePacket(
                netId, ammo, energy, 1, netName, enchantSep));
    }
}
