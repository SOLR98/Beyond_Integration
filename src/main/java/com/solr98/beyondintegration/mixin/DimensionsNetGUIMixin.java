package com.solr98.beyondintegration.mixin;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DimensionsNetGUI.class, remap = false)
public class DimensionsNetGUIMixin {
    @Unique private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        BDGUIExtensionRegistry.ensureRegistered();
        LOGGER.info("[BD-Integration] DimensionsNetGUI init — {} extensions",
                BDGUIExtensionRegistry.getExtensions().size());
        var self = (DimensionsNetGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onInit(self);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onRender(self, g, mx, my, pt);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) {
            if (ext.onMouseClicked(self, mx, my, button)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
