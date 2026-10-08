package com.solr98.beyondintegration.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.solr98.beyondintegration.common.menu.MagnetMenu;
import com.solr98.beyondintegration.feature.magnet.MagnetSettings;
import com.solr98.beyondintegration.feature.magnet.MagnetTier;
import com.solr98.beyondintegration.feature.magnet.MagnetTiers;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.BDBaseGUI;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.client.gui.CommonTexturesRender;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.client.init.BDShortKeys;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.HopperNBTMode;
import com.wintercogs.beyonddimensions.common.machine.HopperRangeMode;
import com.wintercogs.beyonddimensions.common.machine.HopperXpMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 网络磁铁界面的 BI 版实现（1.21.1）：从 BD {@code NetMagnetGUI} 复制而来，绑定 {@link MagnetMenu}。
 * 复制到 BI 以便后续自定义（如自定义档位选择按钮）。
 */
public class MagnetGUI extends BDBaseGUI<MagnetMenu> {

    private RightTabButton filterModeButton;
    private RightTabButton controlModeButton;
    private RightTabButton hopperItemModeButton;
    private RightTabButton hopperXpModeButton;
    private RightTabButton hopperNBTModeButton;
    private RightTabButton hopperFluidModeButton;
    private IconButton itemRangeButton;
    private IconButton fluidRangeButton;
    private int lastItemRangeIndex = Integer.MIN_VALUE;
    private int lastFluidRangeIndex = Integer.MIN_VALUE;

