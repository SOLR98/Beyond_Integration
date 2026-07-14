package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.bind.BindData;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity.class, remap = false)
public class VehicleNetMixin {

    private static final String BCE_NET_ID_KEY = "Net_id";
    private static final String BCE_TOKEN_MOST_KEY = "beyond$tokenMost";
    private static final String BCE_TOKEN_LEAST_KEY = "beyond$tokenLeast";
    private static final String BCE_OWNER_MOST_KEY = "beyond$ownerMost";
    private static final String BCE_OWNER_LEAST_KEY = "beyond$ownerLeast";

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void onReadNbt(CompoundTag tag, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (tag.contains(BCE_NET_ID_KEY)) {
            int netId = tag.getInt(BCE_NET_ID_KEY);
            UUID token = null;
            UUID owner = null;
            if (tag.contains(BCE_TOKEN_MOST_KEY) && tag.contains(BCE_TOKEN_LEAST_KEY)) {
                token = new UUID(tag.getLong(BCE_TOKEN_MOST_KEY), tag.getLong(BCE_TOKEN_LEAST_KEY));
            }
            if (tag.contains(BCE_OWNER_MOST_KEY) && tag.contains(BCE_OWNER_LEAST_KEY)) {
                owner = new UUID(tag.getLong(BCE_OWNER_MOST_KEY), tag.getLong(BCE_OWNER_LEAST_KEY));
            }
            if (token != null && owner != null) {
                VehicleNetStorage.bindVehicleWithToken(self.getUUID(), netId, token, owner);
            } else {
                VehicleNetStorage.bindVehicle(self.getUUID(), netId, owner != null ? owner : UUID.randomUUID());
            }
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void onWriteNbt(CompoundTag tag, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        BindData data = VehicleNetStorage.getBindData(self.getUUID());
        if (data != null) {
            tag.putInt(BCE_NET_ID_KEY, data.netId());
            if (data.token() != null) {
                tag.putLong(BCE_TOKEN_MOST_KEY, data.token().getMostSignificantBits());
                tag.putLong(BCE_TOKEN_LEAST_KEY, data.token().getLeastSignificantBits());
            }
            if (data.playerUuid() != null) {
                tag.putLong(BCE_OWNER_MOST_KEY, data.playerUuid().getMostSignificantBits());
                tag.putLong(BCE_OWNER_LEAST_KEY, data.playerUuid().getLeastSignificantBits());
            }
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        VehicleNetStorage.unbindVehicle(self.getUUID());
    }
}
