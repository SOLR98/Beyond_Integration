package com.solr98.beyondintegration.mixin;

import com.Polarice3.Goety.config.MainConfig;
import com.solr98.beyondintegration.client.SoulEnergyState;
import com.solr98.beyondintegration.client.hud.HudElement;
import com.solr98.beyondintegration.client.hud.HudLayout;
import com.solr98.beyondintegration.client.hud.SoulChangePopups;
import com.solr98.beyondintegration.client.render.SoulHudRenderer;
import com.solr98.beyondintegration.feature.soul.SoulVanillaSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为原版灵魂条 HUD 追加<b>第三条独立灵魂条</b>（网络灵魂源），默认画在原版条上方一行
 * （Goety ≥ 2.5.59 自带增减弹字时再上移一行，留出弹字行）。
 * <p>同时驱动灵魂增减弹字：网络魂弹字始终由 BI 绘制；旧版 Goety 无原版弹字时也由 BI 补显示。
 * <p>条体渲染委托 {@link SoulHudRenderer}；位置由客户端配置决定
 * （{@link ClientConfig.SoulHudPosMode}：相对原版条偏移 / 绝对屏幕坐标），
 * 可用快捷键打开可视化拖动界面调整。
 * <p>客户端 Mixin（{@code client} 段注册）；{@code MixinPlugin} 按 {@code goety} 门控。
 */
@Mixin(targets = "com.Polarice3.Goety.client.gui.overlay.SoulEnergyGui", remap = false)
public class SoulEnergyGuiMixin {

    @Inject(method = "drawHUD", at = @At("RETURN"), remap = false)
    private static void beyond$drawNetworkBar(ForgeGui gui, GuiGraphics g, float partialTicks,
                                              int screenWidth, int screenHeight, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 观测原版魂量变化：旧版 Goety 没有自带弹字时由 BI 补显示
        SoulChangePopups.trackNative(SoulVanillaSource.nativeSouls(mc.player));

        boolean show = MainConfig.SoulGuiShow.get()
                && mc.gameMode != null
                && mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
        int vanillaBarX = (screenWidth / 2) + MainConfig.SoulGuiHorizontal.get();
        if (show) {
            SoulChangePopups.renderNative(g, vanillaBarX + SoulHudRenderer.WIDTH / 2, screenHeight);
        }

        if (!SoulEnergyState.hasSource()) return;
        long amount = SoulEnergyState.getAmount();
        if (amount <= 0L) return;
        if (!show) return;

        // 原版条上方一行；若 Goety ≥ 2.5.59 自带增减弹字，再多留一行避免重叠
        int defY = (screenHeight + MainConfig.SoulGuiVertical.get()) - 9 - SoulHudRenderer.defaultRowOffset();
        int bx = HudLayout.resolveX(HudElement.SOUL, vanillaBarX, screenWidth);
        int by = HudLayout.resolveY(HudElement.SOUL, defY, screenHeight);

        SoulHudRenderer.render(g, bx, by, amount, SoulEnergyState.getMax(), partialTicks);
        SoulChangePopups.renderNetwork(g, bx, by);
    }
}
