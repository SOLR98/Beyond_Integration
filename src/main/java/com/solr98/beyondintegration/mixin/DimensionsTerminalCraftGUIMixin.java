package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry;
import com.wintercogs.beyonddimensions.client.gui.DimensionsTerminalCraftGUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 BeyondDimensions 的 {@link DimensionsTerminalCraftGUI}（终端合成 GUI），
 * 在初始化完成后向该界面应用本模组注册的扩展钩子（BDGUIExtensionRegistry）。
 */
@Mixin(value = DimensionsTerminalCraftGUI.class, remap = false)
public class DimensionsTerminalCraftGUIMixin {

    /** 界面初始化完成后，触发所有扩展的初始化逻辑 */
    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void onInit(CallbackInfo ci) {
        BDGUIExtensionRegistry.ensureRegistered();
        var self = (DimensionsTerminalCraftGUI) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onInit(self);
    }
}
