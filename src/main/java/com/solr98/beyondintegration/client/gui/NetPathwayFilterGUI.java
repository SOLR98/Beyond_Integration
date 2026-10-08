package com.solr98.beyondintegration.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.solr98.beyondintegration.common.menu.NetPathwayFilterMenu;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.BDBaseGUI;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.client.gui.CommonTexturesRender;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.util.GuiRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 维度网络通道过滤界面：复用 BD 原版布局（顶部标题栏 + 过滤标记槽 + 连接条 + 玩家背包），
 * 右侧挂载 BD {@link RightTabButton} 切换「启用过滤 / 仅接收输入 / 模糊过滤」，风格与 BD 网络泵/漏斗一致。
 */
public class NetPathwayFilterGUI extends BDBaseGUI<NetPathwayFilterMenu> {

    private static final int TAB_X = 176;
    private static final int TAB_W = 23;
    private static final int TAB_H = 26;
    private static final int ICON_INSET_X = 3;
    private static final int ICON_INSET_Y = 4;
    private static final int ICON_SIZE = 16;

    private RightTabButton filterButton;
    private RightTabButton onlyInputButton;
    private RightTabButton fuzzyButton;

    public NetPathwayFilterGUI(NetPathwayFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** BD 按钮的两态枚举（顺序为点击切换顺序）。 */
    private enum Toggle { OFF, ON }

    @Override
    protected void init() {
        super.init();
        this.imageWidth = 176;
        this.imageHeight = rebuildImageHeight();
        rebuildLabelHeight();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        filterButton = createToggle(this.topPos + 6,
                sprite("white_filter.png"), sprite("ignore_filter.png"),
                "gui.beyond_integration.net_pathway.filter",
                () -> menu.access != null && menu.access.beyond$isFilterEnabled(),
                value -> menu.access.beyond$setFilterEnabled(value));
        addRenderableWidget(filterButton);

        onlyInputButton = createToggle(this.topPos + 36,
                sprite("hopper_item_mode_deny.png"), sprite("hopper_item_mode_allow.png"),
                "gui.beyond_integration.net_pathway.only_input",
                () -> menu.access != null && menu.access.beyond$isOnlyInput(),
                value -> menu.access.beyond$setOnlyInput(value));
        addRenderableWidget(onlyInputButton);

        fuzzyButton = createToggle(this.topPos + 66,
                sprite("hopper_nbt_mode_deny.png"), sprite("hopper_nbt_mode_allow.png"),
                "gui.beyond_integration.net_pathway.fuzzy",
                () -> menu.access != null && menu.access.beyond$isFuzzy(),
                value -> menu.access.beyond$setFuzzy(value));
        addRenderableWidget(fuzzyButton);
    }

    private RightTabButton createToggle(int y, ResourceLocation iconOn, ResourceLocation iconOff,
                                        String labelKey, BooleanSupplier getter, Consumer<Boolean> setter) {
        RightTabButton[] ref = new RightTabButton[1];
        RightTabButton button = new RightTabButton(this.leftPos + TAB_X, y, TAB_W, TAB_H,
                this.leftPos + TAB_X + ICON_INSET_X, y + ICON_INSET_Y, ICON_SIZE, ICON_SIZE,
                b -> {
                    ref[0].toggleState();
                    if (menu.access != null) {
                        setter.accept(ref[0].currentState == Toggle.ON);
                        menu.writeAndSendQuickData();
                    }
                }) {
            @Override
            protected void initButton() {
                iconMap.put(Toggle.OFF, iconOff);
                iconMap.put(Toggle.ON, iconOn);
                tooltipMap.put(Toggle.OFF, toggleTooltip(labelKey, false));
                tooltipMap.put(Toggle.ON, toggleTooltip(labelKey, true));
                states.add(Toggle.OFF);
                states.add(Toggle.ON);
                setState(getter.getAsBoolean() ? Toggle.ON : Toggle.OFF);
            }
        };
        ref[0] = button;
        return button;
    }

    private static Tooltip toggleTooltip(String labelKey, boolean on) {
        return Tooltip.create(Component.translatable(labelKey,
                Component.translatable(on
                        ? "gui.beyond_integration.net_pathway.on"
                        : "gui.beyond_integration.net_pathway.off")));
    }

    private static ResourceLocation sprite(String file) {
        return ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/" + file);
    }

    private int rows() {
        return Math.max(1, (menu.filterSlots.getSlots() + 8) / 9);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncButton(filterButton, menu.access != null && menu.access.beyond$isFilterEnabled());
        syncButton(onlyInputButton, menu.access != null && menu.access.beyond$isOnlyInput());
        syncButton(fuzzyButton, menu.access != null && menu.access.beyond$isFuzzy());
    }

    private static void syncButton(RightTabButton button, boolean on) {
        Toggle target = on ? Toggle.ON : Toggle.OFF;
        if (button.currentState != target) {
            button.setState(target);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int[] y = new int[]{this.topPos};
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        CommonTexturesRender.renderTopBaseCommon(guiGraphics, this.leftPos, y);
        for (int i = 0; i < rows(); i++) {
            CommonTexturesRender.renderFilterSlots(guiGraphics, this.leftPos, y);
        }
        CommonTexturesRender.renderCommonConnection(guiGraphics, this.leftPos, y);
        CommonTexturesRender.renderPlayerInv(guiGraphics, this.leftPos, y);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        GuiRenderHelper.drawRightAnchoredText(guiGraphics, this.font,
                Component.translatable("menu.label.beyonddimensions.filter_slots"),
                imageWidth - 6, this.titleLabelY + 3, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    private int rebuildImageHeight() {
        return CommonTextures.TOP_BASE_COMMON_HEIGHT
                + rows() * CommonTextures.FILTER_SLOTS_HEIGHT
                + CommonTextures.COMMON_CONNECTION_HEIGHT
                + CommonTextures.PLAYER_INV_HEIGHT;
    }

    private void rebuildLabelHeight() {
        this.titleLabelY = 8;
        this.inventoryLabelY = CommonTextures.TOP_BASE_COMMON_HEIGHT
                + rows() * CommonTextures.FILTER_SLOTS_HEIGHT + 4;
    }
}
