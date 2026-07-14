package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.AuditEntry;
import com.solr98.beyondintegration.feature.bind.BindingAuditLog;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.feature.bind.IBindingTokenHolder;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(targets = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet", remap = false)
public class DimensionsNetMixin implements SuperbAmmoAccessor, NetworkNameProvider, EnchantSeparationAccessor, TaczCreativeAccessor, IBindingTokenHolder {

    @Unique
    private boolean beyond$deltaInit = false;

    private DimensionsNet self() { return (DimensionsNet) (Object) this; }

    @Unique
    private synchronized void beyond$initDeltaHook() {
        if (beyond$deltaInit) return;
        beyond$deltaInit = true;
        DimensionsNet self = self();
        self.getUnifiedStorage().subscribeDelta("beyond_creative", (key, size, insert) -> {
                if (!(key instanceof ItemStackKey ik)) return;
                ItemStack stack = ik.getReadOnlyStack();

                if (ModList.get().isLoaded("tacz")) {
                    try {
                        Class<?> iaBbox = Class.forName("com.tacz.guns.api.item.IAmmoBox");
                        if (iaBbox.isInstance(stack.getItem())) {
                            Map<String, Integer> counts = getTaczCreativeCounts();
                            String typeKey;
                            Boolean allType = (Boolean) iaBbox.getMethod("isAllTypeCreative", ItemStack.class).invoke(stack.getItem(), stack);
                            if (allType) {
                                typeKey = "*";
                            } else {
                                Boolean creative = (Boolean) iaBbox.getMethod("isCreative", ItemStack.class).invoke(stack.getItem(), stack);
                                if (creative) {
                                    ResourceLocation ammoId = (ResourceLocation) iaBbox.getMethod("getAmmoId", ItemStack.class).invoke(stack.getItem(), stack);
                                    if (ammoId == null) return;
                                    typeKey = ammoId.toString();
                                } else {
                                    return;
                                }
                            }
                            if (insert) {
                                counts.merge(typeKey, (int) size, Integer::sum);
                            } else {
                                counts.computeIfPresent(typeKey, (k, v) -> v <= (int) size ? null : v - (int) size);
                            }
                            NetworkAmmoData.markDirty();
                            return;
                        }
                    } catch (Exception ignored) {}
                }

                ResourceLocation regKey = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (regKey != null && "superbwarfare".equals(regKey.getNamespace()) && regKey.getPath().contains("creative")) {
                    Map<String, Long> superbAmmo = getSuperbAmmo();
                    long current = superbAmmo.getOrDefault("__infinite__", 0L);
                    if (insert) {
                        superbAmmo.put("__infinite__", current + size);
                    } else {
                        long remaining = current - size;
                        if (remaining <= 0) superbAmmo.remove("__infinite__");
                        else superbAmmo.put("__infinite__", remaining);
                    }
                    NetworkAmmoData.markDirty();
                }
            });
    }

    @Override
    public Map<String, Long> getSuperbAmmo() {
        beyond$initDeltaHook();
        return NetworkAmmoData.getOrCreate(self().getId()).getSuperbAmmo();
    }

    @Override
    public void setSuperbAmmo(Map<String, Long> map) {
        beyond$initDeltaHook();
        NetworkAmmoData.getOrCreate(self().getId()).setSuperbAmmo(map);
    }

    @Override
    public boolean beyond$isEnchantSeparationEnabled() {
        return NetworkAmmoData.getOrCreate(self().getId()).isEnchantSeparation();
    }

    @Override
    public void beyond$setEnchantSeparationEnabled(boolean v) {
        NetworkAmmoData.getOrCreate(self().getId()).setEnchantSeparation(v);
        NetworkAmmoData.markDirty();
    }

    @Override
    public Map<String, Integer> getTaczCreativeCounts() {
        beyond$initDeltaHook();
        return NetworkAmmoData.getOrCreate(self().getId()).getTaczCreativeTypeCounts();
    }

    @Override
    public void setTaczCreativeCounts(Map<String, Integer> counts) {
        beyond$initDeltaHook();
        Map<String, Integer> map = NetworkAmmoData.getOrCreate(self().getId()).getTaczCreativeTypeCounts();
        map.clear();
        if (counts != null) map.putAll(counts);
    }

    // ========== Lifecycle audit hooks ==========

    @Inject(method = "destroySelf", at = @At("HEAD"), remap = false)
    private void beyond$onDestroySelf(CallbackInfo ci) {
        if (!CommandConfig.enableAuditLog()) return;
        DimensionsNet self = self();
        BindingAuditLog.log(new AuditEntry(
                System.currentTimeMillis(), "NET_DESTROY",
                "-", null,
                self.getId(), "-", "-",
                true, "Network #" + self.getId() + " destroyed"
        ));
    }

    @Inject(method = "mergeOtherNet", at = @At("HEAD"), remap = false)
    private void beyond$onMergeOtherNet(DimensionsNet other, CallbackInfo ci) {
        if (!CommandConfig.enableAuditLog()) return;
        DimensionsNet self = self();
        BindingAuditLog.log(new AuditEntry(
                System.currentTimeMillis(), "NET_MERGE",
                "-", null,
                self.getId(), "-", String.valueOf(other.getId()),
                true, "Network #" + other.getId() + " merged into #" + self.getId()
        ));
    }

    @Override
    public java.util.UUID getBindingToken() {
        return BindingTokenManager.getOrCreateToken(self().getId(), self().getOwner());
    }

    @Override
    public java.util.UUID resetBindingToken() {
        return BindingTokenManager.resetToken(self().getId(), self().getOwner());
    }
}
