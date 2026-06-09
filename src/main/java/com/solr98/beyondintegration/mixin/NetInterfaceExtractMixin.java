package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.NetInterfaceBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.block.entity.NetInterfaceBlockEntity", remap = false)
public class NetInterfaceExtractMixin {

    @Redirect(method = "transferFromNet",
              at = @At(value = "INVOKE",
                       target = "Lcom/wintercogs/beyonddimensions/api/dimensionnet/UnifiedStorage;extract(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JZZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;"),
              remap = false)
    private KeyAmount redirectExtract(UnifiedStorage storage, IStackKey<?> key, long amount, boolean simulate, boolean fuzzy) {
        DimensionsNet net = null;
        try {
            net = ((NetInterfaceBlockEntity) (Object) this).getNet();
        } catch (Exception ignored) {
        }

        if (!ModList.get().isLoaded("superbwarfare") || net == null || !(net instanceof SuperbAmmoAccessor acc)) {
            return storage.extract(key, amount, simulate, fuzzy);
        }
        if (!(key instanceof ItemStackKey itemKey)) {
            return storage.extract(key, amount, simulate, fuzzy);
        }

        ResourceLocation regId = BuiltInRegistries.ITEM.getKey(itemKey.getSource());
        if (regId == null || !"superbwarfare".equals(regId.getNamespace())) {
            return storage.extract(key, amount, simulate, fuzzy);
        }

        String ammoTypeName = beyond$matchAmmoType(regId.getPath());
        if (ammoTypeName == null) {
            return storage.extract(key, amount, simulate, fuzzy);
        }

        var ammoMap = acc.getSuperbAmmo();
        long available = ammoMap.getOrDefault(ammoTypeName, 0L);
        if (available <= 0) {
            return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        }

        long toExtract = Math.min(available, amount);
        if (simulate) {
            return new KeyAmount(
                    new ItemStackKey(new ItemStack(itemKey.getSource(), (int) Math.min(toExtract, 9999))),
                    toExtract);
        }

        long remaining = available - toExtract;
        if (remaining <= 0) {
            ammoMap.remove(ammoTypeName);
        } else {
            ammoMap.put(ammoTypeName, remaining);
        }
        net.setDirty();

        ItemStack resultStack = new ItemStack(itemKey.getSource(), (int) Math.min(toExtract, 9999));
        return new KeyAmount(new ItemStackKey(resultStack), toExtract);
    }

    @Unique
    private static String beyond$matchAmmoType(String path) {
        if ("handgun_ammo".equals(path) || "handgun_ammo_box".equals(path)) return "HandgunAmmo";
        if ("rifle_ammo".equals(path) || "rifle_ammo_box".equals(path)) return "RifleAmmo";
        if ("shotgun_ammo".equals(path) || "shotgun_ammo_box".equals(path)) return "ShotgunAmmo";
        if ("sniper_ammo".equals(path) || "sniper_ammo_box".equals(path)) return "SniperAmmo";
        if ("heavy_ammo".equals(path)) return "HeavyAmmo";
        return null;
    }
}
