package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry;
import com.wintercogs.beyonddimensions.client.gui.DimensionsCraftGUI;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DimensionsCraftGUI.class, remap = false)
public class DimensionsCraftGUIMixin {

    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void onInit(CallbackInfo ci) {
        BDGUIExtensionRegistry.ensureRegistered();
        var self = (DimensionsCraftGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onInit(self);
    }

    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void onRender(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        var self = (DimensionsCraftGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onRender(self, g, mx, my, pt);
    }
}
