package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.BufferTracker;
import com.solr98.beyondintegration.feature.bind.NetworkBindingRegistry;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.block.entity.NetInterfaceBlockEntity", remap = false)
public class NetInterfaceBufferMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void beyond$onInit(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, CallbackInfo ci) {
        NetInterfaceBlockEntity be = (NetInterfaceBlockEntity) (Object) this;
        BufferTracker.register(be.getStackHandler(), -1, pos);
    }

    @Inject(method = "getNet", at = @At("RETURN"))
    private void beyond$onGetNet(CallbackInfoReturnable<com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet> cir) {
        com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet net = cir.getReturnValue();
        if (net == null) return;
        NetInterfaceBlockEntity be = (NetInterfaceBlockEntity) (Object) this;
        BufferTracker.register(be.getStackHandler(), net.getId(), be.getBlockPos());
    }
}
