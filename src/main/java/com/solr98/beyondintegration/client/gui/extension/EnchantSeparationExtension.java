package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.client.widget.EnchantToggleBtn;
import com.solr98.beyondintegration.network.ToggleEnchantSeparationPacket;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class EnchantSeparationExtension implements IDimensionsNetGUIExtension {

    private EnchantToggleBtn button;
    private boolean lastState = true;

    @Override
    public int priority() { return 0; }

    @Override
    public void onInit(DimensionsNetGUI<?> gui) {
        button = new EnchantToggleBtn(
                gui.getGuiLeft() - 18, gui.getGuiTop() + 6 + 18 * 8, btn -> {
            boolean next = !SuperbAmmoCache.INSTANCE.getEnchantSeparation();
            SuperbAmmoCache.INSTANCE.setEnchantSeparation(next);
            PacketDistributor.sendToServer(new ToggleEnchantSeparationPacket());
        });
        button.updateTooltip();
        lastState = SuperbAmmoCache.INSTANCE.getEnchantSeparation();
    }

    @Override
    public void onRender(DimensionsNetGUI<?> gui, GuiGraphics g, int mx, int my, float pt) {
        if (button == null) return;
        button.renderWidget(g, mx, my, pt);
        boolean cur = SuperbAmmoCache.INSTANCE.getEnchantSeparation();
        if (cur != lastState) {
            lastState = cur;
            button.updateTooltip();
        }
        if (button.isMouseOver(mx, my)) {
            g.renderTooltip(gui.getFont(), List.of(Component.translatable("gui.beyond_integration.enchant_sep",
                    Component.translatable(cur ? "gui.beyond_integration.enchant_sep.on" : "gui.beyond_integration.enchant_sep.off"))),
                    ItemStack.EMPTY.getTooltipImage(), ItemStack.EMPTY, mx, my);
        }
    }

    @Override
    public boolean onMouseClicked(DimensionsNetGUI<?> gui, double mx, double my, int button) {
        return this.button != null && this.button.mouseClicked(mx, my, button);
    }
}
