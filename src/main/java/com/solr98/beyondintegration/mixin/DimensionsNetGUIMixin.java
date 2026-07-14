package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry;
import com.solr98.beyondintegration.core.util.WSStateHelper;
import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DimensionsNetGUI.class, remap = false)
public class DimensionsNetGUIMixin {

    private static final ResourceLocation BTN = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    private static final ResourceLocation BH = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");

    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void onInit(CallbackInfo ci) {
        BDGUIExtensionRegistry.ensureRegistered();
        var self = (DimensionsNetGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onInit(self);
    }

    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) ext.onRender(self, graphics, mouseX, mouseY, partialTick);

        var menu = (DimensionsNetMenu) self.getMenu();
        int lx = self.getGuiLeft();
        int gy = self.getGuiTop() + 24 + 18 + (menu.getLines() - 2) * 18 + 26;
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < WorkstationModeConstants.MODES.length; i++) {
            int bx = lx + WorkstationModeConstants.MX[i], by = gy + WorkstationModeConstants.MY[i];
            boolean h = mouseX >= bx && mouseX < bx + 16 && mouseY >= by && mouseY < by + 16;
            graphics.blit(h ? BH : BTN, bx, by, 0, 0, 16, 16, 16, 16);
            var p = graphics.pose(); p.pushPose(); p.translate(bx + 1, by + 1, 1); p.scale(0.85f, 0.85f, 1);
            graphics.renderFakeItem(WorkstationModeConstants.ICONS[i], 0, 0); p.popPose();
            if (h) {
                graphics.renderTooltip(font, Component.translatable("gui.beyond_integration.mode." + WorkstationModeConstants.MODES[i].name().toLowerCase()), mouseX, mouseY);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        for (var ext : BDGUIExtensionRegistry.getExtensions()) {
            if (ext.onMouseClicked(self, mouseX, mouseY, button)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                cir.setReturnValue(true);
                return;
            }
        }

        var menu = (DimensionsNetMenu) self.getMenu();
        int lx = self.getGuiLeft();
        int gy = self.getGuiTop() + 24 + 18 + (menu.getLines() - 2) * 18 + 26;
        for (int i = 0; i < WorkstationModeConstants.MODES.length; i++) {
            int bx = lx + WorkstationModeConstants.MX[i], by = gy + WorkstationModeConstants.MY[i];
            if (mouseX >= bx && mouseX < bx + 16 && mouseY >= by && mouseY < by + 16) {
                if (!WSStateHelper.pendingRestore) {
                    long win = Minecraft.getInstance().getWindow().getWindow();
                    double[] wx = new double[1], wy = new double[1];
                    GLFW.glfwGetCursorPos(win, wx, wy);
                    WSStateHelper.mouseX = wx[0]; WSStateHelper.mouseY = wy[0];
                    WSStateHelper.lineData = menu.lineData;
                    WSStateHelper.pendingRestore = true;
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                PacketHandler.sendToServer(new OpenStorageMenuPacket(WorkstationModeConstants.MODES[i]));
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
