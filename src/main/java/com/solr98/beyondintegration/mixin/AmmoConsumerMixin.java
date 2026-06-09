package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.InventoryTool;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.NetworkNotificationTracker;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.VehicleNetStorage;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = AmmoConsumer.class, remap = false)
public abstract class AmmoConsumerMixin {

    @Shadow(remap = false)
    private AmmoConsumer.AmmoConsumeType type;

    @Shadow(remap = false)
    private Ammo playerAmmoType;

    @Shadow(remap = false)
    private int loadAmount;

    @Shadow(remap = false)
    private ItemStack stack;

    @Inject(method = "consume(Lcom/atsuishio/superbwarfare/data/gun/GunData;Lnet/minecraft/world/entity/Entity;I)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void onConsume(GunData data, Entity entity, int loads,
                           CallbackInfoReturnable<Integer> cir) {
        if (type == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            handleConsumePlayerAmmo(entity, loads, cir);
        } else if (type == AmmoConsumer.AmmoConsumeType.ITEM) {
            handleConsumeItem(entity, loads, cir);
        }
    }

    @Unique
    private void handleConsumePlayerAmmo(Entity entity, int loads,
                                          CallbackInfoReturnable<Integer> cir) {
        if (playerAmmoType == null) return;

        if (entity instanceof ServerPlayer player) {
            consumePlayerAmmoFromServerPlayer(player, loads, cir);
        } else if (entity instanceof VehicleEntity vehicle) {
            consumePlayerAmmoFromVehicle(vehicle, loads, cir);
        } else if (entity instanceof LivingEntity living) {
            consumePlayerAmmoFromMaid(living, loads, cir);
        }
    }

    @Unique
    private void consumePlayerAmmoFromServerPlayer(ServerPlayer player, int loads,
                                                    CallbackInfoReturnable<Integer> cir) {
        if (player.getAbilities().instabuild) return;

        String key = playerAmmoType.serializationName;
        int need = loads * loadAmount;

        int personal = playerAmmoType.get(player);
        int fromPersonal = Math.min(personal, need);
        playerAmmoType.add(player, -fromPersonal);
        int remaining = need - fromPersonal;
        int consumed = fromPersonal / loadAmount;

        if (remaining > 0) {
            var handler = player.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
            if (handler != null) {
                int needed = remaining / loadAmount;
                int fromInv = InventoryTool.consumeAmmoItem(handler, playerAmmoType, needed);
                consumed += fromInv;
                remaining -= fromInv * loadAmount;
            }
        }

        DimensionsNet usedNet = null;
        if (remaining > 0) {
            for (DimensionsNet net : DimensionsNet.getAllNetFromPlayer(player)) {
                if (!(net instanceof SuperbAmmoAccessor acc)) continue;
                var map = acc.getSuperbAmmo();
                if (map.getOrDefault("__infinite__", 0L) > 0) {
                    consumed = loads;
                    remaining = 0;
                    usedNet = net;
                    break;
                }
                long avail = map.getOrDefault(key, 0L);
                if (avail > 0) {
                    long take = Math.min(avail, remaining);
                    map.put(key, avail - take);
                    consumed += (int) (take / loadAmount);
                    remaining -= (int) take;
                    net.setDirty();
                    usedNet = net;
                    if (remaining <= 0) break;
                }
            }
        }

        cir.setReturnValue(Math.min(consumed, loads));
        if (consumed > 0 && usedNet != null) {
            pushUpdate(player, usedNet);
            if (NetworkNotificationTracker.tryNotify(player.getUUID())) {
                var primary = DimensionsNet.getPrimaryNetFromPlayer(player);
                int id = usedNet.getId();
                if (primary != null && primary.getId() == id)
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.beyond_integration.using_primary_net_ammo"));
                else
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.beyond_integration.using_net_ammo", id));
            }
        }
    }

    @Unique
    private void consumePlayerAmmoFromVehicle(VehicleEntity vehicle, int loads,
                                               CallbackInfoReturnable<Integer> cir) {
        int boundNetId = VehicleNetStorage.getBoundNetId(vehicle.getUUID());
        if (boundNetId < 0) return;
        DimensionsNet boundNet = DimensionsNet.getNetFromId(boundNetId);
        if (!(boundNet instanceof SuperbAmmoAccessor acc)) {
            if (boundNet == null) VehicleNetStorage.unbindVehicle(vehicle.getUUID());
            return;
        }

        var map = acc.getSuperbAmmo();
        String key = playerAmmoType.serializationName;
        int need = loads * loadAmount;
        int taken = 0;

        if (map.getOrDefault("__infinite__", 0L) > 0) {
            taken = loads;
        } else {
            long avail = map.getOrDefault(key, 0L);
            if (avail > 0) {
                long take = Math.min(avail, need);
                map.put(key, avail - take);
                taken = (int) (take / loadAmount);
                boundNet.setDirty();
            }
        }

        if (taken > 0) {
            cir.setReturnValue(Math.min(taken, loads));
            for (Entity p : vehicle.getPassengers())
                if (p instanceof ServerPlayer sp) pushVehicleUpdate(sp, boundNet, boundNetId);
        }
    }

