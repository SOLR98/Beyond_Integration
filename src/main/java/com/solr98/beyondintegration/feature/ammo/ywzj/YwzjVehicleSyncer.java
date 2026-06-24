package com.solr98.beyondintegration.feature.ammo.ywzj;

import com.solr98.beyondintegration.core.constants.ModConstants;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.solr98.beyondintegration.network.YwzjVehicleDataResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.vehicle.part.PartUnit;
import org.ywzj.vehicle.vehicle.part.WeaponUnit;
import org.ywzj.vehicle.vehicle.weapon.AbstractVehicleWeapon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class YwzjVehicleSyncer {
    private final Map<UUID, Long> lastSyncTime = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sync(player);
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    private void sync(ServerPlayer player) {
        var vehicle = player.getVehicle();
        if (!(vehicle instanceof AbstractVehicle ywzjVehicle)) return;

        UUID vehUuid = vehicle.getUUID();
        long now = System.currentTimeMillis();
        Long last = lastSyncTime.get(vehUuid);
        if (last != null && now - last < ModConstants.SYNC_INTERVAL_VEHICLE) return;
        lastSyncTime.put(vehUuid, now);

        int netId = VehicleNetStorage.getBoundNetId(vehUuid);
        if (netId < 0) return;

        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            VehicleNetStorage.unbindVehicle(vehUuid);
            return;
        }

        var storage = net.getUnifiedStorage();
        if (storage == null) return;
        long energy = storage.getStackByKey(EnergyStackKey.INSTANCE).amount();
        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        Map<String, Long> ammoMap = collectAmmoCounts(ywzjVehicle, net);

        PacketDistributor.sendToPlayer(player, new YwzjVehicleDataResponsePacket(netId, energy, netName, ammoMap));
    }

    private static Map<String, Long> collectAmmoCounts(AbstractVehicle vehicle, DimensionsNet net) {
        Map<String, Long> result = new HashMap<>();
        for (PartUnit<?> part : vehicle.getPartUnits()) {
            if (!(part instanceof WeaponUnit weaponUnit)) continue;
            collectWeaponAmmo(weaponUnit.weapons, net, result);
            collectWeaponAmmo(weaponUnit.secondaryWeapons, net, result);
            collectWeaponAmmo(weaponUnit.independentWeapons, net, result);
        }
        return result;
    }

    private static void collectWeaponAmmo(List<AbstractVehicleWeapon<?>> weapons, DimensionsNet net, Map<String, Long> result) {
        for (var weapon : weapons) {
            var ingredient = weapon.getData().getReload().getAmmo();
            if (ingredient == null) continue;
            for (ItemStack stack : ingredient.getItems()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (result.containsKey(id)) continue;
                long count = net.getUnifiedStorage().getStackByKey(new ItemStackKey(stack)).amount();
                if (count > 0) result.put(id, count);
            }
        }
    }
}
