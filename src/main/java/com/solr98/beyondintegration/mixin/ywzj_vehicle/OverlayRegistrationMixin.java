package com.solr98.beyondintegration.mixin.ywzj_vehicle;

import com.solr98.beyondintegration.client.NetworkOverlay;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.ywzj.vehicle.client.ClientSetupHandler;

@Mixin(ClientSetupHandler.class)
public class OverlayRegistrationMixin {

    private static final ResourceLocation BCE_NETWORK_OVERLAY = ResourceLocation.parse("ywzj_vehicle:beyond_network");

    @Inject(method = "onRegisterHud", at = @At("TAIL"), remap = false)
    private static void beyond$registerNetworkOverlay(RegisterGuiLayersEvent event, CallbackInfo ci) {
        event.registerBelow(VanillaGuiLayers.CHAT, BCE_NETWORK_OVERLAY, NetworkOverlay.INSTANCE);
    }
}