    public MagnetGUI(MagnetMenu menu, Inventory playerInventory, Component title) {
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

        filterModeButton = new RightTabButton(leftPos + 176, topPos + 6, 23, 26,
                leftPos + 176 + 3, topPos + 6 + 4, 16, 16, button -> {
            filterModeButton.toggleState();
            menu.menuStack.set(BDDataComponents.FILTER_MODE, (FilterMode) filterModeButton.currentState);
            menu.writeAndSendQuickData();
        }) {
            @Override
            protected void initButton() {
                iconMap.put(FilterMode.IGNORE, ResourceLocation.tryBuild(BDConstants.MODID, "widget/ignore_filter"));
                iconMap.put(FilterMode.WHITE, ResourceLocation.tryBuild(BDConstants.MODID, "widget/white_filter"));
                iconMap.put(FilterMode.BLACK, ResourceLocation.tryBuild(BDConstants.MODID, "widget/black_filter"));

                tooltipMap.put(FilterMode.IGNORE, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.filter_mode_ignore")));
                tooltipMap.put(FilterMode.WHITE, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.filter_mode_white")));
                tooltipMap.put(FilterMode.BLACK, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.filter_mode_black")));

                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }

                setState(menu.menuStack.get(BDDataComponents.FILTER_MODE));
            }
        };
        addRenderableWidget(filterModeButton);

        controlModeButton = new RightTabButton(leftPos + 176, topPos + 36, 23, 26,
                leftPos + 176 + 3, topPos + 36 + 4, 16, 16, button -> {
            controlModeButton.toggleState();
            menu.menuStack.set(BDDataComponents.CONTROL_MODE, (RedStoneControlMode) controlModeButton.currentState);
            menu.writeAndSendQuickData();
        }) {
            @Override
            protected void initButton() {
                iconMap.put(RedStoneControlMode.IGNORE, ResourceLocation.tryBuild(BDConstants.MODID, "widget/control_mode_ignore"));
                iconMap.put(RedStoneControlMode.NOT_WORKING, ResourceLocation.tryBuild(BDConstants.MODID, "widget/control_mode_not_working"));

                tooltipMap.put(RedStoneControlMode.IGNORE, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.control_mode_ignore")));
                tooltipMap.put(RedStoneControlMode.NOT_WORKING, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.control_mode_not_working")));

                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }

                setState(menu.menuStack.get(BDDataComponents.CONTROL_MODE));
            }
        };
        addRenderableWidget(controlModeButton);

        hopperItemModeButton = new RightTabButton(leftPos + 176, topPos + 66, 23, 26,
                leftPos + 176 + 3, topPos + 66 + 4, 16, 16, button -> {
            hopperItemModeButton.toggleState();
            menu.menuStack.set(BDDataComponents.HOPPER_ITEM_MODE, (HopperItemMode) hopperItemModeButton.currentState);
            menu.writeAndSendQuickData();
        }) {
            @Override
            protected void initButton() {
                iconMap.put(HopperItemMode.DENY, ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_item_mode_deny"));
                iconMap.put(HopperItemMode.ALLOW, ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_item_mode_allow"));

                tooltipMap.put(HopperItemMode.DENY, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.hopper_item_mode_deny")));
                tooltipMap.put(HopperItemMode.ALLOW, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.hopper_item_mode_allow")));

                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }

                setState(menu.menuStack.get(BDDataComponents.HOPPER_ITEM_MODE));
            }
        };
        addRenderableWidget(hopperItemModeButton);

        hopperXpModeButton = new RightTabButton(leftPos + 176, topPos + 96, 23, 26,
                leftPos + 176 + 3, topPos + 96 + 4, 16, 16, button -> {
            hopperXpModeButton.toggleState();
            menu.menuStack.set(BDDataComponents.HOPPER_XP_MODE, (HopperXpMode) hopperXpModeButton.currentState);
            menu.writeAndSendQuickData();
        }) {
            @Override
            protected void initButton() {
                iconMap.put(HopperXpMode.DENY, ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_xp_mode_deny"));
                iconMap.put(HopperXpMode.ALLOW, ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_xp_mode_allow"));

                tooltipMap.put(HopperXpMode.DENY, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.hopper_xp_mode_deny")));
                tooltipMap.put(HopperXpMode.ALLOW, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.hopper_xp_mode_allow")));

                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }

                setState(menu.menuStack.get(BDDataComponents.HOPPER_XP_MODE));
            }
        };
        addRenderableWidget(hopperXpModeButton);

        hopperNBTModeButton = new RightTabButton(leftPos + 176, topPos + 126, 23, 26,
                leftPos + 176 + 3, topPos + 126 + 4, 16, 16, button -> {
            hopperNBTModeButton.toggleState();
            menu.menuStack.set(BDDataComponents.HOPPER_NBT_MODE, (HopperNBTMode) hopperNBTModeButton.currentState);
            menu.writeAndSendQuickData();
        }) {
            @Override
            protected void initButton() {
                iconMap.put(HopperNBTMode.DENY, ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_nbt_mode_deny"));
                iconMap.put(HopperNBTMode.ALLOW, ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_nbt_mode_allow"));

                tooltipMap.put(HopperNBTMode.DENY, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.hopper_nbt_mode_deny")));
                tooltipMap.put(HopperNBTMode.ALLOW, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.hopper_nbt_mode_allow")));

                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }

                setState(menu.menuStack.get(BDDataComponents.HOPPER_NBT_MODE));
            }
        };
        addRenderableWidget(hopperNBTModeButton);

        hopperFluidModeButton = new RightTabButton(leftPos + 176, topPos + 156, 23, 26,
                leftPos + 176 + 3, topPos + 156 + 4, 16, 16, button -> {
            hopperFluidModeButton.toggleState();
            menu.menuStack.set(BDDataComponents.HOPPER_FLUID_MODE, (HopperFluidMode) hopperFluidModeButton.currentState);
            menu.writeAndSendQuickData();
        }) {
            @Override
            protected void initButton() {
                iconMap.put(HopperFluidMode.DENY, ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_fluid_mode_deny"));
                iconMap.put(HopperFluidMode.ALLOW, ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_fluid_mode_allow"));

                tooltipMap.put(HopperFluidMode.DENY, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.hopper_fluid_mode_deny")));
                tooltipMap.put(HopperFluidMode.ALLOW, Tooltip.create(Component.translatable("tooltip.button.beyonddimensions.hopper_fluid_mode_allow")));

                for (Enum<?> state : iconMap.keySet()) {
                    this.states.add(state);
                }

                setState(menu.menuStack.get(BDDataComponents.HOPPER_FLUID_MODE));
            }
        };
        addRenderableWidget(hopperFluidModeButton);

        itemRangeButton = new IconButton(leftPos - 23, topPos + 126, 23, 26,
                rangeIcon(0),
                leftPos - 18, topPos + 126 + 4, 16, 16, button -> {
            int next = Math.floorMod(MagnetSettings.effectiveItemTier(menu.menuStack) + 1, MagnetTiers.COUNT);
            MagnetSettings.setItemTierIndex(menu.menuStack, next);
            menu.writeAndSendQuickData();
            refreshItemRangeButton(next);
        });
        itemRangeButton.setBackgroundSprites(new WidgetSprites(CommonTextures.LEFT_TAB, CommonTextures.LEFT_TAB));
        addRenderableWidget(itemRangeButton);
        refreshItemRangeButton(MagnetSettings.effectiveItemTier(menu.menuStack));

        fluidRangeButton = new IconButton(leftPos - 23, topPos + 156, 23, 26,
                rangeIcon(0),
                leftPos - 18, topPos + 156 + 4, 16, 16, button -> {
            int next = Math.floorMod(MagnetSettings.effectiveFluidTier(menu.menuStack) + 1, MagnetTiers.COUNT);
            MagnetSettings.setFluidTierIndex(menu.menuStack, next);
            menu.writeAndSendQuickData();
            refreshFluidRangeButton(next);
        });
        fluidRangeButton.setBackgroundSprites(new WidgetSprites(CommonTextures.LEFT_TAB, CommonTextures.LEFT_TAB));
        addRenderableWidget(fluidRangeButton);
        refreshFluidRangeButton(MagnetSettings.effectiveFluidTier(menu.menuStack));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (filterModeButton.currentState != menu.menuStack.get(BDDataComponents.FILTER_MODE))
            filterModeButton.setState(menu.menuStack.get(BDDataComponents.FILTER_MODE));

        if (controlModeButton.currentState != menu.menuStack.get(BDDataComponents.CONTROL_MODE))
            controlModeButton.setState(menu.menuStack.get(BDDataComponents.CONTROL_MODE));

        if (hopperItemModeButton.currentState != menu.menuStack.get(BDDataComponents.HOPPER_ITEM_MODE))
            hopperItemModeButton.setState(menu.menuStack.get(BDDataComponents.HOPPER_ITEM_MODE));

        if (hopperXpModeButton.currentState != menu.menuStack.get(BDDataComponents.HOPPER_XP_MODE))
            hopperXpModeButton.setState(menu.menuStack.get(BDDataComponents.HOPPER_XP_MODE));

        if (hopperNBTModeButton.currentState != menu.menuStack.get(BDDataComponents.HOPPER_NBT_MODE))
            hopperNBTModeButton.setState(menu.menuStack.get(BDDataComponents.HOPPER_NBT_MODE));

        if (hopperFluidModeButton.currentState != menu.menuStack.get(BDDataComponents.HOPPER_FLUID_MODE))
            hopperFluidModeButton.setState(menu.menuStack.get(BDDataComponents.HOPPER_FLUID_MODE));

        int itemIdx = MagnetSettings.effectiveItemTier(menu.menuStack);
        if (itemIdx != lastItemRangeIndex) {
            refreshItemRangeButton(itemIdx);
        }
        int fluidIdx = MagnetSettings.effectiveFluidTier(menu.menuStack);
        if (fluidIdx != lastFluidRangeIndex) {
            refreshFluidRangeButton(fluidIdx);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key mouseKey = InputConstants.getKey(keyCode, scanCode);

        if (Minecraft.getInstance().options.keyInventory.isActiveAndMatches(mouseKey)
                || BDShortKeys.OPEN_MAGNET_GUI_KEY.getKey() == mouseKey) {
            onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int[] drawY = new int[]{this.topPos};
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        CommonTexturesRender.renderTopBaseCommon(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderFilterSlots(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderFilterSlots(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderFilterSlots(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderFilterSlots(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderCommonConnection(guiGraphics, this.leftPos, drawY);
        CommonTexturesRender.renderPlayerInv(guiGraphics, this.leftPos, drawY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        GuiRenderHelper.drawRightAnchoredText(guiGraphics, this.font, Component.translatable("menu.label.beyonddimensions.filter_slots"), imageWidth - 6, this.titleLabelY + 3, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    /** BD 原版六个距离图标，作为自定义档位的循环图标（数量可多于 6，按索引取模）。 */
    private static final ResourceLocation[] RANGE_ICONS = new ResourceLocation[]{
            ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_range_mode_lowest"),
            ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_range_mode_low"),
            ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_range_mode_mid"),
            ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_range_mode_high"),
            ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_range_mode_highest"),
            ResourceLocation.tryBuild(BDConstants.MODID, "widget/hopper_range_mode_chunk"),
    };

    private static ResourceLocation rangeIcon(int index) {
        return RANGE_ICONS[Math.floorMod(index, RANGE_ICONS.length)];
    }

    /** 刷新物品吸取档位按钮的图标与提示文本（随当前档位变化）。 */
    private void refreshItemRangeButton(int index) {
        lastItemRangeIndex = index;
        MagnetTier tier = MagnetTiers.itemByIndex(index);
        itemRangeButton.setIcon(rangeIcon(index));
        itemRangeButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.beyond_integration.magnet.range.item",
                tierName(tier.name()), radiusText(tier), tier.interval())));
    }

    /** 刷新流体吸取档位按钮的图标与提示文本（随当前档位变化）。 */
    private void refreshFluidRangeButton(int index) {
        lastFluidRangeIndex = index;
        MagnetTier tier = MagnetTiers.fluidByIndex(index);
        fluidRangeButton.setIcon(rangeIcon(index));
        fluidRangeButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.beyond_integration.magnet.range.fluid",
                tierName(tier.name()), radiusText(tier), tier.interval())));
    }

    private static Component radiusText(MagnetTier tier) {
        return tier.chunk()
                ? Component.translatable("gui.beyond_integration.magnet.range.chunk")
                : Component.translatable("gui.beyond_integration.magnet.range.blocks", tier.radius());
    }

    /** 档位名本地化：默认档位名映射到翻译，自定义名原样显示。 */
    private static Component tierName(String name) {
        return switch (name) {
            case "lowest", "low", "mid", "high", "highest", "chunk" ->
                    Component.translatable("gui.beyond_integration.magnet.tier." + name);
            default -> Component.literal(name);
        };
    }

    protected int rebuildImageHeight() {
        return CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.FILTER_SLOTS_HEIGHT * 4 + CommonTextures.COMMON_CONNECTION_HEIGHT + CommonTextures.PLAYER_INV_HEIGHT;
    }

    protected void rebuildLabelHeight() {
        this.titleLabelY = 8;
        this.inventoryLabelY = CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.FILTER_SLOTS_HEIGHT * 4 + 4;
    }
}
