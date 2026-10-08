package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.hopper.HopperTierAccess;
import com.solr98.beyondintegration.feature.magnet.MagnetSettings;
import com.solr98.beyondintegration.feature.magnet.MagnetTier;
import com.solr98.beyondintegration.feature.magnet.MagnetTiers;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.client.gui.NetHopperGUI;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.LeftTabButton;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.WidgetSprites;
import com.wintercogs.beyonddimensions.common.menu.NetHopperMenu;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 维度网络漏斗界面扩展：隐藏 BD 原“距离按钮”，改为 BI 的「物品档位 / 流体档位」两个按钮
 * （与磁铁界面一致；档位复用磁铁配置，选择存于方块 NBT 并随 QuickData 同步）。
 */
@Mixin(value = NetHopperGUI.class, remap = false)
public abstract class NetHopperGUITierMixin {

    @Shadow(remap = false)
    private LeftTabButton hopperRangeModeButton;

    @Unique private static java.lang.reflect.Method beyond$addWidgetMethod;
    @Unique private static ResourceLocation[] beyond$ICONS;
    @Unique private IconButton beyond$itemButton;
    @Unique private IconButton beyond$fluidButton;
    @Unique private int beyond$lastItem = Integer.MIN_VALUE;
    @Unique private int beyond$lastFluid = Integer.MIN_VALUE;

    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void beyond$addButtons(CallbackInfo ci) {
        var self = (NetHopperGUI) (Object) this;
        NetHopperMenu menu = self.getMenu();
        if (!(menu.be instanceof HopperTierAccess access)) return;

        if (hopperRangeModeButton != null) {
            hopperRangeModeButton.visible = false;
            hopperRangeModeButton.active = false;
        }

        int x = self.getGuiLeft() - 23;
        int iconX = self.getGuiLeft() - 18;
        int top = self.getGuiTop();

        beyond$itemButton = new IconButton(x, top + 126, 23, 26, beyond$icon(0), iconX, top + 130, 16, 16, b -> {
            int next = Math.floorMod(beyond$effItem(access) + 1, MagnetTiers.COUNT);
            access.beyond$setItemTier(next);
            menu.writeAndSendQuickData();
            beyond$refreshItem(access, next);
        });
        beyond$itemButton.setBackgroundSprites(new WidgetSprites(CommonTextures.LEFT_TAB, CommonTextures.LEFT_TAB));
        beyond$addWidget(beyond$itemButton);
        beyond$refreshItem(access, beyond$effItem(access));

        beyond$fluidButton = new IconButton(x, top + 156, 23, 26, beyond$icon(0), iconX, top + 160, 16, 16, b -> {
            int next = Math.floorMod(beyond$effFluid(access) + 1, MagnetTiers.COUNT);
            access.beyond$setFluidTier(next);
            menu.writeAndSendQuickData();
            beyond$refreshFluid(access, next);
        });
        beyond$fluidButton.setBackgroundSprites(new WidgetSprites(CommonTextures.LEFT_TAB, CommonTextures.LEFT_TAB));
        beyond$addWidget(beyond$fluidButton);
        beyond$refreshFluid(access, beyond$effFluid(access));
    }

    @Inject(method = "containerTick", at = @At("TAIL"), remap = true)
    private void beyond$syncButtons(CallbackInfo ci) {
        var self = (NetHopperGUI) (Object) this;
        NetHopperMenu menu = self.getMenu();
        if (!(menu.be instanceof HopperTierAccess access)) return;
        if (beyond$itemButton != null) {
            int v = beyond$effItem(access);
            if (v != beyond$lastItem) beyond$refreshItem(access, v);
        }
        if (beyond$fluidButton != null) {
            int v = beyond$effFluid(access);
            if (v != beyond$lastFluid) beyond$refreshFluid(access, v);
        }
    }

    @Unique private int beyond$effItem(HopperTierAccess a) {
        int v = a.beyond$getItemTier();
        return v >= 0 ? v : MagnetSettings.DEFAULT_INDEX;
    }

    @Unique private int beyond$effFluid(HopperTierAccess a) {
        int v = a.beyond$getFluidTier();
        return v >= 0 ? v : MagnetSettings.DEFAULT_INDEX;
    }

    @Unique private void beyond$refreshItem(HopperTierAccess access, int index) {
        beyond$lastItem = index;
        MagnetTier tier = MagnetTiers.itemByIndex(index);
        if (beyond$itemButton != null) {
            beyond$itemButton.setIcon(beyond$icon(index));
            beyond$itemButton.setTooltip(Tooltip.create(Component.translatable(
                    "gui.beyond_integration.magnet.range.item",
                    beyond$tierName(tier.name()), beyond$radius(tier), tier.interval())));
        }
    }

    @Unique private void beyond$refreshFluid(HopperTierAccess access, int index) {
        beyond$lastFluid = index;
        MagnetTier tier = MagnetTiers.fluidByIndex(index);
        if (beyond$fluidButton != null) {
            beyond$fluidButton.setIcon(beyond$icon(index));
            beyond$fluidButton.setTooltip(Tooltip.create(Component.translatable(
                    "gui.beyond_integration.magnet.range.fluid",
                    beyond$tierName(tier.name()), beyond$radius(tier), tier.interval())));
        }
    }

    @Unique private static Component beyond$radius(MagnetTier tier) {
        return tier.chunk()
                ? Component.translatable("gui.beyond_integration.magnet.range.chunk")
                : Component.translatable("gui.beyond_integration.magnet.range.blocks", tier.radius());
    }

    @Unique private static Component beyond$tierName(String name) {
        return switch (name) {
            case "lowest", "low", "mid", "high", "highest", "chunk" ->
                    Component.translatable("gui.beyond_integration.magnet.tier." + name);
            default -> Component.literal(name);
        };
    }

    @Unique private static ResourceLocation beyond$icon(int index) {
        if (beyond$ICONS == null) {
            beyond$ICONS = new ResourceLocation[]{
                    ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/hopper_range_mode_lowest.png"),
                    ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/hopper_range_mode_low.png"),
                    ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/hopper_range_mode_mid.png"),
                    ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/hopper_range_mode_high.png"),
                    ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/hopper_range_mode_highest.png"),
                    ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/hopper_range_mode_chunk.png"),
            };
        }
        return beyond$ICONS[Math.floorMod(index, beyond$ICONS.length)];
    }

    @Unique private void beyond$addWidget(GuiEventListener widget) {
        try {
            if (beyond$addWidgetMethod == null) {
                beyond$addWidgetMethod = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                        Screen.class, "m_142416_", GuiEventListener.class);
            }
            beyond$addWidgetMethod.setAccessible(true);
            beyond$addWidgetMethod.invoke(this, widget);
        } catch (Throwable ignored) {
        }
    }
}
