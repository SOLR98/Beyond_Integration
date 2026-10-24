package com.solr98.beyondintegration.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.BDBaseGUI;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.client.gui.CommonTexturesRender;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.LeftTabButton;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.item.XpExchangeItem;
import com.wintercogs.beyonddimensions.common.item.XpExchangeSettings;
import com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * BI 版经验棒界面（复制并替换 BD {@code XpExchangeGUI}，LGPL 来源：Beyond Dimensions）。
 * <p>在 BD 界面基础上：
 * <ul>
 *   <li>最大等级显示与输入框长度按 BI 配置上限调整；</li>
 *   <li>右侧追加 BD 样式修复范围（盔甲/物品栏/主手副手/玩家饰品/女仆饰品）/ 模式 / 档位按钮。</li>
 * </ul>
 * 通过 {@code MenuScreens.register(BDMenus.Xp_Exchange_Menu, ...)} 覆盖 BD 原界面。
 */
public class BiXpExchangeGUI extends BDBaseGUI<XpExchangeMenu> {

    private RightTabButton keepModeButton;
    private EditBox targetLevelField;
    private boolean syncingField;

    private LeftTabButton rangeArmor;
    private LeftTabButton rangeInventory;
    private LeftTabButton rangeHands;
    private LeftTabButton rangeCurios;
    private LeftTabButton rangeMaid;
    private RightTabButton repairMode;
    private RightTabButton repairTier;

    public BiXpExchangeGUI(XpExchangeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();

        this.imageWidth = 176;
        this.imageHeight = rebuildImageHeight();
        rebuildLabelHeight();
        this.leftPos = (this.width - imageWidth) / 2;
        this.topPos = (this.height - imageHeight) / 2;

        this.targetLevelField = new EditBox(getFont(), this.leftPos + 50, this.topPos + 24, 82,
                this.getFont().lineHeight + 6,
                Component.translatable("menu.label.beyonddimensions.xp_exchange.target_level"));
        this.targetLevelField.setMaxLength(biFieldMaxLength());
        this.targetLevelField.setBordered(true);
        this.targetLevelField.setVisible(true);
        this.targetLevelField.setTextColor(16777215);
        this.targetLevelField.setTooltip(Tooltip.create(
                Component.translatable("tooltip.editbox.beyonddimensions.xp_exchange_target_level")));
        this.targetLevelField.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        this.targetLevelField.setValue(Integer.toString(XpExchangeSettings.getTargetLevel(menu.menuStack)));
        this.targetLevelField.setResponder(this::onTargetLevelChanged);
        addRenderableWidget(this.targetLevelField);

        keepModeButton = new RightTabButton(leftPos + 176, topPos + 6, 23, 26,
                leftPos + 176 + 3, topPos + 6 + 4, 16, 16, button -> {
            KeepModeState nextState = keepModeButton.currentState == KeepModeState.WORKING
                    ? KeepModeState.NOT_WORKING : KeepModeState.WORKING;
            keepModeButton.setState(nextState);
            XpExchangeItem.setXpNetKeepMode(menu.menuStack, nextState == KeepModeState.WORKING);
            menu.writeAndSendQuickData();
        }) {
            @Override
            protected void initButton() {
                iconMap.put(KeepModeState.WORKING, ResourceLocation.tryBuild(BDConstants.MODID,
                        "textures/gui/sprites/widget/control_mode_ignore.png"));
                iconMap.put(KeepModeState.NOT_WORKING, ResourceLocation.tryBuild(BDConstants.MODID,
                        "textures/gui/sprites/widget/control_mode_not_working.png"));
                tooltipMap.put(KeepModeState.WORKING, Tooltip.create(Component.translatable(
                        "tooltip.button.beyonddimensions.xp_exchange.keep_mode_working")));
                tooltipMap.put(KeepModeState.NOT_WORKING, Tooltip.create(Component.translatable(
                        "tooltip.button.beyonddimensions.xp_exchange.keep_mode_not_working")));
                this.states.add(KeepModeState.WORKING);
                this.states.add(KeepModeState.NOT_WORKING);
                setState(resolveKeepModeState());
            }
        };
        addRenderableWidget(keepModeButton);

        initRepairButtons();
    }

