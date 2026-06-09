package com.solr98.beyondintegration.handler;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.command.util.NetworkUtils;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.VehicleNetStorage;
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
import java.util.WeakHashMap;

public class PlayerNetworkSyncHandler {
    private final Map<ServerPlayer, PlayerSnapshot> lastSnapshots = new WeakHashMap<>();
    private final Map<java.util.UUID, PlayerSnapshot> lastVehicleSnapshots = new WeakHashMap<>();
    private final Map<ServerPlayer, Long> lastPlayerSyncTime = new WeakHashMap<>();
    private final Map<java.util.UUID, Long> lastVehicleSyncTime = new WeakHashMap<>();
    private static final long FORCE_SYNC_INTERVAL = 5000;

    private record PlayerSnapshot(Map<String, Long> ammo, long energy) {}

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sendAmmoData(player);
        sendVehicleAmmoData(player);
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sendAmmoData(p);
            sendVehicleAmmoData(p);
        }
    }

    private DimensionsNet findNetwork(ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net != null) return net;
        return MenuNetIdHelper.getNetFromMenu(player);
    }

    private void sendAmmoData(ServerPlayer player) {
        DimensionsNet net = findNetwork(player);
        if (net == null || !(net instanceof SuperbAmmoAccessor acc)) return;

        Map<String, Long> fullMap = new HashMap<>(acc.getSuperbAmmo());

        // 检测主手 ITEM 弹药枪械
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof GunItem) {
            GunData data = GunData.from(held);
            if (data != null) {
                AmmoConsumer consumer = data.selectedAmmoConsumer();
                if (consumer != null && consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM && !consumer.stack().isEmpty()) {
                    long itemCount = countItems(net, consumer.stack());
                    var regKey = BuiltInRegistries.ITEM.getKey(consumer.stack().getItem());
                    if (regKey != null) fullMap.put("ITEM:" + regKey, itemCount);
                }
            }
        }

        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();

        // 去重：数据没变化且距上次强制同步不足 5 秒就不发包
        long now = System.currentTimeMillis();
        PlayerSnapshot current = new PlayerSnapshot(new HashMap<>(fullMap), energy);
        PlayerSnapshot last = lastSnapshots.get(player);
        Long lastForce = lastPlayerSyncTime.get(player);
        if (current.equals(last) && lastForce != null && now - lastForce < FORCE_SYNC_INTERVAL) return;
        lastSnapshots.put(player, current);
        lastPlayerSyncTime.put(player, now);

        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        boolean enchantSep = !(net instanceof EnchantSeparationAccessor ea) || ea.beyond$isEnchantSeparationEnabled();
        PacketDistributor.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                net.getId(), fullMap, energy, 0, netName, enchantSep));
    }

    private void sendVehicleAmmoData(ServerPlayer p) {
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

        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();

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
            ItemStack ref = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(ammoStr)));
            if (ref.isEmpty()) continue;
            long count = net.getUnifiedStorage().getStackByKey(new ItemStackKey(ref)).amount();
            if (count > 0) ammo.put(itemKey, count);
        }

        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";

        long now = System.currentTimeMillis();
        java.util.UUID vehUuid = vehicle.getUUID();
        PlayerSnapshot current = new PlayerSnapshot(new HashMap<>(ammo), energy);
        PlayerSnapshot last = lastVehicleSnapshots.get(vehUuid);
        Long lastForce = lastVehicleSyncTime.get(vehUuid);
        if (current.equals(last) && lastForce != null && now - lastForce < FORCE_SYNC_INTERVAL) return;
        lastVehicleSnapshots.put(vehUuid, current);
        lastVehicleSyncTime.put(vehUuid, now);

        boolean enchantSep = !(net instanceof EnchantSeparationAccessor ea) || ea.beyond$isEnchantSeparationEnabled();
        PacketDistributor.sendToPlayer(p, new SuperbAmmoStatusResponsePacket(
                netId, ammo, energy, 1, netName, enchantSep));
    }

    private static long countItems(DimensionsNet net, ItemStack target) {
        var opt = net.getUnifiedStorage().getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return 0;
        var bucket = opt.get();
        ItemStackKey targetKey = new ItemStackKey(target);
        long total = 0;
        for (int i = 0; i < bucket.size(); i++) {
            var rawKey = bucket.get(i);
            if (!(rawKey instanceof ItemStackKey ik)) continue;
            if (!ik.isSame(targetKey)) continue;
            total += net.getUnifiedStorage().getStackByKey(ik).amount();
        }
        return total;
    }
}
