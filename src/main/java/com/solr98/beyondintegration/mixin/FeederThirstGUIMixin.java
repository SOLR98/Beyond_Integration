package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.widget.FeederRegenButton;
import com.solr98.beyondintegration.client.widget.FeederThirstModeButton;
import com.solr98.beyondintegration.feature.feeder.FeederRegenMode;
import com.solr98.beyondintegration.feature.feeder.FeederThirstMode;
import com.solr98.beyondintegration.feature.feeder.FeederThirstSettings;
import com.solr98.beyondintegration.feature.feeder.ThirstBridge;
import com.wintercogs.beyonddimensions.client.gui.NetFeederGUI;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.menu.NetFeederMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 喂食器界面扩展：在 BD 喂食档位按钮下方追加「补水档位」按钮（同款贴图），
 * 并在两个按钮右下角分别叠加原版饥饿图标 / 原版水瓶角标。
 * 角标在 {@code renderLabels}（widget 之后）绘制，确保覆盖在按钮之上。
 * 仅在 {@code thirst} 已加载时由 {@code MixinPlugin} 应用。
 */
@Mixin(value = NetFeederGUI.class, remap = false)
public abstract class FeederThirstGUIMixin
{
    /** 反射缓存 Screen#addRenderableWidget（Mixin 0.8.5 不支持 @Shadow 继承方法） */
    @Unique private static java.lang.reflect.Method beyondintegration$addWidgetMethod;

    /** 原版饥饿图标 sprite（1.21+ HUD sprite）；水瓶角标用原版药水瓶物品渲染 */
    @Unique private static final ResourceLocation beyondintegration$FOOD_ICON =
            ResourceLocation.tryBuild("minecraft", "hud/food_full");

    @Unique private RightTabButton beyondintegration$thirstButton;
    @Unique private RightTabButton beyondintegration$regenButton;

    @Inject(method = "init", at = @At("RETURN"))
    private void beyondintegration$addThirstButton(CallbackInfo ci)
    {
        if (!ThirstBridge.get().loaded()) return;

        var self = (NetFeederGUI) (Object) this;
        NetFeederMenu menu = self.getMenu();

        int x = self.getGuiLeft() + 176;
        int iconX = x + 3;
        int thirstY = self.getGuiTop() + 66;

        beyondintegration$thirstButton = new FeederThirstModeButton(x, thirstY, iconX, thirstY + 4, b ->
        {
            beyondintegration$thirstButton.toggleState();
            FeederThirstSettings.setThirstMode(menu.menuStack,
                    (FeederThirstMode) beyondintegration$thirstButton.currentState);
            menu.writeAndSendQuickData();
        });
        beyondintegration$thirstButton.setState(FeederThirstSettings.getThirstMode(menu.menuStack));
        beyondintegration$addWidget(beyondintegration$thirstButton);

        int regenY = self.getGuiTop() + 96;
        beyondintegration$regenButton = new FeederRegenButton(x, regenY, b ->
        {
            beyondintegration$regenButton.toggleState();
            FeederThirstSettings.setRegenMode(menu.menuStack,
                    beyondintegration$regenButton.currentState == FeederRegenMode.ON);
            menu.writeAndSendQuickData();
        });
        beyondintegration$regenButton.setState(FeederThirstSettings.isRegenMode(menu.menuStack)
                ? FeederRegenMode.ON : FeederRegenMode.OFF);
        beyondintegration$addWidget(beyondintegration$regenButton);
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void beyondintegration$syncThirstButton(CallbackInfo ci)
    {
        NetFeederMenu menu = ((NetFeederGUI) (Object) this).getMenu();

        if (beyondintegration$thirstButton != null)
        {
            FeederThirstMode mode = FeederThirstSettings.getThirstMode(menu.menuStack);
            if (beyondintegration$thirstButton.currentState != mode)
                beyondintegration$thirstButton.setState(mode);
        }

        if (beyondintegration$regenButton != null)
        {
            FeederRegenMode regen = FeederThirstSettings.isRegenMode(menu.menuStack)
                    ? FeederRegenMode.ON : FeederRegenMode.OFF;
            if (beyondintegration$regenButton.currentState != regen)
                beyondintegration$regenButton.setState(regen);
        }
    }

    /** 在 widget 之后（renderLabels 阶段）叠加原版角标 */
    @Inject(method = "renderLabels", at = @At("TAIL"))
    private void beyondintegration$drawModeBadges(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci)
    {
        var self = (NetFeederGUI) (Object) this;
        int bx = self.getGuiLeft() + 176 + 3 + 7;

        // 喂食档位按钮：原版饥饿图标 sprite
        guiGraphics.blitSprite(beyondintegration$FOOD_ICON, bx, self.getGuiTop() + 6 + 4 + 7, 9, 9);

        // 补水档位按钮：原版水瓶（药水瓶物品，缩放到 8x8）
        if (beyondintegration$thirstButton != null)
        {
            var pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(bx, self.getGuiTop() + 66 + 4 + 7, 0);
            pose.scale(0.5f, 0.5f, 1.0f);
            guiGraphics.renderFakeItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION), 0, 0);
            pose.popPose();
        }
    }

    /** 反射调用 Screen#addRenderableWidget 注册按钮（渲染/点击/tooltip 交给 widget 体系） */
    @Unique
    private void beyondintegration$addWidget(GuiEventListener widget)
    {
        try
        {
            if (beyondintegration$addWidgetMethod == null)
            {
                beyondintegration$addWidgetMethod = net.minecraft.client.gui.screens.Screen.class.getDeclaredMethod(
                        "addRenderableWidget", GuiEventListener.class);
            }
            beyondintegration$addWidgetMethod.setAccessible(true);
            beyondintegration$addWidgetMethod.invoke(this, widget);
        }
        catch (Throwable ignored)
        {
        }
    }
}