    private void initRepairButtons() {
        // 左侧：修复范围（BD 左侧 tab，23×26）
        int leftX = leftPos - 23;
        int leftIconX = leftX + 3;
        int y = topPos + 6;
        int w = 23;
        int h = 26;

        rangeArmor = rangeButton(leftX, y, leftIconX, w, h, new ItemStack(Items.DIAMOND_CHESTPLATE),
                "hopper_item_mode_allow", "hopper_item_mode_deny",
                "tooltip.beyond_integration.xp_repair.range.armor",
                XpRepairSettings::isArmorEnabled, XpRepairSettings::setArmorEnabled);
        rangeInventory = rangeButton(leftX, y + h, leftIconX, w, h, new ItemStack(Items.CHEST),
                "slot_button", "slot_button_disabled",
                "tooltip.beyond_integration.xp_repair.range.inventory",
                XpRepairSettings::isInventoryEnabled, XpRepairSettings::setInventoryEnabled);
        rangeHands = rangeButton(leftX, y + h * 2, leftIconX, w, h, new ItemStack(Items.DIAMOND_SWORD),
                "hopper_item_mode_allow", "hopper_item_mode_deny",
                "tooltip.beyond_integration.xp_repair.range.hands",
                XpRepairSettings::isHandsEnabled, XpRepairSettings::setHandsEnabled);
        rangeCurios = rangeButton(leftX, y + h * 3, leftIconX, w, h, ItemStack.EMPTY,
                "curios_bauble", "hopper_item_mode_deny",
                "tooltip.beyond_integration.xp_repair.range.curios",
                XpRepairSettings::isCuriosEnabled, XpRepairSettings::setCuriosEnabled);
        rangeMaid = rangeButton(leftX, y + h * 4, leftIconX, w, h, ItemStack.EMPTY,
                "maid_bauble", "hopper_item_mode_deny",
                "tooltip.beyond_integration.xp_repair.range.maid",
                XpRepairSettings::isMaidEnabled, XpRepairSettings::setMaidEnabled);

        // 右侧：模式 / 档位（BD 右侧 tab，23×26）
        int rightX = leftPos + 176;
        int rightIconX = rightX + 3;
        repairMode = new RightTabButton(rightX, topPos + 36, w, h, rightIconX, topPos + 40, 16, 16,
                b -> {
                    List<CommandConfig.XpRepairAmountMode> available = XpRepairSettings.availableModes();
                    if (available.size() <= 1) return;
                    int idx = available.indexOf(XpRepairSettings.getMode(menu.menuStack));
                    CommandConfig.XpRepairAmountMode next = available.get((idx + 1) % available.size());
                    XpRepairSettings.setMode(menu.menuStack, next);
                    ((RightTabButton) b).setState(next);
                    menu.writeAndSendQuickData();
                }) {
            @Override
            protected void initButton() {
                iconMap.put(CommandConfig.XpRepairAmountMode.PERCENT, XpRepairSettings.icon("white_filter"));
                iconMap.put(CommandConfig.XpRepairAmountMode.ABSOLUTE, XpRepairSettings.icon("black_filter"));
                tooltipMap.put(CommandConfig.XpRepairAmountMode.PERCENT,
                        Tooltip.create(Component.translatable("tooltip.beyond_integration.xp_repair.mode.percent")));
                tooltipMap.put(CommandConfig.XpRepairAmountMode.ABSOLUTE,
                        Tooltip.create(Component.translatable("tooltip.beyond_integration.xp_repair.mode.absolute")));
                states.add(CommandConfig.XpRepairAmountMode.PERCENT);
                states.add(CommandConfig.XpRepairAmountMode.ABSOLUTE);
                setState(XpRepairSettings.getMode(menu.menuStack));
            }
        };

        repairTier = new RightTabButton(rightX, topPos + 36 + h, w, h, rightIconX, topPos + 40 + h, 16, 16,
                b -> {
                    int max = XpRepairSettings.maxTier().ordinal();
                    if (max <= 0) return;
                    int idx = XpRepairSettings.getTier(menu.menuStack).ordinal();
                    CommandConfig.XpRepairTier next = CommandConfig.XpRepairTier.values()[(idx + 1) % (max + 1)];
                    XpRepairSettings.setTier(menu.menuStack, next);
                    ((RightTabButton) b).setState(next);
                    menu.writeAndSendQuickData();
                }) {
            @Override
            protected void initButton() {
                iconMap.put(CommandConfig.XpRepairTier.LOW, XpRepairSettings.icon("hopper_range_mode_low"));
                iconMap.put(CommandConfig.XpRepairTier.NORMAL, XpRepairSettings.icon("hopper_range_mode_mid"));
                iconMap.put(CommandConfig.XpRepairTier.HIGH, XpRepairSettings.icon("hopper_range_mode_high"));
                iconMap.put(CommandConfig.XpRepairTier.MAX, XpRepairSettings.icon("hopper_range_mode_highest"));
                for (CommandConfig.XpRepairTier tier : CommandConfig.XpRepairTier.values()) {
                    tooltipMap.put(tier, Tooltip.create(Component.translatable(
                            "tooltip.beyond_integration.xp_repair.tier." + tier.name().toLowerCase(Locale.ROOT))));
                    states.add(tier);
                }
                setState(XpRepairSettings.getTier(menu.menuStack));
            }
        };

        addRenderableWidget(rangeArmor);
        addRenderableWidget(rangeInventory);
        addRenderableWidget(rangeHands);
        addRenderableWidget(rangeCurios);
        addRenderableWidget(rangeMaid);
        addRenderableWidget(repairMode);
        addRenderableWidget(repairTier);
        refreshRepairButtons();
    }
    private LeftTabButton rangeButton(int x, int y, int iconX, int w, int h, ItemStack item,
                                      String onIcon, String offIcon, String tooltipKey,
                                      Predicate<ItemStack> getter,
                                      BiConsumer<ItemStack, Boolean> setter) {
        final ItemStack displayItem = item == null ? ItemStack.EMPTY : item;
        final ItemStack barrier = new ItemStack(Items.BARRIER);
        return new LeftTabButton(x, y, w, h, iconX, y + 4, 16, 16, b -> {
            boolean next = !getter.test(menu.menuStack);
            setter.accept(menu.menuStack, next);
            ((LeftTabButton) b).setState(next
                    ? XpRepairSettings.ToggleState.ON : XpRepairSettings.ToggleState.OFF);
            menu.writeAndSendQuickData();
        }) {
            @Override
            protected void initButton() {
                // 物品类按钮不绘制 iconMap 图标（由 drawIcon 绘制物品），此处仅占位避免空纹理
                ResourceLocation on = displayItem.isEmpty()
                        ? (onIcon.startsWith("curios_") || onIcon.startsWith("maid_")
                        ? XpRepairSettings.biIcon(onIcon) : XpRepairSettings.icon(onIcon))
                        : XpRepairSettings.icon("hopper_item_mode_allow");
                iconMap.put(XpRepairSettings.ToggleState.ON, on);
                iconMap.put(XpRepairSettings.ToggleState.OFF, XpRepairSettings.icon(offIcon));
                Tooltip tooltip = Tooltip.create(Component.translatable(tooltipKey));
                tooltipMap.put(XpRepairSettings.ToggleState.ON, tooltip);
                tooltipMap.put(XpRepairSettings.ToggleState.OFF, tooltip);
                states.add(XpRepairSettings.ToggleState.ON);
                states.add(XpRepairSettings.ToggleState.OFF);
                setState(getter.test(menu.menuStack)
                        ? XpRepairSettings.ToggleState.ON : XpRepairSettings.ToggleState.OFF);
            }

            @Override
            protected void drawIcon(GuiGraphics g, int mouseX, int mouseY, float pt) {
                int ix = getX() + iconOffsetX;
                int iy = getY() + iconOffsetY;
                if (displayItem.isEmpty()) {
                    super.drawIcon(g, mouseX, mouseY, pt);
                } else {
                    g.renderItem(displayItem, ix, iy);
                }
                // 关闭状态叠加屏障图标
                if (currentState == XpRepairSettings.ToggleState.OFF) {
                    g.renderItem(barrier, ix, iy);
                }
            }
        };
    }

