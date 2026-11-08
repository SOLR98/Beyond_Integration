package com.solr98.beyondintegration.client.widget;

import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.LeftTabButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 经验棒「修复范围」开关按钮（BD 左侧 tab 样式）：
 * 开启显示范围图标/物品，关闭时叠加屏障；点击切换对应 NBT 开关。
 * <p>状态在构造完成后才填充（{@code StatusButton} 会在父类构造中调用 {@code initButton}，
 * 此时子类字段尚未赋值）。
 */
public class XpRepairRangeButton extends LeftTabButton {

    private static final ItemStack BARRIER = new ItemStack(Items.BARRIER);

    private final ItemStack menuStack;
    private final ItemStack displayItem;
    private final Predicate<ItemStack> getter;
    private final BiConsumer<ItemStack, Boolean> setter;

    public XpRepairRangeButton(int x, int y, int iconX, ItemStack menuStack, ItemStack displayItem,
                               String onIcon, String offIcon, String tooltipKey,
                               Predicate<ItemStack> getter, BiConsumer<ItemStack, Boolean> setter,
                               Button.OnPress onPress) {
        super(x, y, 23, 26, iconX, y + 4, 16, 16, onPress);
        this.menuStack = menuStack;
        this.displayItem = displayItem == null ? ItemStack.EMPTY : displayItem;
        this.getter = getter;
        this.setter = setter;

        ResourceLocation on = this.displayItem.isEmpty()
                ? resolveIcon(onIcon)
                : XpRepairSettings.icon("hopper_item_mode_allow");
        iconMap.put(XpRepairSettings.ToggleState.ON, on);
        iconMap.put(XpRepairSettings.ToggleState.OFF, resolveIcon(offIcon));
        tooltipMap.put(XpRepairSettings.ToggleState.ON,
                Tooltip.create(Component.translatable(tooltipKey + ".on")));
        tooltipMap.put(XpRepairSettings.ToggleState.OFF,
                Tooltip.create(Component.translatable(tooltipKey + ".off")));
        states.add(XpRepairSettings.ToggleState.ON);
        states.add(XpRepairSettings.ToggleState.OFF);
        refreshState();
    }

    /** 父类构造期调用：此时字段未赋值，状态统一在构造体填充。 */
    @Override
    protected void initButton() {
    }

    /** 图标名解析：curios_/maid_ 前缀走 BI 自有贴图，其余走 BD widget 图集。 */
    private static ResourceLocation resolveIcon(String name) {
        if (name.startsWith("curios_") || name.startsWith("maid_")) {
            return XpRepairSettings.biIcon(name);
        }
        return XpRepairSettings.icon(name);
    }

    /** 按当前物品 NBT 刷新显示状态。 */
    public void refreshState() {
        XpRepairSettings.ToggleState state = getter.test(menuStack)
                ? XpRepairSettings.ToggleState.ON : XpRepairSettings.ToggleState.OFF;
        if (currentState != state) setState(state);
    }

    /** 切换并写回物品 NBT。 */
    public void toggleValue() {
        boolean next = !getter.test(menuStack);
        setter.accept(menuStack, next);
        setState(next ? XpRepairSettings.ToggleState.ON : XpRepairSettings.ToggleState.OFF);
    }

    @Override
    protected void drawIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int ix = getX() + iconOffsetX;
        int iy = getY() + iconOffsetY;
        if (displayItem.isEmpty()) {
            super.drawIcon(guiGraphics, mouseX, mouseY, partialTick);
        } else {
            guiGraphics.renderItem(displayItem, ix, iy);
        }
        if (currentState == XpRepairSettings.ToggleState.OFF) {
            guiGraphics.renderItem(BARRIER, ix, iy);
        }
    }
}
