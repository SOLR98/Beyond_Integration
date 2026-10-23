package com.solr98.beyondintegration.mixin;

import com.Polarice3.Goety.config.MainConfig;
import com.solr98.beyondintegration.client.SoulEnergyState;
import com.solr98.beyondintegration.client.hud.HudElement;
import com.solr98.beyondintegration.client.hud.HudLayout;
import com.solr98.beyondintegration.client.hud.SoulChangePopups;
import com.solr98.beyondintegration.client.render.SoulHudRenderer;
import com.solr98.beyondintegration.feature.soul.SoulVanillaSource;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为 Goety 灵魂条 HUD 追加第三条网络灵魂条（1.21.1 LayeredDraw）。
 */
@Mixin(targets = "com.Polarice3.Goety.client.gui.overlay.SoulEnergyGui", remap = false)
public class SoulEnergyGuiMixin {

    @Inject(method = "drawHUD", at = @At("RETURN"), remap = false)
    private static void beyond$drawNetworkBar(GuiGraphics g, DeltaTracker deltaTracker, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        SoulChangePopups.trackNative(SoulVanillaSource.nativeSouls(mc.player));

        boolean show = MainConfig.SoulGuiShow.get()
                && mc.gameMode != null
                && mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
        int screenWidth = g.guiWidth();
        int screenHeight = g.guiHeight();
        int vanillaBarX = (screenWidth / 2) + MainConfig.SoulGuiHorizontal.get();
        if (show) {
            SoulChangePopups.renderNative(g, vanillaBarX + SoulHudRenderer.WIDTH / 2, screenHeight);
        }

        if (!SoulEnergyState.hasSource()) return;
        long amount = SoulEnergyState.getAmount();
        if (amount <= 0L) return;
        if (!show) return;

        int defY = (screenHeight + MainConfig.SoulGuiVertical.get()) - 9 - SoulHudRenderer.defaultRowOffset();
        int bx = HudLayout.resolveX(HudElement.SOUL, vanillaBarX, screenWidth);
        int by = HudLayout.resolveY(HudElement.SOUL, defY, screenHeight);

        SoulHudRenderer.render(g, bx, by, amount, SoulEnergyState.getMax(), deltaTracker.getGameTimeDeltaPartialTick(false));
        // 施法进度条：画在网络魂条同一行（覆盖条体，不上移）
        SoulHudRenderer.renderCastProgress(g, bx, by);
        SoulChangePopups.renderNetwork(g, bx, by);
    }
}
