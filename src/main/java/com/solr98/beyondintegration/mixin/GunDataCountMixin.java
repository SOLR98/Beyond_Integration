package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入 Superb Warfare 的 {@link GunData}，扩展备用弹药（backup ammo）判定与计数：
 * 将维度网络中的弹药计入备用弹药（玩家/物品两类消耗），覆盖玩家、载具、女仆实体，
 * 使 HUD 与换弹判定能感知网络弹药；网络存在无限弹药时返回 Integer.MAX_VALUE。
 */
@Mixin(value = GunData.class, remap = false)
public abstract class GunDataCountMixin {

    /** 扩展 hasBackupAmmo：网络（玩家网络 / 女仆终端 / 载具网络）中存在弹药时视为有备用弹药 */
    @Inject(method = "hasBackupAmmo(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void onHasBackupAmmo(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // SW 0.8.9.1+ 支持 entity 为 null（函数内 null 早退），此处同样跳过网络判定
        if (entity == null) return;
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
        } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM && beyond$isExpAmmo(consumer)) {
            // 经验弹药：玩家经验或网络 XP 流体（可自动转化为经验）存在即视为有备用弹药
            DimensionsNet net = null;
            if (entity instanceof ServerPlayer player) {
                net = DimensionsNet.getPrimaryNetFromPlayer(player);
            } else if (entity instanceof LivingEntity living) {
                if (ModList.get().isLoaded("touhou_little_maid")) {
                    net = MaidNetworkHelper.findTerminal(living);
                }
            }
            if (entity instanceof ServerPlayer p && p.totalExperience > 0) {
                cir.setReturnValue(true);
                return;
            }
            if (net != null && net.getUnifiedStorage()
                    .getStackByKey(com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler.xpFluidKey()).amount() > 0) {
                cir.setReturnValue(true);
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

    /** 扩展 countBackupAmmo：把网络弹药数量叠加进备用弹药计数 */
    @Inject(method = "countBackupAmmo(Lnet/minecraft/world/entity/Entity;)I",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void onCountBackupAmmo(Entity entity, CallbackInfoReturnable<Integer> cir) {
        // SW 0.8.9.1+ 载具 BVR 同步以 null 调用 countBackupAmmo（函数内 null 早退返回 virtualAmmo）；
        // 此处必须同样跳过网络判定，否则 entity.level() 抛 NPE（crash-2026-08-23）
        if (entity == null) return;
        if (cir.getReturnValue() >= Integer.MAX_VALUE / 2) return;
        GunData self = (GunData) (Object) this;
        AmmoConsumer consumer = self.selectedAmmoConsumer();
        if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            beyond$countPlayerAmmoFromNetwork(entity, cir, consumer);
        } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM && beyond$isExpAmmo(consumer)) {
            // 经验弹药（ExpAmmoStrategy）：网络 XP 流体可自动转化为玩家经验补给
            // 玩家已有经验由原逻辑计入；此处叠加网络可转化经验（按 1 经验 = 1 弹药保守折算）
            DimensionsNet net = null;
            if (entity instanceof ServerPlayer player) {
                net = DimensionsNet.getPrimaryNetFromPlayer(player);
            } else if (entity instanceof LivingEntity living) {
                if (ModList.get().isLoaded("touhou_little_maid")) {
                    net = MaidNetworkHelper.findTerminal(living);
                }
            }
            if (net == null) return;
            long netXp = net.getUnifiedStorage()
                    .getStackByKey(com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler.xpFluidKey()).amount() / 20;
            if (netXp > 0) {
                cir.setReturnValue((int) Math.min((long) cir.getReturnValue() + netXp, Integer.MAX_VALUE));
            }
        } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM) {
            beyond$countItemAmmoFromNetwork(entity, cir, consumer);
        }
    }

    /** 判断消耗器是否为经验弹药（ammo 字符串以 "exp" 开头，SW ExpAmmoStrategy） */
    @Unique
    private static boolean beyond$isExpAmmo(AmmoConsumer consumer) {
        try {
            String ammo = consumer.getAmmo();
            return ammo != null && ammo.toLowerCase(java.util.Locale.ROOT).startsWith("exp");
        } catch (Exception e) {
            return false;
        }
    }

    /** 统计玩家弹药类网络备用数量：无限弹药返回 MAX_VALUE，否则叠加网络计数 */
    @Unique
    private void beyond$countPlayerAmmoFromNetwork(Entity entity, CallbackInfoReturnable<Integer> cir, AmmoConsumer consumer) {
        if (entity == null) return;
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
            VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
            DimensionsNet net = cache.getNet();
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

    /** 统计物品类网络备用数量：无限弹药返回 MAX_VALUE，否则统计网络中匹配物品总量 */
    @Unique
    private void beyond$countItemAmmoFromNetwork(Entity entity, CallbackInfoReturnable<Integer> cir, AmmoConsumer consumer) {
        if (entity == null) return;
        ItemStack ammoStack = consumer.stack();
        if (ammoStack.isEmpty()) return;
        if (entity.level().isClientSide()) return;

        DimensionsNet net = null;
        if (entity instanceof ServerPlayer player) {
            net = TaczAmmoExtractor.findNetworkForPlayer(player);
        } else if (entity instanceof VehicleEntity vehicle) {
            VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
            net = cache.getNet();
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

    /** SW 物品弹药无 NBT 变种：reference key 精确查询（O(1)，与扣减同口径） */
    private static long countItemsInNetwork(DimensionsNet net, ItemStack target) {
        if (net == null) return 0;
        return net.getUnifiedStorage().getStackByKey(new ItemStackKey(target)).amount();
    }
}
