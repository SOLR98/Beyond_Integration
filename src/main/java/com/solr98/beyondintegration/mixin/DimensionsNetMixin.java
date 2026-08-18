package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
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

/**
 * 注入 BeyondDimensions 的 {@link DimensionsNet}，为其实现四个本模组扩展接口：
 * SuperbAmmoAccessor（SW 网络弹药存取）、NetworkNameProvider（网络名称）、
 * EnchantSeparationAccessor（附魔分离开关）、TaczCreativeAccessor（TACZ 创造弹药计数），
 * 数据统一挂在 NetworkAmmoData 上，并订阅存储变化以同步创造模式弹药。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet", remap = false)
public class DimensionsNetMixin implements SuperbAmmoAccessor, NetworkNameProvider, EnchantSeparationAccessor, TaczCreativeAccessor {

    /** 增量钩子是否已初始化的标记（只初始化一次） */
    @Unique
    private boolean beyond$deltaInit = false;

    /** 将 this 强转为 DimensionsNet（Mixin 惯用法） */
    private DimensionsNet self() { return (DimensionsNet) (Object) this; }

    /** 订阅统一存储的增量事件：把进出的创造模式弹药（TACZ 弹盒 / SW 创造弹药）同步进网络弹药缓存 */
    @Unique
    private synchronized void beyond$initDeltaHook() {
        if (beyond$deltaInit) return;
        beyond$deltaInit = true;
        // 经统一订阅中心注册（弱引用订阅 + 网络销毁/服务器停止时自动清理）
        BdSubscriptionHub.subscribe(self().getId(), this, (key, size, insert) -> {
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
}
