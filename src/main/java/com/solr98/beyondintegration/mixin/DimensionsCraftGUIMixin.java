package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry;
import com.wintercogs.beyonddimensions.client.gui.DimensionsCraftGUI;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DimensionsCraftGUI.class, remap = false)
public class DimensionsCraftGUIMixin {

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        BDGUIExtensionRegistry.ensureRegistered();
        var self = (DimensionsCraftGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onInit(self);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        var self = (DimensionsCraftGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onRender(self, g, mx, my, pt);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        var self = (DimensionsCraftGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) {
            if (ext.onMouseClicked(self, mx, my, button)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
