package com.solr98.beyondintegration.client.widget;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import com.solr98.beyondintegration.feature.xp.XpRepairTier;
import com.solr98.beyondintegration.feature.xp.XpRepairTiers;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;

/**
 * 经验棒「修复档位」按钮（BD 右侧 tab 样式，置于界面右下角）：
 * 不绘制图标，直接显示当前档位每次修复量文本（按服务端配置的修复模式：
 * 百分比模式 {@code 5%}，固定值模式显示固定耐久数值）。
 * <p>档位来自服务端 {@code xp_repair_tiers} 列表，数量可变，界面按索引循环。
 */
public class XpRepairTierButton extends RightTabButton {

    private final ItemStack menuStack;

    public XpRepairTierButton(int x, int y, ItemStack menuStack, Button.OnPress onPress) {
        super(x, y, 23, 26, x, y, 23, 26, onPress);
        this.menuStack = menuStack;
    }

    /** 父类构造期调用：此时字段未赋值，状态统一在绘制时读取。 */
    @Override
    protected void initButton() {
    }

    @Override
    protected void drawIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        String text = repairText(currentTier());
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        int x = getX() + (getWidth() - font.width(text)) / 2;
        int y = getY() + (getHeight() - font.lineHeight) / 2 + 1;
        guiGraphics.drawString(font, text, x, y, 0xFF808080, false);
    }

    /** 悬浮框按 BD 方式走 widget tooltip（由 BDBaseGUIRepairClickMixin 统一补渲染）。 */
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        XpRepairTier tier = currentTier();
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                net.minecraft.network.chat.Component.translatable(
                        "tooltip.beyond_integration.xp_repair.tier",
                        tier.name(), repairText(tier), tier.interval())));
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
    }

    private XpRepairTier currentTier() {
        return XpRepairTiers.byIndex(XpRepairSettings.getTierIndex(menuStack));
    }

    /** 按服务端配置的修复模式给出该档位每次修复量文本。 */
    private static String repairText(XpRepairTier tier) {
        if (CommandConfig.xpRepairMode() == CommandConfig.XpRepairAmountMode.ABSOLUTE) {
            return Integer.toString(CommandConfig.xpAutoRepairAbsolute());
        }
        return tier.percent() + "%";
    }
}
