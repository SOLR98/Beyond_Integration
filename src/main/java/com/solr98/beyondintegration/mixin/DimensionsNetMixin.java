package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.solr98.beyondintegration.handler.YwzjCreativeAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mixin(targets = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet", remap = false)
public class DimensionsNetMixin implements SuperbAmmoAccessor, NetworkNameProvider, EnchantSeparationAccessor, TaczCreativeAccessor, YwzjCreativeAccessor {
    @Unique private Map<String, Long> beyond$superbAmmo = new HashMap<>();
    @Unique private boolean beyond$ammoLoaded = false;
    @Unique private boolean beyond$enchantSeparation = true;
    @Unique private boolean beyond$deltaInit = false;
    @Unique private Set<String> beyond$taczCreativeTypes = new HashSet<>();
    @Unique private boolean beyond$ywzjCreativeAmmo = false;

    @Unique
    private void beyond$loadFromDisk() {
        if (beyond$ammoLoaded) return;
        beyond$ammoLoaded = true;
        int netId = ((DimensionsNet) (Object) this).getId();
        if (netId < 0) return;
        NetworkAmmoData data = NetworkAmmoData.get();
        Map<String, Long> saved = data.getAmmoForNet(netId);
        if (!saved.isEmpty()) beyond$superbAmmo = new HashMap<>(saved);
        beyond$enchantSeparation = data.getEnchantSeparation(netId);
        var savedTypes = data.getCreativeTypesForNet(netId);
        if (!savedTypes.isEmpty()) beyond$taczCreativeTypes = new HashSet<>(savedTypes);
        beyond$ywzjCreativeAmmo = data.getYwzjCreativeAmmo(netId);
    }

    @Unique
    private synchronized void beyond$initExtractHook() {
        if (beyond$deltaInit) return;
        beyond$deltaInit = true;
        DimensionsNet self = (DimensionsNet) (Object) this;
        var storage = self.getUnifiedStorage();
        if (storage instanceof AbstractUnorderedStackHandler handler) {
            handler.subscribeDelta("beyond_creative", (key, size, insert) -> {
                if (insert) return;
                if (key instanceof ItemStackKey ik) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(ik.getReadOnlyStack().getItem());
                    if (id == null) return;
                    // Superb Warfare 创造箱取出 → 清除无限标记
                    if ("superbwarfare".equals(id.getNamespace()) && id.getPath().contains("creative")) {
                        beyond$superbAmmo.remove("__infinite__");
                        self.setDirty();
                    }
                    // TACZ 创造箱取出 → 检查 custom_data 中的 Creative/AllTypeCreative 标记
                    if ("tacz".equals(id.getNamespace()) && "ammo_box".equals(id.getPath())) {
                        var customData = ik.getReadOnlyStack().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                        if (customData != null) {
                            var tag = customData.copyTag();
                            if (tag.contains("Creative") || tag.contains("AllTypeCreative")) {
                                beyond$taczCreativeTypes.clear();
                                self.setDirty();
                            }
                        }
                    }
                    // YWZJ 创造弹药取出 → 取消无限弹药标记
                    if ("ywzj_vehicle".equals(id.getNamespace()) && "ammo_creative".equals(id.getPath())) {
                        beyond$ywzjCreativeAmmo = false;
                        self.setDirty();
                    }
                }
            });
        }
    }

    @Override
    public Map<String, Long> getSuperbAmmo() { beyond$loadFromDisk(); beyond$initExtractHook(); return beyond$superbAmmo; }

    @Override
    public void setSuperbAmmo(Map<String, Long> map) {
        beyond$loadFromDisk();
        beyond$superbAmmo.clear();
        if (map != null) beyond$superbAmmo.putAll(map);
    }

    @Override
    public Set<String> getTaczCreativeTypes() { beyond$loadFromDisk(); beyond$initExtractHook(); return beyond$taczCreativeTypes; }

    @Override
    public void setTaczCreativeTypes(Set<String> types) {
        beyond$taczCreativeTypes.clear();
        if (types != null) beyond$taczCreativeTypes.addAll(types);
    }

    @Override
    public boolean beyond$isEnchantSeparationEnabled() { beyond$loadFromDisk(); return beyond$enchantSeparation; }

    @Override
    public void beyond$setEnchantSeparationEnabled(boolean v) { beyond$enchantSeparation = v; }

    @Override
    public boolean beyond$isYwzjCreativeAmmo() { beyond$loadFromDisk(); beyond$initExtractHook(); return beyond$ywzjCreativeAmmo; }

    @Override
    public void beyond$setYwzjCreativeAmmo(boolean creative) { beyond$ywzjCreativeAmmo = creative; }
}
