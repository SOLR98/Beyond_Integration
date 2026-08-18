package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry;
import com.wintercogs.beyonddimensions.client.gui.DimensionsCraftGUI;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 BeyondDimensions 的 {@link DimensionsCraftGUI}（合成终端界面），
 * 通过 BDGUIExtensionRegistry 向该界面注册本模组的扩展（初始化、渲染钩子），
 * 实现工作站附加功能（如弹药面板、网络信息等）。
 */
@Mixin(value = DimensionsCraftGUI.class, remap = false)
public class DimensionsCraftGUIMixin {

    /** 界面初始化完成后，通知所有已注册的扩展执行各自的初始化 */
    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void onInit(CallbackInfo ci) {
        BDGUIExtensionRegistry.ensureRegistered();
        var self = (DimensionsCraftGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onInit(self);
    }

    /** 界面渲染末尾，通知所有扩展追加绘制内容 */
    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void onRender(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        var self = (DimensionsCraftGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onRender(self, g, mx, my, pt);
    }
}
