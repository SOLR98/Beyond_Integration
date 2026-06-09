package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.VehicleNetStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity.class, remap = false)
public class VehicleNetMixin {

    private static final String BCE_NET_ID_KEY = "Net_id";

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void onReadNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(BCE_NET_ID_KEY)) {
            Entity self = (Entity) (Object) this;
            VehicleNetStorage.bindVehicle(self.getUUID(), tag.getInt(BCE_NET_ID_KEY));
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void onWriteNbt(CompoundTag tag, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        int netId = VehicleNetStorage.getBoundNetId(self.getUUID());
        if (netId >= 0) tag.putInt(BCE_NET_ID_KEY, netId);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        VehicleNetStorage.unbindVehicle(self.getUUID());
    }
}
