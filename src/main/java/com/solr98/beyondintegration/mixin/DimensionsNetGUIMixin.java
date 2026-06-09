package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.cache.SuperbAmmoCache;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestEnchantSeparationPacket;
import com.solr98.beyondintegration.network.RequestSuperbAmmoExtractPacket;
import com.solr98.beyondintegration.network.RequestSuperbAmmoStatusPacket;
import com.solr98.beyondintegration.network.ToggleEnchantSeparationPacket;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = DimensionsNetGUI.class, remap = false)
public class DimensionsNetGUIMixin {

    @Unique
    private static final String[] AMMO_ITEMS = {
        "superbwarfare:handgun_ammo", "superbwarfare:rifle_ammo", "superbwarfare:shotgun_ammo",
        "superbwarfare:sniper_ammo", "superbwarfare:heavy_ammo"
    };
    @Unique
    private static final String[] AMMO_NAMES = {
        "HandgunAmmo", "RifleAmmo", "ShotgunAmmo", "SniperAmmo", "HeavyAmmo"
    };
    @Unique
    private static final int AMOUNT_COUNT = 5;

    @Unique
    private static final int SLOT_SIZE = 18;
    @Unique
    private static final int SLOT_GAP = 2;
    @Unique
    private int beyond$hoveredSlot = -1;
    @Unique
    private boolean beyond$hoveredEnchant = false;

    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void onInit(CallbackInfo ci) {
        PacketHandler.sendToServer(new RequestEnchantSeparationPacket());
        if (ModList.get().isLoaded("superbwarfare")) {
            PacketHandler.sendToServer(new RequestSuperbAmmoStatusPacket());
        }
    }

    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        Font font = Minecraft.getInstance().font;