    @Unique
    private void consumePlayerAmmoFromMaid(LivingEntity living, int loads,
                                            CallbackInfoReturnable<Integer> cir) {
        DimensionsNet net = MaidNetworkHelper.findTerminal(living);
        if (net == null || !(net instanceof SuperbAmmoAccessor acc)) return;

        var map = acc.getSuperbAmmo();
        String key = playerAmmoType.serializationName;
        int need = loads * loadAmount;
        int taken = 0;

        if (map.getOrDefault("__infinite__", 0L) > 0) {
            taken = loads;
        } else {
            long networkAmmo = map.getOrDefault(key, 0L);
            if (networkAmmo > 0) {
                long take = Math.min(networkAmmo, need);
                map.put(key, networkAmmo - take);
                taken = (int) (take / loadAmount);
                net.setDirty();
            }
        }

        if (taken > 0) cir.setReturnValue(Math.min(taken, loads));
    }

    @Unique
    private void handleConsumeItem(Entity entity, int loads,
                                   CallbackInfoReturnable<Integer> cir) {
        if (stack.isEmpty()) return;

        if (entity instanceof VehicleEntity vehicle) {
            consumeItemFromVehicle(vehicle, loads, cir);
        } else if (entity instanceof ServerPlayer player) {
            consumeItemFromServerPlayer(player, loads, cir);
        } else if (entity instanceof LivingEntity living) {
            consumeItemFromMaid(living, loads, cir);
        }
    }

    @Unique
    private void consumeItemFromVehicle(VehicleEntity vehicle, int loads,
                                         CallbackInfoReturnable<Integer> cir) {
        int boundNetId = VehicleNetStorage.getBoundNetId(vehicle.getUUID());
        if (boundNetId < 0) return;
        DimensionsNet net = DimensionsNet.getNetFromId(boundNetId);
        if (net == null) {
            VehicleNetStorage.unbindVehicle(vehicle.getUUID());
            return;
        }

        int taken = 0;

        if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
            taken = loads;
        } else {
            KeyAmount extracted = net.getUnifiedStorage().extract(new ItemStackKey(stack), loads, false, false);
            if (extracted.amount() > 0) {
                taken += (int) extracted.amount();
                net.setDirty();
            }
        }

        if (taken > 0) cir.setReturnValue(taken);
    }

    @Unique
    private void consumeItemFromServerPlayer(ServerPlayer player, int loads,
                                              CallbackInfoReturnable<Integer> cir) {
        if (player.getAbilities().instabuild) return;

        int taken = 0;

        var handler = player.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler != null) {
            taken = InventoryTool.consumeItem(handler, s -> s.is(stack.getItem()), loads);
        }

        if (taken < loads) {
            ItemStackKey itemKey = new ItemStackKey(stack);
            for (DimensionsNet net : DimensionsNet.getAllNetFromPlayer(player)) {
                if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
                    taken = loads;
                    break;
                }
                KeyAmount extracted = net.getUnifiedStorage().extract(itemKey, loads - taken, false, false);
                if (extracted.amount() > 0) {
                    taken += (int) extracted.amount();
                    net.setDirty();
                }
                if (taken >= loads) break;
            }
        }

        if (taken > 0) cir.setReturnValue(taken);
    }

    @Unique
    private void consumeItemFromMaid(LivingEntity living, int loads,
                                     CallbackInfoReturnable<Integer> cir) {
        DimensionsNet maidNet = MaidNetworkHelper.findTerminal(living);
        if (maidNet == null) return;

        int taken = 0;

        if (maidNet instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
            taken = loads;
        } else {
            KeyAmount extracted = maidNet.getUnifiedStorage().extract(new ItemStackKey(stack), loads, false, false);
            if (extracted.amount() > 0) {
                taken += (int) extracted.amount();
                maidNet.setDirty();
            }
        }

        if (taken > 0) cir.setReturnValue(taken);
    }

    @Unique
    private static void pushUpdate(ServerPlayer player, DimensionsNet net) {
        if (!(net instanceof SuperbAmmoAccessor acc)) return;
        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        PacketHandler.sendToPlayer(player, SuperbAmmoStatusResponsePacket.fromNet(
                net, new HashMap<>(acc.getSuperbAmmo()), energy, 0,
                ((NetworkNameProvider) net).getCustomName()));
    }

    @Unique
    private static void pushVehicleUpdate(ServerPlayer player, DimensionsNet net, int netId) {
        if (!(net instanceof SuperbAmmoAccessor acc)) return;
        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        PacketHandler.sendToPlayer(player, SuperbAmmoStatusResponsePacket.fromNet(
                net, new HashMap<>(acc.getSuperbAmmo()), energy, 1,
                ((NetworkNameProvider) net).getCustomName()));
    }
}
