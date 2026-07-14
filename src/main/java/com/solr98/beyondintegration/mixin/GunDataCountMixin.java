package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunData.class, remap = false)
public abstract class GunDataCountMixin {

    @Inject(method = "hasBackupAmmo(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void onHasBackupAmmo(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        GunData self = (GunData) (Object) this;
        AmmoConsumer consumer = self.selectedAmmoConsumer();
        if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            Ammo ammoType = consumer.getPlayerAmmoType();
            if (ammoType == null) return;
            if (entity instanceof ServerPlayer player) {
                var acc = TaczAmmoExtractor.resolveSuperbAmmo(player);
                if (acc != null) {
                    var map = acc.getSuperbAmmo();
                    if (map.getOrDefault("__infinite__", 0L) > 0
                            || map.getOrDefault(ammoType.serializationName, 0L) > 0) {
                        cir.setReturnValue(true);
                    }
                }
            } else if (entity.level().isClientSide() && entity instanceof Player) {
                String key = ammoType.serializationName;
                if (SuperbAmmoCache.getCount("__infinite__") > 0
                        || SuperbAmmoCache.getCount(key) > 0) {
                    cir.setReturnValue(true);
                }
            } else if (entity instanceof LivingEntity living) {
                if (ModList.get().isLoaded("touhou_little_maid")) {
                    DimensionsNet net = MaidNetworkHelper.findTerminal(living);
                    if (net instanceof SuperbAmmoAccessor acc) {
                        var map = acc.getSuperbAmmo();
                        if (map.getOrDefault("__infinite__", 0L) > 0
                                || map.getOrDefault(ammoType.serializationName, 0L) > 0) {
                            cir.setReturnValue(true);
                        }
                    }
                }
            }
        } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM) {
            ItemStack ammoStack = consumer.stack();
            if (ammoStack.isEmpty()) return;
            DimensionsNet net = null;
            if (entity instanceof ServerPlayer player) {
                net = TaczAmmoExtractor.findNetworkForPlayer(player);
            } else if (entity instanceof LivingEntity living) {
                if (ModList.get().isLoaded("touhou_little_maid")) {
                    net = MaidNetworkHelper.findTerminal(living);
                }
            }
            if (net == null) return;
            if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
                cir.setReturnValue(true);
            } else if (countItemsInNetwork(net, ammoStack) > 0) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "countBackupAmmo(Lnet/minecraft/world/entity/Entity;)I",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void onCountBackupAmmo(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() >= Integer.MAX_VALUE / 2) return;
        GunData self = (GunData) (Object) this;
        AmmoConsumer consumer = self.selectedAmmoConsumer();
        if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            countPlayerAmmoFromNetwork(entity, cir, consumer);
        } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM) {
            countItemAmmoFromNetwork(entity, cir, consumer);
        }
    }

    private void countPlayerAmmoFromNetwork(Entity entity, CallbackInfoReturnable<Integer> cir, AmmoConsumer consumer) {
        Ammo ammoType = consumer.getPlayerAmmoType();
        if (ammoType == null) return;

        if (entity instanceof ServerPlayer player) {
            var acc = TaczAmmoExtractor.resolveSuperbAmmo(player);
            if (acc != null) {
                var map = acc.getSuperbAmmo();
                if (map.getOrDefault("__infinite__", 0L) > 0) {
                    cir.setReturnValue(Integer.MAX_VALUE);
                    return;
                }
                long n = map.getOrDefault(ammoType.serializationName, 0L);
                if (n > 0) {
                    cir.setReturnValue((int) Math.min((long) cir.getReturnValue() + n, Integer.MAX_VALUE));
                }
            }
            return;
        }

        if (entity.level().isClientSide() && entity instanceof Player) {
            String key = ammoType.serializationName;
            if (SuperbAmmoCache.getCount("__infinite__") > 0) {
                cir.setReturnValue(Integer.MAX_VALUE);
                return;
            }
            long n = SuperbAmmoCache.getCount(key);
            if (n > 0) {
                cir.setReturnValue((int) Math.min((long) cir.getReturnValue() + n, Integer.MAX_VALUE));
            }
            return;
        }

        if (entity instanceof LivingEntity living) {
            if (ModList.get().isLoaded("touhou_little_maid")) {
                DimensionsNet net = MaidNetworkHelper.findTerminal(living);
                if (net instanceof SuperbAmmoAccessor acc) {
                    var map = acc.getSuperbAmmo();
                    if (map.getOrDefault("__infinite__", 0L) > 0) {
                        cir.setReturnValue(Integer.MAX_VALUE);
                        return;
                    }
                    long n = map.getOrDefault(ammoType.serializationName, 0L);
                    if (n > 0) {
                        cir.setReturnValue((int) Math.min((long) cir.getReturnValue() + n, Integer.MAX_VALUE));
                    }
                }
            }
            return;
        }

        if (entity instanceof VehicleEntity vehicle) {
            DimensionsNet net = VehicleNetStorage.getNetworkForVehicle(vehicle.getUUID());
            if (net == null) return;
            if (net instanceof SuperbAmmoAccessor acc) {
                var map = acc.getSuperbAmmo();
                if (map.getOrDefault("__infinite__", 0L) > 0) {
                    cir.setReturnValue(Integer.MAX_VALUE);
                    return;
                }
                long n = map.getOrDefault(ammoType.serializationName, 0L);
                if (n > 0) {
                    cir.setReturnValue((int) Math.min(n, Integer.MAX_VALUE));
                }
            }
        }
    }

    private void countItemAmmoFromNetwork(Entity entity, CallbackInfoReturnable<Integer> cir, AmmoConsumer consumer) {
        ItemStack ammoStack = consumer.stack();
        if (ammoStack.isEmpty()) return;
        if (entity.level().isClientSide()) return;

        DimensionsNet net = null;
        if (entity instanceof ServerPlayer player) {
            net = TaczAmmoExtractor.findNetworkForPlayer(player);
        } else if (entity instanceof VehicleEntity vehicle) {
            net = VehicleNetStorage.getNetworkForVehicle(vehicle.getUUID());
        } else if (entity instanceof LivingEntity living) {
            if (ModList.get().isLoaded("touhou_little_maid")) {
                net = com.solr98.beyondintegration.maid.MaidNetworkHelper.findTerminal(living);
            }
        }
        if (net == null) return;

        if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
            cir.setReturnValue(Integer.MAX_VALUE);
            return;
        }

        long total = countItemsInNetwork(net, ammoStack);
        if (total > 0) {
            cir.setReturnValue((int) Math.min(total, Integer.MAX_VALUE));
        }
    }

    private static long countItemsInNetwork(DimensionsNet net, ItemStack target) {
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
