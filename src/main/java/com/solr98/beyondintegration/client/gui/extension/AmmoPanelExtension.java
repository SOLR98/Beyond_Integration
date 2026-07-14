package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestSuperbAmmoExtractPacket;
import com.solr98.beyondintegration.network.RequestSuperbAmmoStatusPacket;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class AmmoPanelExtension implements IDimensionsNetGUIExtension {

    private static final String[] AMMO_ITEMS = {
        "superbwarfare:handgun_ammo", "superbwarfare:rifle_ammo", "superbwarfare:shotgun_ammo",
        "superbwarfare:sniper_ammo", "superbwarfare:heavy_ammo"
    };
    private static final String[] AMMO_NAMES = {
        "HandgunAmmo", "RifleAmmo", "ShotgunAmmo", "SniperAmmo", "HeavyAmmo"
    };
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 2;
    private int hoveredSlot = -1;

    @Override
    public int priority() { return 1; }

    @Override
    public void onInit(DimensionsNetGUI<?> gui) {
        if (!ModList.get().isLoaded("superbwarfare")) return;
        var player = Minecraft.getInstance().player;
        if (player != null)
            PacketHandler.sendToServer(new RequestSuperbAmmoStatusPacket());
    }

    @Override
    public void onRender(DimensionsNetGUI<?> gui, GuiGraphics g, int mx, int my, float pt) {
        if (!ModList.get().isLoaded("superbwarfare")) return;
        if (!SuperbAmmoCache.hasData()) return;
        if (SuperbAmmoCache.getNetId() < 0) return;

        boolean infinite = SuperbAmmoCache.getCount("__infinite__") > 0;
        int panelX = gui.getGuiLeft() + gui.getXSize() + 4;
        int panelY = gui.getGuiTop() + 8;
        hoveredSlot = -1;

        for (int i = 0; i < 5; i++) {
            int sx = panelX;
            int sy = panelY + i * (SLOT_SIZE + SLOT_GAP);
            boolean hover = mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
            if (hover) hoveredSlot = i;

            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF8B8B8B);
            g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xFF373737);
            if (hover) g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x80FFFFFF);

            var ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[i]));
            if (ammoItem != null) g.renderFakeItem(new ItemStack(ammoItem), sx + 1, sy + 1);

            long count = SuperbAmmoCache.getCount(AMMO_NAMES[i]);
            var overlay = infinite ? "\u221E" : count == 0 ? "0" : BDGUIHelper.compactFormat(count);
            int overlayColor = infinite ? 0xFFAA00 : count == 0 ? 0x555555 : 0xFFFFFF;
            var pose = g.pose();
            pose.pushPose();
            pose.translate(0, 0, 300);
            float scale = 0.666f;
            pose.scale(scale, scale, scale);
            int textX = (int)((sx + 19 - gui.getFont().width(overlay) * scale) / scale);
            int textY = (int)((sy + 12) / scale);
            g.drawString(gui.getFont(), overlay, textX, textY, overlayColor);
            pose.popPose();
        }

        if (hoveredSlot >= 0) {
            String ammoName = AMMO_NAMES[hoveredSlot];
            long count = SuperbAmmoCache.getCount(ammoName);
            var ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[hoveredSlot]));
            List<Component> tooltip = new ArrayList<>();
            if (ammoItem != null) tooltip.add(Component.translatable(ammoItem.getDescriptionId()));
            else tooltip.add(Component.literal(ammoName));
            if (infinite) tooltip.add(Component.literal("\u221E").withStyle(ChatFormatting.GOLD));
            else tooltip.add(Component.literal(NumberFormat.getIntegerInstance().format(count)).withStyle(ChatFormatting.WHITE));
            if (ammoItem != null) g.renderTooltip(gui.getFont(), tooltip, new ItemStack(ammoItem).getTooltipImage(), new ItemStack(ammoItem), mx, my);
        }
    }

    @Override
    public boolean onMouseClicked(DimensionsNetGUI<?> gui, double mx, double my, int button) {
        if (button != 0) return false;
        if (!ModList.get().isLoaded("superbwarfare")) return false;
        if (!SuperbAmmoCache.hasData()) return false;
        if (SuperbAmmoCache.getNetId() < 0) return false;

        int panelX = gui.getGuiLeft() + gui.getXSize() + 4;
        int panelY = gui.getGuiTop() + 8;
        int hitSlot = -1;
        for (int i = 0; i < 5; i++) {
            int sx = panelX;
            int sy = panelY + i * (SLOT_SIZE + SLOT_GAP);
            if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) { hitSlot = i; break; }
        }
        if (hitSlot < 0) return false;

        String ammoName = AMMO_NAMES[hitSlot];
        long count = SuperbAmmoCache.getCount(ammoName);
        if (count <= 0) return false;

        long toExtract = 64;
        if (BDGUIHelper.isShiftDown(gui)) toExtract = 256;
        PacketHandler.sendToServer(new RequestSuperbAmmoExtractPacket(ammoName, Math.min(toExtract, count)));
        return true;
    }
}