    private void refreshRepairButtons() {
        boolean allowed = CommandConfig.xpAutoRepairEnabled() && !XpRepairSettings.availableModes().isEmpty();
        if (rangeArmor != null) {
            rangeArmor.setState(XpRepairSettings.isArmorEnabled(menu.menuStack)
                    ? XpRepairSettings.ToggleState.ON : XpRepairSettings.ToggleState.OFF);
            rangeArmor.active = allowed;
        }
        if (rangeInventory != null) {
            rangeInventory.setState(XpRepairSettings.isInventoryEnabled(menu.menuStack)
                    ? XpRepairSettings.ToggleState.ON : XpRepairSettings.ToggleState.OFF);
            rangeInventory.active = allowed;
        }
        if (rangeHands != null) {
            rangeHands.setState(XpRepairSettings.isHandsEnabled(menu.menuStack)
                    ? XpRepairSettings.ToggleState.ON : XpRepairSettings.ToggleState.OFF);
            rangeHands.active = allowed;
        }
        if (rangeCurios != null) {
            rangeCurios.setState(XpRepairSettings.isCuriosEnabled(menu.menuStack)
                    ? XpRepairSettings.ToggleState.ON : XpRepairSettings.ToggleState.OFF);
            rangeCurios.active = allowed;
        }
        if (rangeMaid != null) {
            rangeMaid.setState(XpRepairSettings.isMaidEnabled(menu.menuStack)
                    ? XpRepairSettings.ToggleState.ON : XpRepairSettings.ToggleState.OFF);
            rangeMaid.active = allowed;
        }
        if (repairMode != null) {
            repairMode.setState(XpRepairSettings.getMode(menu.menuStack));
            repairMode.active = allowed && XpRepairSettings.availableModes().size() > 1;
        }
        if (repairTier != null) {
            repairTier.setState(XpRepairSettings.getTier(menu.menuStack));
            repairTier.active = allowed && XpRepairSettings.maxTier().ordinal() > 0;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        KeepModeState keepModeState = resolveKeepModeState();
        if (keepModeButton.currentState != keepModeState)
            keepModeButton.setState(keepModeState);

        String currentTargetLevel = Integer.toString(XpExchangeSettings.getTargetLevel(menu.menuStack));
        if (!targetLevelField.isFocused() && !targetLevelField.getValue().equals(currentTargetLevel)) {
            syncingField = true;
            targetLevelField.setValue(currentTargetLevel);
            syncingField = false;
        }

        refreshRepairButtons();
    }

    private void onTargetLevelChanged(String text) {
        if (syncingField) return;
        int targetLevel = text.isEmpty() ? 0 : Integer.parseInt(text);
        int sanitizedTargetLevel = XpExchangeSettings.sanitizeTargetLevel(targetLevel);
        XpExchangeSettings.setTargetLevel(menu.menuStack, sanitizedTargetLevel);
        if (!text.isEmpty()) {
            String sanitizedText = Integer.toString(sanitizedTargetLevel);
            if (!sanitizedText.equals(text)) {
                syncingField = true;
                targetLevelField.setValue(sanitizedText);
                syncingField = false;
            }
        }
        menu.writeAndSendQuickData();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (targetLevelField != null && targetLevelField.isMouseOver(mouseX, mouseY) && delta != 0) {
            int step = hasControlDown() ? 100 : (hasShiftDown() ? 10 : 1);
            int direction = delta > 0 ? 1 : -1;
            int currentValue = targetLevelField.getValue().isEmpty() ? 0 : Integer.parseInt(targetLevelField.getValue());
            int nextValue = XpExchangeSettings.sanitizeTargetLevel(currentValue + direction * step);

            syncingField = true;
            targetLevelField.setValue(Integer.toString(nextValue));
            syncingField = false;

            XpExchangeSettings.setTargetLevel(menu.menuStack, nextValue);
            menu.writeAndSendQuickData();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private KeepModeState resolveKeepModeState() {
        return XpExchangeItem.getOrDefaultXpNetKeepMode(menu.menuStack, false)
                ? KeepModeState.WORKING : KeepModeState.NOT_WORKING;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int[] drawY = new int[]{this.topPos};
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        CommonTexturesRender.renderTopBaseCommon(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderCommonConnection(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderCommonConnection(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderCommonConnection(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderCommonConnection(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderCommonConnection(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderPlayerInv(guiGraphics, this.leftPos, drawY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        guiGraphics.drawString(this.font, Component.translatable("menu.label.beyonddimensions.xp_exchange.target_level"),
                8, 27, 4210752, false);
        int maxLevel = CommandConfig.xpRodTweaksEnabled()
                ? CommandConfig.xpRodMaxTargetLevel() : XpExchangeSettings.MAX_TARGET_LEVEL;
        guiGraphics.drawString(this.font,
                Component.translatable("menu.label.beyonddimensions.xp_exchange.max_level", maxLevel),
                8, 41, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    protected int rebuildImageHeight() {
        return CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.COMMON_CONNECTION_HEIGHT * 5
                + CommonTextures.PLAYER_INV_HEIGHT;
    }

    protected void rebuildLabelHeight() {
        this.titleLabelY = 8;
        this.inventoryLabelY = CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.COMMON_CONNECTION_HEIGHT * 4 + 4;
    }

    /** 输入框长度：至少覆盖 BI 配置最大值的位数。 */
    private int biFieldMaxLength() {
        if (!CommandConfig.xpRodTweaksEnabled()) return 6;
        return Math.max(6, Integer.toString(CommandConfig.xpRodMaxTargetLevel()).length());
    }

    private enum KeepModeState {
        WORKING,
        NOT_WORKING
    }
}
