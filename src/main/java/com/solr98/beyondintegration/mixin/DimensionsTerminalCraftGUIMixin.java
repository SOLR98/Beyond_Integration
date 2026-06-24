package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry;
import com.wintercogs.beyonddimensions.client.gui.DimensionsTerminalCraftGUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DimensionsTerminalCraftGUI.class, remap = false)
public class DimensionsTerminalCraftGUIMixin {

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        BDGUIExtensionRegistry.ensureRegistered();
        var self = (DimensionsTerminalCraftGUI) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onInit(self);
    }
}
