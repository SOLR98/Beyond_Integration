package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.client.widget.XpRepairRangeButton;
import com.solr98.beyondintegration.client.widget.XpRepairTierButton;
import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import com.wintercogs.beyonddimensions.client.gui.XpExchangeGUI;
import com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 经验棒界面扩展（喂食器同款思路：不替换 BD 界面，仅在原 {@code XpExchangeGUI} 上追加按钮）：
 * <ul>
 *   <li>左侧 5 个修复范围开关；右下角修复档位按钮（按服务端配置的模式显示修复量文本）；</li>
 *   <li>修复模式由服务端配置决定，界面不再提供切换；</li>
 *   <li>{@code renderLabels} 的最大等级行、输入框长度按 BI 配置调整。</li>
 * </ul>
 * 设置通过菜单 QuickData 同步（见 {@code XpExchangeMenuMixin}）。
 */
@Mixin(value = XpExchangeGUI.class, remap = false)
public abstract class XpExchangeRepairGUIMixin {

    @Unique private static java.lang.reflect.Method beyondintegration$addWidgetMethod;

    @Unique private final XpRepairRangeButton[] beyondintegration$rangeButtons = new XpRepairRangeButton[5];
    @Unique private XpRepairTierButton beyondintegration$tierButton;

    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void beyondintegration$addRepairButtons(CallbackInfo ci) {
        XpExchangeGUI self = (XpExchangeGUI) (Object) this;
        XpExchangeMenu menu = self.getMenu();
        ItemStack stack = menu.menuStack;

        int leftX = self.getGuiLeft() - 23;
        int iconX = self.getGuiLeft() - 18;
        int top = self.getGuiTop();

        beyondintegration$rangeButtons[0] = beyondintegration$range(leftX, top + 6, iconX, menu,
                new ItemStack(Items.DIAMOND_CHESTPLATE),
                "hopper_item_mode_allow", "hopper_item_mode_deny",
                "tooltip.beyond_integration.xp_repair.range.armor",
                XpRepairSettings::isArmorEnabled, XpRepairSettings::setArmorEnabled);
        beyondintegration$rangeButtons[1] = beyondintegration$range(leftX, top + 36, iconX, menu,
                new ItemStack(Items.CHEST),
                "slot_button", "slot_button_disabled",
                "tooltip.beyond_integration.xp_repair.range.inventory",
                XpRepairSettings::isInventoryEnabled, XpRepairSettings::setInventoryEnabled);
        beyondintegration$rangeButtons[2] = beyondintegration$range(leftX, top + 66, iconX, menu,
                new ItemStack(Items.DIAMOND_SWORD),
                "hopper_item_mode_allow", "hopper_item_mode_deny",
                "tooltip.beyond_integration.xp_repair.range.hands",
                XpRepairSettings::isHandsEnabled, XpRepairSettings::setHandsEnabled);
        // 饰品 / 女仆饰品：关闭仅叠加屏障，不替换图标
        beyondintegration$rangeButtons[3] = beyondintegration$range(leftX, top + 96, iconX, menu,
                ItemStack.EMPTY,
                "curios_bauble", "curios_bauble",
                "tooltip.beyond_integration.xp_repair.range.curios",
                XpRepairSettings::isCuriosEnabled, XpRepairSettings::setCuriosEnabled);
        beyondintegration$rangeButtons[4] = beyondintegration$range(leftX, top + 126, iconX, menu,
                ItemStack.EMPTY,
                "maid_bauble", "maid_bauble",
                "tooltip.beyond_integration.xp_repair.range.maid",
                XpRepairSettings::isMaidEnabled, XpRepairSettings::setMaidEnabled);

        beyondintegration$tierButton = new XpRepairTierButton(
                self.getGuiLeft() + 176, self.getGuiTop() + self.getYSize() - 26, stack,
                b -> {
                    int count = com.solr98.beyondintegration.feature.xp.XpRepairTiers.count();
                    if (count <= 0) return;
                    int index = XpRepairSettings.getTierIndex(menu.menuStack);
                    XpRepairSettings.setTierIndex(menu.menuStack, (index + 1) % count);
                    menu.writeAndSendQuickData();
                });

        for (XpRepairRangeButton button : beyondintegration$rangeButtons) {
            beyondintegration$addWidget(button);
        }
        beyondintegration$addWidget(beyondintegration$tierButton);

        beyondintegration$refreshRepairButtons(self);
    }

    @Inject(method = "containerTick", at = @At("TAIL"), remap = true)
    private void beyondintegration$syncRepairButtons(CallbackInfo ci) {
        beyondintegration$refreshRepairButtons((XpExchangeGUI) (Object) this);
    }

    @Unique
    private XpRepairRangeButton beyondintegration$range(int x, int y, int iconX, XpExchangeMenu menu,
                                                       ItemStack displayItem, String onIcon, String offIcon,
                                                       String tooltipKey,
                                                       java.util.function.Predicate<ItemStack> getter,
                                                       java.util.function.BiConsumer<ItemStack, Boolean> setter) {
        return new XpRepairRangeButton(x, y, iconX, menu.menuStack, displayItem, onIcon, offIcon, tooltipKey,
                getter, setter, b -> {
            ((XpRepairRangeButton) b).toggleValue();
            menu.writeAndSendQuickData();
        });
    }

    @Unique
    private void beyondintegration$refreshRepairButtons(XpExchangeGUI self) {
        for (XpRepairRangeButton button : beyondintegration$rangeButtons) {
            if (button == null) continue;
            button.refreshState();
            button.active = true;
        }
        if (beyondintegration$tierButton != null) {
            beyondintegration$tierButton.active = true;
        }
    }

    /** 点击兜底由 {@code BDBaseGUIRepairClickMixin} 在 BD 基础 GUI 上按坐标处理。 */

    @Unique
    private void beyondintegration$addWidget(GuiEventListener widget) {
        try {
            if (beyondintegration$addWidgetMethod == null) {
                beyondintegration$addWidgetMethod = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                        net.minecraft.client.gui.screens.Screen.class, "m_142416_", GuiEventListener.class);
            }
            beyondintegration$addWidgetMethod.setAccessible(true);
            beyondintegration$addWidgetMethod.invoke(this, widget);
        } catch (Throwable ignored) {
        }
    }

    /** 最大等级行改用 BI 配置上限（BD 原为固定 21863）。 */
    @ModifyArg(method = "renderLabels", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            ordinal = 2), index = 1, remap = true)
    private Component beyondintegration$maxLevelLabel(Component original) {
        if (!CommandConfig.xpRodTweaksEnabled()) return original;
        return Component.translatable("menu.label.beyonddimensions.xp_exchange.max_level",
                CommandConfig.xpRodMaxTargetLevel());
    }

    /** 输入框长度至少覆盖配置上限的位数。 */
    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/EditBox;setMaxLength(I)V"), index = 0, remap = true)
    private int beyondintegration$fieldMaxLength(int original) {
        if (!CommandConfig.xpRodTweaksEnabled()) return original;
        return Math.max(original, Integer.toString(CommandConfig.xpRodMaxTargetLevel()).length());
    }
}
