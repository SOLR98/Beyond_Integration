package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.solr98.beyondintegration.handler.YwzjCreativeAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.level.saveddata.SavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SavedData.class)
public class SavedDataAmmoSyncMixin {
    @Inject(method = "setDirty", at = @At("HEAD"))
    private void onSetDirty(CallbackInfo ci) {
        if (!((Object) this instanceof DimensionsNet net)) return;
        if (net.getId() < 0) return;
        int netId = net.getId();
        NetworkAmmoData data = NetworkAmmoData.get();
        if (net instanceof SuperbAmmoAccessor acc) data.setAmmoForNet(netId, acc.getSuperbAmmo());
        if (net instanceof EnchantSeparationAccessor ea) data.setEnchantSeparation(netId, ea.beyond$isEnchantSeparationEnabled());
        if (net instanceof TaczCreativeAccessor tca) data.setCreativeTypesForNet(netId, tca.getTaczCreativeTypes());
        if (net instanceof YwzjCreativeAccessor yca) data.setYwzjCreativeAmmo(netId, yca.beyond$isYwzjCreativeAmmo());
    }
}
