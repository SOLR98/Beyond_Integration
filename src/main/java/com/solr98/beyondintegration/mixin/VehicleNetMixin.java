package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity", remap = false)
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

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true, remap = false)
    private void onInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return;
        cir.setReturnValue(InteractionResult.SUCCESS);
        if (!player.level().isClientSide) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null) VehicleNetStorage.bindVehicle(((Entity) (Object) this).getUUID(), netId);
        }
    }
}
