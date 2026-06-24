package com.solr98.beyondintegration.mixin;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.InventoryTool;
import com.solr98.beyondintegration.command.util.NetworkUtils;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "com.atsuishio.superbwarfare.data.gun.AmmoConsumer", remap = false)
public class AmmoConsumerMixin {
    @Shadow(remap = false) private AmmoConsumer.AmmoConsumeType type;
    @Shadow(remap = false) private com.atsuishio.superbwarfare.data.gun.Ammo playerAmmoType;
    @Shadow(remap = false) private int loadAmount;
    @Shadow(remap = false) private ItemStack stack;

    @Unique private static final long NOTIFICATION_COOLDOWN_MS = 300_000;
    @Unique private static final Map<UUID, Long> beyond$lastNotificationTime = new ConcurrentHashMap<>();

    @Inject(method = "consume(Lcom/atsuishio/superbwarfare/data/gun/GunData;Lnet/minecraft/world/entity/Entity;I)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void beyond$onConsume(GunData data, Entity entity, int loads, CallbackInfoReturnable<Integer> cir) {
        if (type == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            if (playerAmmoType == null) return;
            if (entity instanceof ServerPlayer player) {
                handleConsumePlayerAmmo(player, loads, cir);
            } else if (entity instanceof VehicleEntity vehicle) {
                handleConsumeVehicleAmmo(vehicle, loads, cir);
            } else if (entity instanceof LivingEntity living) {
                handleConsumeMaidAmmo(living, loads, cir);
            }
        } else if (type == AmmoConsumer.AmmoConsumeType.ITEM) {
            if (stack.isEmpty()) return;
            if (entity instanceof ServerPlayer player) {
                handleConsumePlayerItem(player, loads, cir);
            } else if (entity instanceof VehicleEntity vehicle) {
                handleConsumeVehicleItem(vehicle, loads, cir);
            } else if (entity instanceof LivingEntity living) {
                handleConsumeMaidItem(living, loads, cir);
            }
        }
    }

    @Unique
    private void handleConsumePlayerAmmo(ServerPlayer player, int loads, CallbackInfoReturnable<Integer> cir) {
        if (player.level() == null || player.level().isClientSide()) return;

        String key = playerAmmoType.serializationName;
        int need = loads * loadAmount;
        int personal = playerAmmoType.get(player);
        int fromPersonal = Math.min(personal, need);
        playerAmmoType.add(player, -fromPersonal);
        int remaining = need - fromPersonal;
        int consumed = fromPersonal / loadAmount;
        DimensionsNet usedNet = null;

        if (remaining > 0) {
            int fromInv = InventoryTool.consumeAmmoItem(player, playerAmmoType, remaining);
            consumed += fromInv / loadAmount;
            remaining -= fromInv;
        }

        if (remaining > 0) {
            for (var net : NetworkUtils.getPlayerNetsPrimaryFirst(player)) {
                if (!(net instanceof SuperbAmmoAccessor acc)) continue;
                var map = acc.getSuperbAmmo();
                if (map.getOrDefault("__infinite__", 0L) > 0) { consumed = loads; remaining = 0; usedNet = net; break; }
                long avail = map.getOrDefault(key, 0L);
                if (avail <= 0) continue;
                long take = Math.min(avail, remaining);
                map.put(key, avail - take);
                consumed += (int) (take / loadAmount);
                remaining -= (int) take;
                net.setDirty();
                usedNet = net;
                break;
            }
        }

        cir.setReturnValue(Math.min(consumed, loads));
        if (consumed > 0 && usedNet != null) {
            pushUpdate(player, usedNet);
            if (shouldNotify(player)) {
                var primary = DimensionsNet.getPrimaryNetFromPlayer(player);
                int id = usedNet.getId();
                if (primary != null && primary.getId() == id)
                    player.sendSystemMessage(Component.translatable("message.beyond_integration.using_primary_net_ammo"));
                else
                    player.sendSystemMessage(Component.translatable("message.beyond_integration.using_net_ammo", id));
            }
        }
    }

    @Unique
    private void handleConsumeVehicleAmmo(VehicleEntity vehicle, int loads, CallbackInfoReturnable<Integer> cir) {
        int boundNetId = VehicleNetStorage.getBoundNetId(vehicle.getUUID());
        if (boundNetId < 0) return;
        var net = DimensionsNet.getNetFromId(boundNetId);
        if (net == null) {
            VehicleNetStorage.unbindVehicle(vehicle.getUUID());
            return;
        }
        if (!(net instanceof SuperbAmmoAccessor acc)) return;

        var map = acc.getSuperbAmmo();
        String key = playerAmmoType.serializationName;
        if (map.getOrDefault("__infinite__", 0L) > 0) {
            cir.setReturnValue(loads);
            for (Entity p : vehicle.getPassengers())
                if (p instanceof ServerPlayer sp) pushVehicleUpdate(sp, net, boundNetId);
            return;
        }
        long networkAmmo = map.getOrDefault(key, 0L);
        if (networkAmmo <= 0) return;
        int need = loads * loadAmount;
        long take = Math.min(networkAmmo, need);
        if (take <= 0) return;
        map.put(key, networkAmmo - take);
        net.setDirty();
        int taken = (int) (take / loadAmount);
        if (taken > 0) {
            cir.setReturnValue(taken);
            for (Entity p : vehicle.getPassengers())
                if (p instanceof ServerPlayer sp) pushVehicleUpdate(sp, net, boundNetId);
        }
    }

