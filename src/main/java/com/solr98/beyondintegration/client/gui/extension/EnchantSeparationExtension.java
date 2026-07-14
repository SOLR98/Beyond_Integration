package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.ToggleEnchantSeparationPacket;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class EnchantSeparationExtension implements IDimensionsNetGUIExtension {

    private int btnX, btnY;

    @Override
    public int priority() { return 0; }

    @Override
    public void onInit(DimensionsNetGUI<?> gui) {
        btnX = gui.getGuiLeft() - 18;
        btnY = BDGUIExtensionRegistry.getSlotY(gui.getGuiTop(), 0);
    }

    @Override
    public void onRender(DimensionsNetGUI<?> gui, GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        boolean on = SuperbAmmoCache.getEnchantSeparation();
        boolean hover = mx >= btnX && mx < btnX + 16 && my >= btnY && my < btnY + 16;
        ResourceLocation tex = ResourceLocation.tryParse(hover
                ? "beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png"
                : "beyonddimensions:textures/gui/sprites/widget/slot_button.png");
        g.blit(tex, btnX, btnY, 0, 0, 16, 16, 16, 16);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(btnX + 1, btnY + 1, 1);
        pose.scale(0.85f, 0.85f, 1);
        g.renderFakeItem(new ItemStack(Items.ENCHANTED_BOOK), 0, 0);
        pose.popPose();
        if (!on) {
            g.fill(btnX + 1, btnY + 1, btnX + 15, btnY + 15, 0x40FFFFFF);
            g.renderFakeItem(new ItemStack(Items.BARRIER), btnX + 1, btnY + 1);
        }
        if (hover) {
            g.renderTooltip(font, List.of(
                    Component.translatable("gui.beyond_integration.enchant_sep",
                            Component.translatable(on
                                    ? "gui.beyond_integration.enchant_sep.on"
                                    : "gui.beyond_integration.enchant_sep.off"))),
                    ItemStack.EMPTY.getTooltipImage(), ItemStack.EMPTY, mx, my);
        }
    }

    @Override
    public boolean onMouseClicked(DimensionsNetGUI<?> gui, double mx, double my, int button) {
        if (mx >= btnX && mx < btnX + 16 && my >= btnY && my < btnY + 16) {
            boolean next = !SuperbAmmoCache.getEnchantSeparation();
            SuperbAmmoCache.setEnchantSeparation(next);
            PacketHandler.sendToServer(new ToggleEnchantSeparationPacket());
            return true;
        }
        return false;
    }
}
