package com.solr98.beyondintegration.feature.ammo.sw;

import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class SwPlayerAmmoSyncer {

    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final Map<UUID, PlayerSnapshot> lastSnapshots = new HashMap<>();
    private static final Map<UUID, PlayerSnapshot> lastVehicleSnapshots = new HashMap<>();
    private static final Map<UUID, Integer> tickCounters = new HashMap<>();

    private record PlayerSnapshot(Map<String, Long> ammo, long energy) {}

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.side.isClient()) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        int tick = tickCounters.getOrDefault(uuid, 0);
        tickCounters.put(uuid, (tick + 1) % SYNC_INTERVAL_TICKS);
        if (tick != 0) return;

        sendAmmoData(player);
        sendVehicleAmmoData(player);
    }

    private void sendAmmoData(ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null || !(net instanceof SuperbAmmoAccessor acc)) return;

        Map<String, Long> fullMap = new HashMap<>(acc.getSuperbAmmo());

        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof GunItem) {
            GunData data = GunData.from(held);
            if (data != null) {
                AmmoConsumer consumer = data.selectedAmmoConsumer();
                if (consumer != null && consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM && !consumer.stack().isEmpty()) {
                    long itemCount = net.getUnifiedStorage().getStackByKey(new ItemStackKey(consumer.stack())).amount();
                    var regKey = ForgeRegistries.ITEMS.getKey(consumer.stack().getItem());
                    if (regKey != null) fullMap.put("ITEM:" + regKey, itemCount);
                }
            }
        }

        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        PlayerSnapshot current = new PlayerSnapshot(new HashMap<>(fullMap), energy);
        PlayerSnapshot last = lastSnapshots.get(player.getUUID());
        if (current.equals(last)) return;
        lastSnapshots.put(player.getUUID(), current);

        String netName = ((NetworkNameProvider) net).getCustomName();
        PacketHandler.sendToPlayer(player,
            SuperbAmmoStatusResponsePacket.fromNet(net, fullMap, energy, 0, netName));
    }

    private void sendVehicleAmmoData(ServerPlayer p) {
        if (!(p.getVehicle() instanceof VehicleEntity vehicle)) return;
        DimensionsNet net = VehicleNetStorage.getNetworkForVehicle(vehicle.getUUID());
        if (net == null) return;
        if (!(net instanceof SuperbAmmoAccessor acc)) return;

        Map<String, Long> ammo = new HashMap<>(acc.getSuperbAmmo());
        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();

        for (int seat = 0; seat < vehicle.getMaxPassengers(); seat++) {
            GunData data = vehicle.getGunData(seat);
            if (data == null) continue;
            AmmoConsumer consumer = data.selectedAmmoConsumer();
            if (consumer == null || consumer.getType() != AmmoConsumer.AmmoConsumeType.ITEM) continue;
            String raw = consumer.stack().isEmpty() ? null : ForgeRegistries.ITEMS.getKey(consumer.stack().getItem()).toString();
            if (raw == null) raw = consumer.getAmmo();
            if (raw == null || raw.isEmpty()) continue;
            raw = raw.strip();
            int space = raw.indexOf(' ');
            if (space > 0) raw = raw.substring(space + 1).strip();
            if (raw.startsWith("@") || raw.startsWith("#")) raw = raw.substring(1);
            String itemKey = "ITEM:" + raw;
            if (ammo.containsKey(itemKey)) continue;
            try {
                var ref = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(raw)));
                if (!ref.isEmpty()) {
                    long count = net.getUnifiedStorage().getStackByKey(new ItemStackKey(ref)).amount();
                    if (count > 0) ammo.put(itemKey, count);
                }
            } catch (Exception ignored) {}
        }

        String netName = ((NetworkNameProvider) net).getCustomName();
        UUID vehUuid = vehicle.getUUID();
        PlayerSnapshot current = new PlayerSnapshot(new HashMap<>(ammo), energy);
        if (current.equals(lastVehicleSnapshots.get(vehUuid))) return;
        lastVehicleSnapshots.put(vehUuid, current);

        PacketHandler.sendToPlayer(p, SuperbAmmoStatusResponsePacket.fromNet(
                net, ammo, energy, 1, netName));
    }
}
