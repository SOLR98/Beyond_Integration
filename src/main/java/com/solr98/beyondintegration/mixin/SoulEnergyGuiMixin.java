package com.solr98.beyondintegration.mixin;

import com.Polarice3.Goety.config.MainConfig;
import com.Polarice3.Goety.utils.SEHelper;
import com.solr98.beyondintegration.client.SoulEnergyState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在原版灵魂条上叠加"网络魂"段（方案 B，青色）：
 * 原版条（含由 {@code SEUpdatePacketMixin} 并入的网络魂）保持原色，网络魂占比那段用青色高亮。
 * <p>客户端 Mixin（{@code client} 段注册）；{@code MixinPlugin} 按 {@code goety} 门控。
 */
@Mixin(targets = "com.Polarice3.Goety.client.gui.overlay.SoulEnergyGui", remap = false)
public class SoulEnergyGuiMixin {

    /** 网络魂段颜色（青，约 80% 不透明） */
    private static final int NET_COLOR = 0xCC8AD8FF;

    @Inject(method = "drawHUD", at = @At("TAIL"), remap = false)
    private static void beyond$drawNetworkSegment(ForgeGui gui, GuiGraphics g, float partialTicks,
                                                  int screenWidth, int screenHeight, CallbackInfo ci) {
        if (!SoulEnergyState.hasSource()) return;
        int net = SoulEnergyState.getAmount();
        if (net <= 0) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int total = MainConfig.MaxArcaSouls.get();
        if (total <= 0) return;

        // 原版条读数已含网络魂（并入 SE tag）；原生 = 合并值 - 网络魂
        int merged = SEHelper.getSESouls(mc.player);
        int nativeSouls = Math.max(0, merged - net);

        int i = (screenWidth / 2) + MainConfig.SoulGuiHorizontal.get();
        int height = screenHeight + MainConfig.SoulGuiVertical.get();

        int nativeLen = (int) (117 * (nativeSouls / (double) total));
        int netLen = (int) (117 * (net / (double) total));
        int x0 = i + 9 + nativeLen;
        int w = Math.min(netLen, Math.max(0, 117 - nativeLen));
        if (w > 0) {
            g.fill(x0, height - 7, x0 + w, height - 7 + 5, NET_COLOR);
        }
    }
}
