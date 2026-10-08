package com.solr98.beyondintegration.client.widget;

import com.solr98.beyondintegration.feature.feeder.FeederThirstMode;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 喂食器「补水档位」侧面按钮：与 BD 喂食档位同款语义与贴图（右下角由 GUI 叠加水瓶角标）。
 */
public class FeederThirstModeButton extends RightTabButton
{
    public FeederThirstModeButton(int x, int y, int iconX, int iconY, OnPress onPress)
    {
        super(x, y, 23, 26, iconX, iconY, 16, 16, onPress);
    }

    @Override
    protected void initButton()
    {
        // 复用 BD 喂食档位贴图（同款语义）；切换顺序与 BD 喂食按钮一致：
        // HUNGER_TO_EAT -> NORMAL -> SATURATION_KEEP -> CRAZY
        iconMap.put(FeederThirstMode.HUNGER_TO_EAT, rl("feeder_mode_hunger_to_eat"));
        iconMap.put(FeederThirstMode.NORMAL, rl("feeder_mode_normal"));
        iconMap.put(FeederThirstMode.SATURATION_KEEP, rl("feeder_mode_saturation_keep"));
        iconMap.put(FeederThirstMode.CRAZY, rl("feeder_mode_crazy"));

        tooltipMap.put(FeederThirstMode.SATURATION_KEEP, Tooltip.create(Component.translatable("tooltip.button.beyond_integration.feeder_thirst_saturation_keep")));
        tooltipMap.put(FeederThirstMode.CRAZY, Tooltip.create(Component.translatable("tooltip.button.beyond_integration.feeder_thirst_crazy")));
        tooltipMap.put(FeederThirstMode.NORMAL, Tooltip.create(Component.translatable("tooltip.button.beyond_integration.feeder_thirst_normal")));
        tooltipMap.put(FeederThirstMode.HUNGER_TO_EAT, Tooltip.create(Component.translatable("tooltip.button.beyond_integration.feeder_thirst_hunger_to_eat")));

        states.addAll(iconMap.keySet());
        setState(FeederThirstMode.NORMAL);
    }

    /** 复用 BD 资源命名空间下的喂食档位纹理（1.20.1 为完整纹理路径） */
    private static ResourceLocation rl(String name)
    {
        return ResourceLocation.tryBuild("beyonddimensions", "textures/gui/sprites/widget/" + name + ".png");
    }
}
