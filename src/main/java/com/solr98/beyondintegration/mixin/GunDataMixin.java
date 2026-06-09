package com.solr98.beyondintegration.mixin;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.command.util.NetworkUtils;
import com.solr98.beyondintegration.handler.FakePlayerNetMarker;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.VehicleNetStorage;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.atsuishio.superbwarfare.data.gun.GunData", remap = false)
public class GunDataMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private boolean beyond$isBackpackMode() {
        try { return ((GunData) (Object) this).useBackpackAmmo(); } catch (Exception e) { return false; }
    }

    @Inject(method = "hasBackupAmmo", at = @At("RETURN"), cancellable = true, remap = false)
    private void onHasBackupAmmo(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        try {
            var consumer = ((GunData) (Object) this).selectedAmmoConsumer();
            if (consumer == null) return;
            var type = consumer.getType();
            if (type == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
                var ammoType = consumer.getPlayerAmmoType();
                if (ammoType == null) return;
                String key = ammoType.serializationName;
                if (SuperbAmmoCache.INSTANCE.hasData() && SuperbAmmoCache.INSTANCE.hasInfinite()) { cir.setReturnValue(true); return; }
                if (entity instanceof VehicleEntity && SuperbAmmoCache.INSTANCE.vehicleHasData()) {
                    if (SuperbAmmoCache.INSTANCE.getVehicleCount("__infinite__") > 0) { cir.setReturnValue(true); return; }
                    if (SuperbAmmoCache.INSTANCE.getVehicleCount(key) > 0) { cir.setReturnValue(true); return; }
                }
                for (var net : getNets(entity)) {
                    if (!(net instanceof SuperbAmmoAccessor acc)) continue;
                    var m = acc.getSuperbAmmo();
                    if (m.getOrDefault("__infinite__", 0L) > 0 || m.getOrDefault(key, 0L) > 0) { cir.setReturnValue(true); return; }
                }
                if (SuperbAmmoCache.INSTANCE.hasData() && SuperbAmmoCache.INSTANCE.getCount(key) > 0) cir.setReturnValue(true);
            } else if (type == AmmoConsumer.AmmoConsumeType.ITEM) {
                ItemStack ammoStack = consumer.stack();
                if (ammoStack.isEmpty()) return;
                for (var net : getNets(entity)) {
                    if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) { cir.setReturnValue(true); return; }
                    if (countItems(net, ammoStack) > 0) { cir.setReturnValue(true); return; }
                }
            }
        } catch (Exception e) { LOGGER.warn("hasBackupAmmo error", e); }
    }

    @Inject(method = "countBackupAmmo", at = @At("RETURN"), cancellable = true, remap = false)
    private void onCountBackupAmmo(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() >= Integer.MAX_VALUE / 2) return;
        // 客户端 HUD: 只对 useBackpackAmmo 枪显示网络弹药
        if (entity != null && entity.level() != null && entity.level().isClientSide() && !beyond$isBackpackMode()) return;
        try {
            var consumer = ((GunData) (Object) this).selectedAmmoConsumer();
            if (consumer == null) return;
            var type = consumer.getType();
            if (type == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
                var ammoType = consumer.getPlayerAmmoType();
                if (ammoType == null) return;
                String key = ammoType.serializationName;
                if (SuperbAmmoCache.INSTANCE.hasData() && SuperbAmmoCache.INSTANCE.hasInfinite()) { cir.setReturnValue(Integer.MAX_VALUE); return; }
                if (entity instanceof VehicleEntity && SuperbAmmoCache.INSTANCE.vehicleHasData()) {
                    if (SuperbAmmoCache.INSTANCE.getVehicleCount("__infinite__") > 0) { cir.setReturnValue(Integer.MAX_VALUE); return; }
                    long vc = SuperbAmmoCache.INSTANCE.getVehicleCount(key);
                    if (vc > 0) { cir.setReturnValue((int) Math.min(cir.getReturnValue() + vc, Integer.MAX_VALUE)); return; }
                }
                for (var net : getNets(entity)) {
                    if (!(net instanceof SuperbAmmoAccessor acc)) continue;
                    if (acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) { cir.setReturnValue(Integer.MAX_VALUE); return; }
                }
                for (var net : getNets(entity)) {
                    if (!(net instanceof SuperbAmmoAccessor acc)) continue;
                    long n = acc.getSuperbAmmo().getOrDefault(key, 0L);
                    if (n > 0) { cir.setReturnValue((int) Math.min(cir.getReturnValue() + n, Integer.MAX_VALUE)); return; }
                }
                if (SuperbAmmoCache.INSTANCE.hasData()) {
                    long n = SuperbAmmoCache.INSTANCE.getCount(key);
                    if (n > 0) cir.setReturnValue((int) Math.min(cir.getReturnValue() + n, Integer.MAX_VALUE));
                }
            } else if (type == AmmoConsumer.AmmoConsumeType.ITEM) {
                ItemStack ammoStack = consumer.stack();
                if (ammoStack.isEmpty()) return;
                for (var net : getNets(entity))
                    if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) { cir.setReturnValue(Integer.MAX_VALUE); return; }
                for (var net : getNets(entity)) {
                    long n = countItems(net, ammoStack);
                    if (n > 0) { cir.setReturnValue((int) Math.min(cir.getReturnValue() + n, Integer.MAX_VALUE)); return; }
                }
            }
        } catch (Exception e) { LOGGER.warn("countBackupAmmo error", e); }
    }

    private static long countItems(DimensionsNet net, ItemStack target) {
        var opt = net.getUnifiedStorage().getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return 0;
        var bucket = opt.get();
        var targetKey = new ItemStackKey(target);
        long total = 0;
        for (int i = 0; i < bucket.size(); i++) {
            if (bucket.get(i) instanceof ItemStackKey ik && ik.isSame(targetKey))
                total += net.getUnifiedStorage().getStackByKey(ik).amount();
        }
        return total;
    }

    private static List<DimensionsNet> getNets(Entity entity) {
        List<DimensionsNet> nets = new ArrayList<>();
        if (entity instanceof ServerPlayer p) nets.addAll(NetworkUtils.getPlayerNetsPrimaryFirst(p));
        if (entity instanceof VehicleEntity v) { int id = VehicleNetStorage.getBoundNetId(v.getUUID()); if (id >= 0) { var n = DimensionsNet.getNetFromId(id); if (n != null) nets.add(n); } }
        if (entity instanceof FakePlayer fp) { var n = FakePlayerNetMarker.getNet(fp); if (n != null) nets.add(n); }
        if (entity instanceof LivingEntity living && net.neoforged.fml.ModList.get().isLoaded("touhou_little_maid")) {
            var n = MaidNetworkHelper.findTerminal(living);
            if (n != null) nets.add(n);
        }
        return nets;
    }
}