    @Unique
    private void handleConsumeMaidAmmo(LivingEntity living, int loads, CallbackInfoReturnable<Integer> cir) {
        DimensionsNet net = MaidNetworkHelper.findTerminal(living);
        if (net == null || !(net instanceof SuperbAmmoAccessor acc)) return;

        var map = acc.getSuperbAmmo();
        if (map.getOrDefault("__infinite__", 0L) > 0) { cir.setReturnValue(loads); return; }
        String key = playerAmmoType.serializationName;
        long networkAmmo = map.getOrDefault(key, 0L);
        if (networkAmmo <= 0) return;
        int need = loads * loadAmount;
        long take = Math.min(networkAmmo, need);
        if (take <= 0) return;
        map.put(key, networkAmmo - take);
        net.setDirty();
        int taken = (int) (take / loadAmount);
        if (taken > 0) cir.setReturnValue(Math.min(taken, loads));
    }

    @Unique
    private void handleConsumePlayerItem(ServerPlayer player, int loads, CallbackInfoReturnable<Integer> cir) {
        if (player.level() == null || player.level().isClientSide()) return;

        int need = loads * loadAmount;
        long taken = 0;
        var cap = player.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ENTITY);
        if (cap != null) taken = InventoryTool.consumeItem(cap, stack.getItem(), need);

        if (taken < need) {
            var itemKey = new ItemStackKey(stack);
            for (var net : NetworkUtils.getPlayerNetsPrimaryFirst(player)) {
                if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
                    cir.setReturnValue(loads);
                    return;
                }
                var ext = net.getUnifiedStorage().extract(itemKey, need - (int) taken, false, false);
                if (ext.amount() > 0) { taken += ext.amount(); net.setDirty(); }
                if (taken >= need) break;
            }
        }
        if (taken > 0) cir.setReturnValue((int) (taken / loadAmount));
    }

    @Unique
    private void handleConsumeVehicleItem(VehicleEntity vehicle, int loads, CallbackInfoReturnable<Integer> cir) {
        int netId = VehicleNetStorage.getBoundNetId(vehicle.getUUID());
        if (netId < 0) return;
        var net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            VehicleNetStorage.unbindVehicle(vehicle.getUUID());
            return;
        }
        if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
            cir.setReturnValue(loads);
            return;
        }
        int need = loads * loadAmount;
        var ext = net.getUnifiedStorage().extract(new ItemStackKey(stack), need, false, false);
        if (ext.amount() > 0) { net.setDirty(); cir.setReturnValue((int) (ext.amount() / loadAmount)); }
    }

    @Unique
    private void handleConsumeMaidItem(LivingEntity living, int loads, CallbackInfoReturnable<Integer> cir) {
        if (!net.neoforged.fml.ModList.get().isLoaded("touhou_little_maid")) return;
        var maidNet = MaidNetworkHelper.findTerminal(living);
        if (maidNet == null) return;
        if (maidNet instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
            cir.setReturnValue(loads);
            return;
        }
        int need = loads * loadAmount;
        var ext = maidNet.getUnifiedStorage().extract(new ItemStackKey(stack), need, false, false);
        if (ext.amount() > 0) { maidNet.setDirty(); cir.setReturnValue((int) (ext.amount() / loadAmount)); }
    }

    @Unique
    private static boolean shouldNotify(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = beyond$lastNotificationTime.get(player.getUUID());
        if (last != null && now - last < NOTIFICATION_COOLDOWN_MS) return false;
        beyond$lastNotificationTime.put(player.getUUID(), now);
        return true;
    }

    // ═══════════════════════════════════════════════════════
    //  推送方法
    // ═══════════════════════════════════════════════════════

    private static void pushUpdate(ServerPlayer player, DimensionsNet net) {
        if (!(net instanceof SuperbAmmoAccessor acc)) return;
        var ammo = acc.getSuperbAmmo();
        if (ammo.isEmpty()) return;
        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        String name = net instanceof com.solr98.beyondintegration.handler.NetworkNameProvider nnp ? nnp.getCustomName() : "";
        boolean enchantSep = !(net instanceof EnchantSeparationAccessor ea) || ea.beyond$isEnchantSeparationEnabled();
        PacketDistributor.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                net.getId(), new HashMap<>(ammo), energy, 0, name, enchantSep));
    }

    private static void pushVehicleUpdate(ServerPlayer player, DimensionsNet net, int netId) {
        if (!(net instanceof SuperbAmmoAccessor acc)) return;
        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        String name = net instanceof com.solr98.beyondintegration.handler.NetworkNameProvider nnp ? nnp.getCustomName() : "";
        boolean enchantSep = !(net instanceof EnchantSeparationAccessor ea) || ea.beyond$isEnchantSeparationEnabled();
        PacketDistributor.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                netId, new HashMap<>(acc.getSuperbAmmo()), energy, 1, name, enchantSep));
    }
}
