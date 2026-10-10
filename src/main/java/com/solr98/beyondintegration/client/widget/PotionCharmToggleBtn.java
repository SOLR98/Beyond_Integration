package com.solr98.beyondintegration.client.widget;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.feature.charm.PotionCharmMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 网络药水护符按钮（16×16）：
 * <ul>
 *   <li>正常点击：循环切换生效目标（仅玩家 → 仅女仆 → 玩家和女仆 → 关闭）；</li>
 *   <li>Shift+点击：献祭网络内一本"单附魔=经验修补"附魔书，解锁该网络的经验修补。</li>
 * </ul>
 * 关闭时显示禁用纹理，其余显示正常/悬停纹理 + 药水图标；tooltip 显示目标与解锁状态。
 */
public class PotionCharmToggleBtn extends Button {
    private static final ResourceLocation SLOT = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    private static final ResourceLocation SLOT_DISABLED = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_disabled.png");
    private static final ResourceLocation SLOT_HOVERED = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");

    public PotionCharmToggleBtn(int x, int y, OnPress onPress) {
        super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        boolean off = PotionCharmMode.of(SuperbAmmoCache.getPotionCharmMode()).isOff();
        ResourceLocation tex = off ? SLOT_DISABLED : (isHovered ? SLOT_HOVERED : SLOT);
        g.blit(tex, getX(), getY(), 0, 0, 16, 16, 16, 16);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(getX() + 1, getY() + 1, 1);
        pose.scale(0.85f, 0.85f, 1);
        g.renderFakeItem(new ItemStack(Items.POTION), 0, 0);
        pose.popPose();
    }

    /** 按当前目标与解锁状态刷新 tooltip。 */
    public void updateTooltip() {
        PotionCharmMode mode = PotionCharmMode.of(SuperbAmmoCache.getPotionCharmMode());
        boolean mending = SuperbAmmoCache.getPotionCharmMendingUnlocked();
        Component tip = Component.empty()
                .append(Component.translatable("gui.beyond_integration.potion_charm").withStyle(ChatFormatting.GOLD))
                .append("\n")
                .append(Component.translatable("gui.beyond_integration.potion_charm.mode",
                        Component.translatable(mode.langKey())))
                .append("\n")
                .append(Component.translatable(mending
                        ? "gui.beyond_integration.potion_charm.mending.on"
                        : "gui.beyond_integration.potion_charm.mending.off"))
                .append("\n")
                .append(Component.translatable(mending
                        ? "gui.beyond_integration.potion_charm.hint.unlocked"
                        : "gui.beyond_integration.potion_charm.hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
        setTooltip(Tooltip.create(tip));
    }
}
