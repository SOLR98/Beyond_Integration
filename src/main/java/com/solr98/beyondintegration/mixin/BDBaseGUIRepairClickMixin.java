package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import com.wintercogs.beyonddimensions.client.gui.BDBaseGUI;
import com.wintercogs.beyonddimensions.client.gui.XpExchangeGUI;
import com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 经验棒界面修复设置的点击兜底。
 * <p>{@code XpExchangeGUI} 未覆盖 {@code mouseClicked}（该方法声明在 BD {@code BDBaseGUI}），
 * 无法在 GUI Mixin 中注入，因此改在 BD 基础 GUI 上按坐标命中本模组按钮后直接处理：
 * 左侧 5 个修复范围（{@code leftPos-23}，30px 步进）、右下角档位（{@code leftPos+176}，底部对齐）。
 * 其余 BD 界面的点击不受影响。
 */
@Mixin(value = BDBaseGUI.class, remap = false)
public class BDBaseGUIRepairClickMixin {

    private static final int RANGE_SIZE = 5;
    private static final int BUTTON_WIDTH = 23;
    private static final int BUTTON_HEIGHT = 26;
    private static final int RANGE_STEP = 30;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void beyondintegration$repairClicks(double mouseX, double mouseY, int button,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        if (!((Object) this instanceof XpExchangeGUI gui)) return;
        XpExchangeMenu menu = gui.getMenu();
        if (menu == null) return;

        ItemStack stack = menu.menuStack;
        int left = gui.getGuiLeft();
        int top = gui.getGuiTop();

        for (int i = 0; i < RANGE_SIZE; i++) {
            int x = left - BUTTON_WIDTH;
            int y = top + 6 + i * RANGE_STEP;
            if (mouseX < x || mouseY < y || mouseX >= x + BUTTON_WIDTH || mouseY >= y + BUTTON_HEIGHT) continue;
            beyondintegration$toggleRange(menu, stack, i);
            cir.setReturnValue(true);
            return;
        }

        int tierX = left + 176;
        int tierY = top + gui.getYSize() - BUTTON_HEIGHT;
        if (mouseX >= tierX && mouseY >= tierY
                && mouseX < tierX + BUTTON_WIDTH && mouseY < tierY + BUTTON_HEIGHT) {
            int count = com.solr98.beyondintegration.feature.xp.XpRepairTiers.count();
            if (count > 0) {
                int index = XpRepairSettings.getTierIndex(stack);
                XpRepairSettings.setTierIndex(stack, (index + 1) % count);
                menu.writeAndSendQuickData();
            }
            cir.setReturnValue(true);
        }
    }

    /**
     * 悬浮框兜底：BD 的 {@code renderTooltip} 覆盖后只处理槽位物品、未调用 super，
     * 导致 widget 自带 tooltip 不渲染；改在 {@code render} 末尾（每帧一次）按原版逻辑补渲染。
     */
    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void beyondintegration$renderWidgetTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                        float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof XpExchangeGUI gui)) return;
        java.util.List<? extends GuiEventListener> children = gui.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (!(children.get(i) instanceof AbstractWidget widget) || !widget.visible) continue;
            if (!widget.isMouseOver(mouseX, mouseY)) continue;
            net.minecraft.client.gui.components.Tooltip tooltip = widget.getTooltip();
            if (tooltip == null) continue;
            Minecraft minecraft = Minecraft.getInstance();
            guiGraphics.renderTooltip(minecraft.font, tooltip.toCharSequence(minecraft), mouseX, mouseY);
            return;
        }
    }

    private static void beyondintegration$toggleRange(XpExchangeMenu menu, ItemStack stack, int index) {
        switch (index) {
            case 0 -> XpRepairSettings.setArmorEnabled(stack, !XpRepairSettings.isArmorEnabled(stack));
            case 1 -> XpRepairSettings.setInventoryEnabled(stack, !XpRepairSettings.isInventoryEnabled(stack));
            case 2 -> XpRepairSettings.setHandsEnabled(stack, !XpRepairSettings.isHandsEnabled(stack));
            case 3 -> XpRepairSettings.setCuriosEnabled(stack, !XpRepairSettings.isCuriosEnabled(stack));
            case 4 -> XpRepairSettings.setMaidEnabled(stack, !XpRepairSettings.isMaidEnabled(stack));
            default -> {
                return;
            }
        }
        menu.writeAndSendQuickData();
    }
}
