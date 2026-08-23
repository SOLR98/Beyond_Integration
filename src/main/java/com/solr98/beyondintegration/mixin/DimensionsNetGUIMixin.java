package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.client.gui.extension.BDGUIHelper;
import com.solr98.beyondintegration.client.widget.EnchantToggleBtn;
import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestSuperbAmmoExtractPacket;
import com.solr98.beyondintegration.network.RequestEnchantSeparationPacket;
import com.solr98.beyondintegration.network.RequestSuperbAmmoStatusPacket;
import com.solr98.beyondintegration.network.ToggleEnchantSeparationPacket;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 注入 BeyondDimensions 的 {@link DimensionsNetGUI}（存储网络界面），
 * 扩展该界面：附魔分离开关按钮、工作站模式切换按钮、Superb Warfare 弹药面板
 * （显示/提取网络弹药，支持 Shift 批量），并处理对应点击交互。
 */
@Mixin(value = DimensionsNetGUI.class, remap = false)
public class DimensionsNetGUIMixin {
    /** 模式按钮正常贴图 */
    @Unique private static final ResourceLocation BTN = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    /** 模式按钮悬停贴图 */
    @Unique private static final ResourceLocation BH = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");
    /** 弹药面板显示的 5 种 SW 弹药物品 ID */
    @Unique private static final String[] AMMO_ITEMS = {
        "superbwarfare:handgun_ammo", "superbwarfare:rifle_ammo", "superbwarfare:shotgun_ammo",
        "superbwarfare:sniper_ammo", "superbwarfare:heavy_ammo"
    };
    /** 与 AMMO_ITEMS 对应的网络缓存键名 */
    @Unique private static final String[] AMMO_NAMES = {
        "HandgunAmmo", "RifleAmmo", "ShotgunAmmo", "SniperAmmo", "HeavyAmmo"
    };
    /** 弹药格子大小 */
    @Unique private static final int SLOT_SIZE = 18;
    /** 弹药格子间距 */
    @Unique private static final int SLOT_GAP = 2;
    /** 当前鼠标悬停的弹药格子下标（-1 表示无） */
    @Unique private int beyond$hoveredSlot = -1;
    /** 附魔分离开关按钮 */
    @Unique private EnchantToggleBtn beyond$enchantBtn;
    /** 附魔物品(装备)分离开关按钮（全局配置关闭物品分离时不创建/不显示） */
    /** 上次记录的附魔分离状态，用于检测变化并刷新 tooltip */
    @Unique private boolean beyond$lastEnchantState = true;
    /** 上次记录的附魔物品分离状态，用于检测变化并刷新 tooltip */

    /** 初始化完成后：创建附魔分离按钮、请求服务端弹药状态数据 */
    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void onInit(CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;

        beyond$enchantBtn = new EnchantToggleBtn(
                self.getGuiLeft() - 18, self.getGuiTop() + 6 + 18 * 8, btn -> {
            boolean next = !SuperbAmmoCache.getEnchantSeparation();
            SuperbAmmoCache.setEnchantSeparation(next);
            PacketHandler.sendToServer(new ToggleEnchantSeparationPacket());
        });
        beyond$enchantBtn.updateTooltip();
        beyond$lastEnchantState = SuperbAmmoCache.getEnchantSeparation();

        if (ModList.get().isLoaded("superbwarfare"))
            PacketHandler.sendToServer(new RequestSuperbAmmoStatusPacket());
        // 附魔分离状态请求：独立于 SW，仅 BD+BI 时按钮也能正确回显
        PacketHandler.sendToServer(new RequestEnchantSeparationPacket());
    }

