package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.feature.crafting.DimensionsCutMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.List;

public class DimensionsCutGUI extends DimensionsStorageGUI<DimensionsCutMenu> {
    private static final ResourceLocation BG=ResourceLocation.tryParse("beyond_integration:textures/gui/stonecutter.png");
    private static final ResourceLocation BTN=ResourceLocation.tryParse("beyond_integration:textures/gui/stonecutter_button.png");
    private static final ResourceLocation TH=ResourceLocation.tryParse("beyond_integration:textures/gui/scroll_thumb.png");

    private static final int GRID_X = 52, GRID_Y = 3, GRID_COLS = 4, GRID_ROWS = 3, CELL_W = 16, CELL_H = 18;
    private static final int SCROLL_X = 119, THUMB_W = 12, THUMB_H = 15;
    private static final int GRID_H = GRID_ROWS * CELL_H;

    private float scrollOffs;
    private boolean scrolling;

    public DimensionsCutGUI(DimensionsCutMenu c, Inventory p, Component t) { super(c,p,t); }

    private int getMaxScrollRows() {
        int numRecipes = this.menu.getNumRecipes();
        return Math.max(0, (numRecipes + GRID_COLS - 1) / GRID_COLS - GRID_ROWS);
    }

    private int getStartIdx() {
        return (int)(scrollOffs * getMaxScrollRows()) * GRID_COLS;
    }

    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy=getGapY();g.blit(BG,this.leftPos,gy,0,0,176,62,176,62);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.cut"), this.leftPos + 6, gy - 7, 0x404040, false);
        List<StonecutterRecipe> recipes = this.menu.getRecipes();
        int numRecipes = recipes.size();
        int sel = this.menu.getSelectedRecipeIndex();
        if (sel < 0 || sel >= numRecipes) sel = -1;
        int startIdx = getStartIdx();
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int mx = (int)(Minecraft.getInstance().mouseHandler.xpos() / guiScale);
        int my = (int)(Minecraft.getInstance().mouseHandler.ypos() / guiScale);
        for(int r=0;r<GRID_ROWS;r++)for(int c=0;c<GRID_COLS;c++){
            int idx = startIdx + r * GRID_COLS + c;
            int bx = this.leftPos + GRID_X + c * CELL_W;
            int by = gy + GRID_Y + r * CELL_H;
            boolean isSelected = idx == sel;
            boolean isHovered = mx >= bx && mx < bx + CELL_W && my >= by && my < by + CELL_H;
            int srcY = isSelected ? 36 : isHovered ? 18 : 0;
            g.blit(BTN, bx, by, 0, srcY, 16, 18, 16, 54);
            if (idx < numRecipes) {
                ItemStack output = recipes.get(idx).getResultItem(Minecraft.getInstance().level.registryAccess());
                g.renderFakeItem(output, bx, by + 1);
                if (isHovered) {
                    g.renderTooltip(Minecraft.getInstance().font, output, mx, my);
                }
            }
        }
        if (numRecipes > GRID_ROWS * GRID_COLS) {
            int thumbY = gy + GRID_Y + (int)(scrollOffs * (GRID_H - THUMB_H));
            g.blit(TH, this.leftPos + SCROLL_X, thumbY, 0, 0, THUMB_W, THUMB_H, 24, 15);
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        int gy=getGapY();
        if (this.menu.getNumRecipes() > GRID_ROWS * GRID_COLS) {
            int sx = this.leftPos + SCROLL_X;
            int sy = gy + GRID_Y;
            if (mx >= sx && mx < sx + THUMB_W && my >= sy && my < sy + GRID_H) {
                scrolling = true;
                return true;
            }
        }
        int rx=this.leftPos+GRID_X, ry=gy+GRID_Y;
        if(mx>=rx&&mx<rx+GRID_COLS*CELL_W&&my>=ry&&my<ry+GRID_ROWS*CELL_H){
            int col=(int)((mx-rx)/CELL_W), row=(int)((my-ry)/CELL_H);
            int idx = getStartIdx() + row * GRID_COLS + col;
            List<StonecutterRecipe> recipes = this.menu.getRecipes();
            if (idx < recipes.size() && Minecraft.getInstance().gameMode!=null)
                Minecraft.getInstance().gameMode.handleInventoryButtonClick(this.menu.containerId, idx);
            return true;
        }
        return super.mouseClicked(mx,my,btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrolling && btn == 0) {
            int gy = getGapY();
            if (getMaxScrollRows() > 0) {
                float relY = (float)(my - (gy + GRID_Y + THUMB_H / 2.0F));
                scrollOffs = Mth.clamp(relY / (GRID_H - THUMB_H), 0.0F, 1.0F);
                return true;
            }
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) scrolling = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (this.menu.getNumRecipes() > GRID_ROWS * GRID_COLS) {
            int maxRows = getMaxScrollRows();
            if (maxRows > 0) {
                scrollOffs = Mth.clamp(scrollOffs - (float)(delta / maxRows), 0.0F, 1.0F);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }
}