        // ── 附魔分离按钮（始终渲染） ──
        boolean enchantOn = SuperbAmmoCache.getEnchantSeparation();
        beyond$hoveredEnchant = false;
        int bx = self.getGuiLeft() - 18;
        int by = self.getGuiTop() + 6 + 18 * 9;
        beyond$hoveredEnchant = mouseX >= bx && mouseX < bx + 16 && mouseY >= by && mouseY < by + 16;
        ResourceLocation tex = beyond$hoveredEnchant
                ? ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png")
                : ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
        graphics.blit(tex, bx, by, 0, 0, 16, 16, 16, 16);
        var pp = graphics.pose();
        pp.pushPose();
        pp.translate(bx + 1, by + 1, 1);
        pp.scale(0.85f, 0.85f, 1);
        graphics.renderFakeItem(new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK), 0, 0);
        pp.popPose();
        if (beyond$hoveredEnchant) {
            graphics.renderTooltip(font, java.util.List.of(
                    Component.translatable("gui.beyond_integration.enchant_sep",
                            Component.translatable(enchantOn
                                    ? "gui.beyond_integration.enchant_sep.on"
                                    : "gui.beyond_integration.enchant_sep.off"))),
                    ItemStack.EMPTY.getTooltipImage(), ItemStack.EMPTY, mouseX, mouseY);
        }

        // ── SBW 弹药面板（仅 SBW 加载时） ──
        if (!ModList.get().isLoaded("superbwarfare")) return;
        if (!SuperbAmmoCache.hasData()) return;
        if (SuperbAmmoCache.getNetId() < 0) return;

        int panelX = self.getGuiLeft() + self.getXSize() + 4;
        int panelY = self.getGuiTop() + 8;
        boolean infinite = SuperbAmmoCache.getCount("__infinite__") > 0;
        beyond$hoveredSlot = -1;

        for (int i = 0; i < AMOUNT_COUNT; i++) {
            String ammoName = AMMO_NAMES[i];
            int sx = panelX;
            int sy = panelY + i * (SLOT_SIZE + SLOT_GAP);

            boolean hover = mouseX >= sx && mouseX < sx + SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + SLOT_SIZE;
            if (hover) beyond$hoveredSlot = i;

            graphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF8B8B8B);
            graphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xFF373737);
            if (hover) {
                graphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x80FFFFFF);
            }

            Item ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[i]));
            if (ammoItem != null) {
                graphics.renderFakeItem(new ItemStack(ammoItem), sx + 1, sy + 1);
            }

            long count = SuperbAmmoCache.getCount(ammoName);
            String overlay;
            int overlayColor;
            if (infinite) {
                overlay = "\u221E";
                overlayColor = 0xFFAA00;
            } else if (count == 0) {
                overlay = "0";
                overlayColor = 0x555555;
            } else {
                overlay = count >= 1000 ? compactFormat(count) : String.valueOf(count);
                overlayColor = 0xFFFFFF;
            }
            float scale = 0.666f;
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(0, 0, 300);
            pose.scale(scale, scale, scale);
            int textX = (int)((sx + 19 - font.width(overlay) * scale) / scale);
            int textY = (int)((sy + 12) / scale);
            graphics.drawString(font, overlay, textX, textY, overlayColor);
            pose.popPose();
        }

        if (beyond$hoveredSlot >= 0) {
            String ammoName = AMMO_NAMES[beyond$hoveredSlot];
            long count = SuperbAmmoCache.getCount(ammoName);
            Item ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[beyond$hoveredSlot]));
            List<Component> tooltip = new ArrayList<>();
            if (ammoItem != null) {
                tooltip.add(Component.translatable(ammoItem.getDescriptionId()));
            } else {
                tooltip.add(Component.literal(ammoName));
            }
            if (infinite) {
                tooltip.add(Component.literal("\u221E").withStyle(ChatFormatting.GOLD));
            } else {
                String fullCount = java.text.NumberFormat.getIntegerInstance().format(count);
                tooltip.add(Component.literal(fullCount).withStyle(ChatFormatting.WHITE));
            }
            if (ammoItem != null) {
                graphics.renderTooltip(font, tooltip, new ItemStack(ammoItem).getTooltipImage(), new ItemStack(ammoItem), mouseX, mouseY);
            }
        }

    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // ── 附魔分离按钮（直接计算坐标，不依赖 render） ──
        var self = (DimensionsNetGUI<?>) (Object) this;
        int bx = self.getGuiLeft() - 18;
        int by = self.getGuiTop() + 6 + 18 * 9;
        if (mouseX >= bx && mouseX < bx + 16 && mouseY >= by && mouseY < by + 16) {
            cir.setReturnValue(true);
            boolean next = !SuperbAmmoCache.getEnchantSeparation();
            SuperbAmmoCache.setEnchantSeparation(next);
            PacketHandler.sendToServer(new ToggleEnchantSeparationPacket());
            return;
        }

        // ── SBW 弹药面板 ──
        if (!ModList.get().isLoaded("superbwarfare")) return;
        if (!SuperbAmmoCache.hasData()) return;
        if (SuperbAmmoCache.getNetId() < 0) return;

        int slot = getHoveredSlot((int) mouseX, (int) mouseY);
        if (slot < 0) return;

        String ammoName = AMMO_NAMES[slot];
        long count = SuperbAmmoCache.getCount(ammoName);
        if (count <= 0) return;

        cir.setReturnValue(true);

        long toExtract = 64;
        if (beyond$hasShiftDown()) toExtract = 256;

        try {
            PacketHandler.sendToServer(new RequestSuperbAmmoExtractPacket(ammoName, Math.min(toExtract, count)));
        } catch (Exception ignored) {}
    }

    @Unique
    private boolean beyond$hasShiftDown() {
        try {
            var self = (DimensionsNetGUI<?>) (Object) this;
            var menu = self.getMenu();
            if (menu instanceof DimensionsNetMenu) {
                return ((DimensionsNetMenu) menu).hasShiftDown;
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Unique
    private int getHoveredSlot(int mouseX, int mouseY) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        int panelX = self.getGuiLeft() + self.getXSize() + 4;
        int panelY = self.getGuiTop() + 8;

        for (int i = 0; i < AMOUNT_COUNT; i++) {
            int sx = panelX;
            int sy = panelY + i * (SLOT_SIZE + SLOT_GAP);

            if (mouseX >= sx && mouseX < sx + SLOT_SIZE
                && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    @Unique
    private static String compactFormat(long value) {
        if (value >= 1_000_000_000L) return (value / 100_000_000L) / 10.0 + "B";
        if (value >= 1_000_000L)     return (value / 100_000L) / 10.0 + "M";
        if (value >= 1_000L)         return (value / 100L) / 10.0 + "K";
        return String.valueOf(value);
    }
}