    /** 渲染末尾：绘制附魔按钮、工作站模式按钮和网络弹药面板（含悬停 tooltip） */
    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void onRender(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        var f = Minecraft.getInstance().font;

        beyond$hoveredSlot = -1;

        // 附魔按钮
        if (beyond$enchantBtn != null) {
            beyond$enchantBtn.renderWidget(g, mx, my, pt);
            boolean cur = SuperbAmmoCache.getEnchantSeparation();
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

        // 附魔物品分离按钮
        // 工作站模式按钮（统一由 mixin 绘制：BI 工作站界面/BD 合成终端/纯存储）
        var menu = (DimensionsNetMenu) self.getMenu();
        int lx = self.getGuiLeft();
        int gy = self.getGuiTop() + 24 + 18 + (menu.getLines() - 2) * 18 + 26;
        boolean tooltipShown = false;
        for (int i = 0; i < WorkstationModeConstants.MODES.length; i++) {
            int bx = lx + WorkstationModeConstants.MX[i], by = gy + WorkstationModeConstants.MY[i];
            boolean on = beyond$isCurrentMode(menu.getClass(), WorkstationModeConstants.MODES[i]);
            boolean h = mx >= bx && mx < bx + 16 && my >= by && my < by + 16;
            g.blit(h || on ? BH : BTN, bx, by, 0, 0, 16, 16, 16, 16);
            var p = g.pose(); p.pushPose(); p.translate(bx + 1, by + 1, 1); p.scale(0.85f, 0.85f, 1);
            g.renderFakeItem(WorkstationModeConstants.ICONS[i], 0, 0); p.popPose();
            // 仅渲染第一个命中的 tooltip 避免文本重叠
            if (h && !tooltipShown) {
                tooltipShown = true;
                g.renderTooltip(f, Component.translatable("gui.beyond_integration.mode." + WorkstationModeConstants.MODES[i].name().toLowerCase()), mx, my);
            }
        }

        // 弹药面板
        if (!ModList.get().isLoaded("superbwarfare")) return;
        if (!SuperbAmmoCache.hasData()) return;
        if (SuperbAmmoCache.getNetId() < 0) return;

        boolean infinite = SuperbAmmoCache.getCount("__infinite__") > 0;
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

            long count = SuperbAmmoCache.getCount(AMMO_NAMES[i]);
            var overlay = infinite ? "\u221E" : count == 0 ? "0" : BDGUIHelper.compactFormat(count);
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
            long count = SuperbAmmoCache.getCount(ammoName);
            var ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[beyond$hoveredSlot]));
            List<Component> tooltip = new ArrayList<>();
            if (ammoItem != null) tooltip.add(Component.translatable(ammoItem.getDescriptionId()));
            else tooltip.add(Component.literal(ammoName));
            if (infinite) tooltip.add(Component.literal("\u221E").withStyle(ChatFormatting.GOLD));
            else tooltip.add(Component.literal(NumberFormat.getIntegerInstance().format(count)).withStyle(ChatFormatting.WHITE));
            if (ammoItem != null) g.renderTooltip(f, tooltip, new ItemStack(ammoItem).getTooltipImage(), new ItemStack(ammoItem), mx, my);
        }
    }

    /** 点击处理：附魔按钮 / 模式切换按钮 / 弹药面板提取（Shift 批量 256，普通 64） */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void onMouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        var self = (DimensionsNetGUI<?>) (Object) this;

        // 扩展驱动：优先处理扩展点击（如 Ctrl+右键 附魔分离保护切换）
        for (var ext : com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry.getExtensions()) {
            if (ext.onMouseClicked(self, mx, my, button)) {
                cir.setReturnValue(true);
                return;
            }
        }

        // 附魔按钮点击
        if (beyond$enchantBtn != null && beyond$enchantBtn.mouseClicked(mx, my, button)) {
            cir.setReturnValue(true);
            return;
        }

        // 附魔物品分离按钮点击
        var menu = (DimensionsNetMenu) self.getMenu();
        int lx = self.getGuiLeft();
        int gy = self.getGuiTop() + 24 + 18 + (menu.getLines() - 2) * 18 + 26;
        for (int i = 0; i < WorkstationModeConstants.MODES.length; i++) {
            int bx = lx + WorkstationModeConstants.MX[i], by = gy + WorkstationModeConstants.MY[i];
            if (mx >= bx && mx < bx + 16 && my >= by && my < by + 16) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                com.solr98.beyondintegration.client.gui.WorkstationTransferHelper.save(menu);
                PacketHandler.sendToServer(new OpenStorageMenuPacket(WorkstationModeConstants.MODES[i]));
                cir.setReturnValue(true);
                return;
            }
        }

        // 弹药面板点击
        if (!ModList.get().isLoaded("superbwarfare")) return;
        if (!SuperbAmmoCache.hasData()) return;
        if (SuperbAmmoCache.getNetId() < 0) return;

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
        boolean infinite = SuperbAmmoCache.getCount("__infinite__") > 0;
        long count = infinite ? Long.MAX_VALUE : SuperbAmmoCache.getCount(ammoName);
        if (count <= 0) return;

        cir.setReturnValue(true);
        long toExtract = 64;
        if (beyond$hasShiftDown()) toExtract = 256;
        PacketHandler.sendToServer(new RequestSuperbAmmoExtractPacket(ammoName, Math.min(toExtract, count)));
    }

    /** 读取原菜单的 hasShiftDown 字段，判断是否按住 Shift */
    @Unique
    private boolean beyond$hasShiftDown() {
        try {
            var self = (DimensionsNetGUI<?>) (Object) this;
            var menu = self.getMenu();
            if (menu instanceof DimensionsNetMenu) return ((DimensionsNetMenu) menu).hasShiftDown;
        } catch (Exception ignored) {}
        return false;
    }

    /** 判断当前打开的菜单是否属于指定的工作站模式 */
    @Unique
    private static boolean beyond$isCurrentMode(Class<?> menuClass, OpenStorageMenuPacket.Type mode) {
        return switch (mode) {
            case ANVIL -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsAnvilMenu.class;
            case CUT -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsCutMenu.class;
            case GRIND -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsGrindMenu.class;
            case SMITH -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsSmithMenu.class;
            case CRAFT -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsCraftMenu.class;
            default -> false;
        };
    }
}
