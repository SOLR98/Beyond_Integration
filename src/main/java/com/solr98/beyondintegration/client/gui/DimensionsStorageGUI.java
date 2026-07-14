package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.core.util.WSStateHelper;
import com.solr98.beyondintegration.feature.crafting.DimensionsStorageMenu;
import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public class DimensionsStorageGUI<T extends DimensionsStorageMenu> extends DimensionsNetGUI<T> {
    protected static final ResourceLocation
        TEX_TOP  = ResourceLocation.tryParse("beyonddimensions:textures/gui/top_base.png"),
        TEX_TSL  = ResourceLocation.tryParse("beyonddimensions:textures/gui/top_slots.png"),
        TEX_MSL  = ResourceLocation.tryParse("beyonddimensions:textures/gui/mid_slots.png"),
        TEX_BSL  = ResourceLocation.tryParse("beyonddimensions:textures/gui/bottom_slots.png"),
        TEX_PINV = ResourceLocation.tryParse("beyonddimensions:textures/gui/player_inv.png"),
        TEX_BTN  = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png"),
        TEX_BH   = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");

    public DimensionsStorageGUI(T c, Inventory p, Component t) { super(c,p,t); }
    protected int mouseX, mouseY;

    protected int getGapY() { return this.topPos + 24 + 18 + (this.menu.getLines() - 2) * 18 + 26; }
    protected int getPanelHeight() { return 62; }
    protected void renderWorkstationPanel(GuiGraphics g) {}

    private boolean hasModeButtons() {
        return this instanceof DimensionsAnvilGUI || this instanceof DimensionsCutGUI ||
               this instanceof DimensionsGrindGUI || this instanceof DimensionsSmithGUI ||
               this instanceof DimensionsCraftGUI;
    }

    @Override protected void init() {
        super.init();
        if(WSStateHelper.pendingRestore){
            this.menu.lineData = Math.min(WSStateHelper.lineData, this.menu.maxLineData);
            long win = Minecraft.getInstance().getWindow().getWindow();
            GLFW.glfwSetCursorPos(win, WSStateHelper.mouseX, WSStateHelper.mouseY);
            WSStateHelper.pendingRestore = false;
        }
    }

    @Override protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        mouseX = mx; mouseY = my;
        int dy = this.topPos;
        g.blit(TEX_TOP, this.leftPos, dy, 0,0,194,24,194,24); dy += 24;
        g.blit(TEX_TSL, this.leftPos, dy, 0,0,194,18,194,18); dy += 18;
        for (int i = 0; i < this.menu.getLines() - 2; i++) { g.blit(TEX_MSL, this.leftPos, dy, 0,0,194,18,194,18); dy += 18; }
        g.blit(TEX_BSL, this.leftPos, dy, 0,0,194,26,194,26); dy += 26;
        renderWorkstationPanel(g);
        renderModeButtons(g, mx, my);
        dy += getPanelHeight();
        g.blit(TEX_PINV, this.leftPos, dy, 0,0,176,89,176,89);
    }

    private void renderModeButtons(GuiGraphics g, int mx, int my) {
        if(!hasModeButtons()) return;
        int gy = getGapY();
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < WorkstationModeConstants.MODES.length; i++) {
            int bx=this.leftPos+WorkstationModeConstants.MX[i], by=gy+WorkstationModeConstants.MY[i];
            boolean on=this.menu.getClass()==getMenuClass(WorkstationModeConstants.MODES[i]), h=mx>=bx&&mx<bx+16&&my>=by&&my<by+16;
            g.blit(h||on?TEX_BH:TEX_BTN, bx,by, 0,0,16,16,16,16);
            var p=g.pose(); p.pushPose(); p.translate(bx+1,by+1,1); p.scale(0.85f,0.85f,1);
            g.renderFakeItem(WorkstationModeConstants.ICONS[i],0,0); p.popPose();
            if (h) {
                g.renderTooltip(font, Component.translatable("gui.beyond_integration.mode." + WorkstationModeConstants.MODES[i].name().toLowerCase()), mx, my);
            }
        }
    }

    private Class<?> getMenuClass(OpenStorageMenuPacket.Type t) {
        return switch(t){
            case ANVIL -> com.solr98.beyondintegration.feature.crafting.DimensionsAnvilMenu.class;
            case CUT -> com.solr98.beyondintegration.feature.crafting.DimensionsCutMenu.class;
            case GRIND -> com.solr98.beyondintegration.feature.crafting.DimensionsGrindMenu.class;
            case SMITH -> com.solr98.beyondintegration.feature.crafting.DimensionsSmithMenu.class;
            case CRAFT -> com.solr98.beyondintegration.feature.crafting.DimensionsCraftMenu.class;
            default -> com.solr98.beyondintegration.feature.crafting.DimensionsStorageMenu.class;
        };
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if(hasModeButtons()){
            int gy=getGapY();
            for(int i=0;i<WorkstationModeConstants.MODES.length;i++){
                int bx=this.leftPos+WorkstationModeConstants.MX[i], by=gy+WorkstationModeConstants.MY[i];
                if(mx>=bx&&mx<bx+16&&my>=by&&my<by+16){
                    if(!WSStateHelper.pendingRestore){
                        WSStateHelper.lineData=this.menu.lineData;
                        WSStateHelper.pendingRestore=true;
                    }
                    PacketHandler.sendToServer(new OpenStorageMenuPacket(WorkstationModeConstants.MODES[i]));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override protected int rebuildImageHeight() { int ph = this.menu.getPanelHeight(); return 24+18+(this.menu.getLines()-2)*18+26+ph+89; }
    @Override protected void rebuildLabelHeight() { int ph = this.menu.getPanelHeight(); this.titleLabelY = 8; this.inventoryLabelY = 24 + this.menu.getLines() * 18 + 5 + ph; }
    @Override protected int calMaxLines() { int ph = this.menu.getPanelHeight(); return (int)((this.height - 36 - (24+18+26+ph+89)) / 18 + 2); }
    @Override public void onClose() { WSStateHelper.clear(); super.onClose(); }
}
