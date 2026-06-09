package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.NetworkSavedData;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(value = DimensionsNet.class, remap = false)
public class DimensionsNetMixin implements SuperbAmmoAccessor, NetworkNameProvider, EnchantSeparationAccessor, TaczCreativeAccessor {

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
                            NetworkSavedData.markDirty();
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
                    NetworkSavedData.markDirty();
                }
            });
    }

    @Override
    public Map<String, Long> getSuperbAmmo() {
        beyond$initDeltaHook();
        return NetworkSavedData.getOrCreate(self().getId()).getSuperbAmmo();
    }

    @Override
    public void setSuperbAmmo(Map<String, Long> map) {
        beyond$initDeltaHook();
        NetworkSavedData.getOrCreate(self().getId()).setSuperbAmmo(map);
    }

    @Override
    public boolean beyond$isEnchantSeparationEnabled() {
        return NetworkSavedData.getOrCreate(self().getId()).isEnchantSeparation();
    }

    @Override
    public void beyond$setEnchantSeparationEnabled(boolean v) {
        NetworkSavedData.getOrCreate(self().getId()).setEnchantSeparation(v);
        NetworkSavedData.markDirty();
    }

    @Override
    public Map<String, Integer> getTaczCreativeCounts() {
        beyond$initDeltaHook();
        return NetworkSavedData.getOrCreate(self().getId()).getTaczCreativeTypeCounts();
    }

    @Override
    public void setTaczCreativeCounts(Map<String, Integer> counts) {
        beyond$initDeltaHook();
        Map<String, Integer> map = NetworkSavedData.getOrCreate(self().getId()).getTaczCreativeTypeCounts();
        map.clear();
        if (counts != null) map.putAll(counts);
    }
}
