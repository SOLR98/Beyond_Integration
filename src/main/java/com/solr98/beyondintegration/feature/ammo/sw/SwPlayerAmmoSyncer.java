package com.solr98.beyondintegration.feature.ammo.sw;

import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.solr98.beyondintegration.core.constants.ModConstants;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.MenuNetIdHelper;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
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

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class SwPlayerAmmoSyncer {
    private final Map<ServerPlayer, PlayerSnapshot> lastSnapshots = new WeakHashMap<>();
    private final Map<ServerPlayer, Long> lastSyncTime = new WeakHashMap<>();

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

    private void sync(ServerPlayer player) {
        DimensionsNet net = findNetwork(player);
        if (net == null || !(net instanceof SuperbAmmoAccessor acc)) return;

        Map<String, Long> fullMap = new HashMap<>(acc.getSuperbAmmo());

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

        var storage = net.getUnifiedStorage();
        if (storage == null) return;
        long energy = storage.getStackByKey(EnergyStackKey.INSTANCE).amount();

        long now = System.currentTimeMillis();
        PlayerSnapshot current = new PlayerSnapshot(new HashMap<>(fullMap), energy);
        PlayerSnapshot last = lastSnapshots.get(player);
        Long lastForce = lastSyncTime.get(player);
        if (current.equals(last) && lastForce != null && now - lastForce < ModConstants.SYNC_INTERVAL_FORCE) return;
        lastSnapshots.put(player, current);
        lastSyncTime.put(player, now);

        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        boolean enchantSep = !(net instanceof EnchantSeparationAccessor ea) || ea.beyond$isEnchantSeparationEnabled();
        PacketDistributor.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                net.getId(), fullMap, energy, 0, netName, enchantSep));
    }

    private DimensionsNet findNetwork(ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net != null) return net;
        return MenuNetIdHelper.getNetFromMenu(player);
    }

    static long countItems(DimensionsNet net, ItemStack target) {
        var storage = net.getUnifiedStorage();
        if (storage == null) return 0;
        var opt = storage.getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return 0;
        var bucket = opt.get();
        ItemStackKey targetKey = new ItemStackKey(target);
        long total = 0;
        for (int i = 0; i < bucket.size(); i++) {
            var rawKey = bucket.get(i);
            if (!(rawKey instanceof ItemStackKey ik)) continue;
            if (!ik.isSame(targetKey)) continue;
            total += storage.getStackByKey(ik).amount();
        }
        return total;
    }
}
