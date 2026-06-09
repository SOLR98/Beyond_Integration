package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.client.widget.EnchantToggleBtn;
import com.solr98.beyondintegration.network.RequestSuperbAmmoExtractPacket;
import com.solr98.beyondintegration.network.RequestSuperbAmmoStatusPacket;
import com.solr98.beyondintegration.network.ToggleEnchantSeparationPacket;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI", remap = false)
public class DimensionsNetGUIMixin {
    @Unique private static final String[] AMMO_ITEMS = {
        "superbwarfare:handgun_ammo", "superbwarfare:rifle_ammo", "superbwarfare:shotgun_ammo",
        "superbwarfare:sniper_ammo", "superbwarfare:heavy_ammo"
    };
    @Unique private static final String[] AMMO_NAMES = {
        "HandgunAmmo", "RifleAmmo", "ShotgunAmmo", "SniperAmmo", "HeavyAmmo"
    };
    @Unique private static final int SLOT_SIZE = 18;
    @Unique private static final int SLOT_GAP = 2;
    @Unique private int beyond$hoveredSlot = -1;
    @Unique private EnchantToggleBtn beyond$enchantBtn;
    @Unique private boolean beyond$lastEnchantState = true;

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;

        beyond$enchantBtn = new EnchantToggleBtn(
                self.getGuiLeft() - 18, self.getGuiTop() + 6 + 18 * 8, btn -> {
            boolean next = !SuperbAmmoCache.INSTANCE.getEnchantSeparation();
            SuperbAmmoCache.INSTANCE.setEnchantSeparation(next);
            PacketDistributor.sendToServer(new ToggleEnchantSeparationPacket());
        });
        beyond$enchantBtn.updateTooltip();
        beyond$lastEnchantState = SuperbAmmoCache.INSTANCE.getEnchantSeparation();

        if (net.neoforged.fml.ModList.get().isLoaded("superbwarfare"))
            PacketDistributor.sendToServer(new RequestSuperbAmmoStatusPacket(
                    com.solr98.beyondintegration.handler.MenuNetIdHelper.getNetIdFromMenu(Minecraft.getInstance().player)));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        var mc = Minecraft.getInstance();
        var f = mc.font;

        beyond$hoveredSlot = -1;

        // 附魔按钮
        if (beyond$enchantBtn != null) {
            beyond$enchantBtn.renderWidget(g, mx, my, pt);
            boolean cur = SuperbAmmoCache.INSTANCE.getEnchantSeparation();
            if (cur != beyond$lastEnchantState) {
                beyond$lastEnchantState = cur;
                beyond$enchantBtn.updateTooltip();
            }
            if (beyond$enchantBtn.isMouseOver(mx, my)) {
                g.renderTooltip(f, List.of(Component.translatable("gui.beyond_integration.enchant_sep",
                        Component.translatable(cur ? "gui.beyond_integration.enchant_sep.on" : "gui.beyond_integration.enchant_sep.off"))),
                        ItemStack.EMPTY.getTooltipImage(), ItemStack.EMPTY, mx, my);
            }
        }

        // 弹药面板
        if (!net.neoforged.fml.ModList.get().isLoaded("superbwarfare")) return;
        if (!SuperbAmmoCache.INSTANCE.hasData()) return;
        if (SuperbAmmoCache.INSTANCE.getNetId() < 0) return;

        boolean infinite = SuperbAmmoCache.INSTANCE.hasInfinite();
        int panelX = self.getGuiLeft() + self.getXSize() + 4;
        int panelY = self.getGuiTop() + 8;

        for (int i = 0; i < 5; i++) {
            int sx = panelX;
            int sy = panelY + i * (SLOT_SIZE + SLOT_GAP);
            boolean hover = mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
            if (hover) beyond$hoveredSlot = i;

            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF8B8B8B);
            g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xFF373737);
            if (hover) g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x80FFFFFF);

            var ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[i]));
            if (ammoItem != null) g.renderFakeItem(new ItemStack(ammoItem), sx + 1, sy + 1);

            long count = SuperbAmmoCache.INSTANCE.getCount(AMMO_NAMES[i]);
            var overlay = infinite ? "\u221E" : count == 0 ? "0" : compactFormat(count);
            int overlayColor = infinite ? 0xFFAA00 : count == 0 ? 0x555555 : 0xFFFFFF;
            var pose = g.pose();
            pose.pushPose();
            pose.translate(0, 0, 300);
            float scale = 0.666f;
            pose.scale(scale, scale, scale);
            int textX = (int)((sx + 19 - f.width(overlay) * scale) / scale);
            int textY = (int)((sy + 12) / scale);
            g.drawString(f, overlay, textX, textY, overlayColor);
            pose.popPose();
        }

        if (beyond$hoveredSlot >= 0) {
            String ammoName = AMMO_NAMES[beyond$hoveredSlot];
            long count = SuperbAmmoCache.INSTANCE.getCount(ammoName);
            var ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[beyond$hoveredSlot]));
            List<Component> tooltip = new ArrayList<>();
            if (ammoItem != null) tooltip.add(Component.translatable(ammoItem.getDescriptionId()));
            else tooltip.add(Component.literal(ammoName));
            if (infinite) tooltip.add(Component.literal("\u221E").withStyle(ChatFormatting.GOLD));
            else tooltip.add(Component.literal(NumberFormat.getIntegerInstance().format(count)).withStyle(ChatFormatting.WHITE));
            if (ammoItem != null) g.renderTooltip(f, tooltip, new ItemStack(ammoItem).getTooltipImage(), new ItemStack(ammoItem), mx, my);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        var self = (DimensionsNetGUI<?>) (Object) this;

        // 附魔按钮点击
        if (beyond$enchantBtn != null && beyond$enchantBtn.mouseClicked(mx, my, button)) {
            cir.setReturnValue(true);
            return;
        }

        // 弹药面板点击
        if (!net.neoforged.fml.ModList.get().isLoaded("superbwarfare")) return;
        if (!SuperbAmmoCache.INSTANCE.hasData()) return;
        if (SuperbAmmoCache.INSTANCE.getNetId() < 0) return;

        int panelX = self.getGuiLeft() + self.getXSize() + 4;
        int panelY = self.getGuiTop() + 8;
        int hitSlot = -1;
        for (int i = 0; i < 5; i++) {
            int sx = panelX;
            int sy = panelY + i * (SLOT_SIZE + SLOT_GAP);
            if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) { hitSlot = i; break; }
        }
        if (hitSlot < 0) return;

        String ammoName = AMMO_NAMES[hitSlot];
        boolean infinite = SuperbAmmoCache.INSTANCE.hasInfinite();
        long count = infinite ? Long.MAX_VALUE : SuperbAmmoCache.INSTANCE.getCount(ammoName);
        if (count <= 0) return;

        cir.setReturnValue(true);
        long toExtract = 64;
        if (beyond$hasShiftDown()) toExtract = 256;
        PacketDistributor.sendToServer(new RequestSuperbAmmoExtractPacket(ammoName, Math.min(toExtract, count)));
    }

    @Unique
    private boolean beyond$hasShiftDown() {
        try {
            var self = (DimensionsNetGUI<?>) (Object) this;
            var menu = self.getMenu();
            if (menu instanceof DimensionsNetMenu) return ((DimensionsNetMenu) menu).hasShiftDown;
        } catch (Exception ignored) {}
        return false;
    }

    @Unique
    private static String compactFormat(long value) {
        if (value >= 1_000_000_000L) return String.format("%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000L)     return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000L)         return String.format("%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }
}
