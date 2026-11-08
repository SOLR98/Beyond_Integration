package com.solr98.beyondintegration.client.hud;

import com.solr98.beyondintegration.client.render.SoulHudRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 灵魂「增减弹字」：模仿 Goety ≥ 2.5.59 的 {@code SoulChangePopup}。
 * <ul>
 *   <li><b>网络魂</b>：始终由 BI 绘制，显示在网络灵魂条上方；</li>
 *   <li><b>原版魂</b>：Goety 新版已有自带弹字时交给它；旧版则由 BI 在原版条上方补显示。</li>
 * </ul>
 * <p>基于墙钟计时，不依赖客户端 tick；同号变化在短时间内合并。
 */
public final class SoulChangePopups {

    private SoulChangePopups() {
    }

    /** 单个弹字槽（网络 / 原版各一个，互不覆盖）。 */
    private static final class Popup {
        private static final long DURATION_MS = 2000L;
        private static final long MERGE_WINDOW_MS = 500L;
        private static final float RISE = 8.0F;

        private long amount;
        private long startMs;

        void pushDelta(long previous, long current) {
            long change = current - previous;
            if (change == 0L) return;
            long now = Util.getMillis();
            boolean merge = isActive(now)
                    && now - startMs <= MERGE_WINDOW_MS
                    && Long.signum(change) == Long.signum(amount);
            amount = merge ? amount + change : change;
            startMs = now;
        }

        void clear() {
            amount = 0L;
            startMs = 0L;
        }

        boolean isActive(long now) {
            return amount != 0L && now - startMs < DURATION_MS;
        }

        void render(GuiGraphics g, int centerX, int y) {
            long now = Util.getMillis();
            if (!isActive(now)) return;

            var font = Minecraft.getInstance().font;
            String text = (amount > 0L ? "+" : "") + amount;
            float progress = Mth.clamp((now - startMs) / (float) DURATION_MS, 0.0F, 1.0F);
            float alpha = progress < 0.6F ? 1.0F : 1.0F - ((progress - 0.6F) / 0.4F);
            int alphaBits = Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24;
            if (alphaBits == 0) return;

            int color = amount > 0L ? 0x3AE0F0 : 0x288F85;
            int x = centerX - font.width(text) / 2;

            g.pose().pushPose();
            g.pose().translate(0.0F, -progress * RISE, 0.0F);
            g.drawString(font, text, x, y, alphaBits | color, true);
            g.flush();
            g.pose().popPose();
        }
    }

    private static final Popup NETWORK = new Popup();
    private static final Popup NATIVE = new Popup();
    /** 上次观测到的原版魂量；{@code -1} = 尚未初始化（避免登录假弹字）。 */
    private static long lastNative = -1L;

    /** 网络魂变化（由 {@code SoulEnergyState.set} 计算差值）。 */
    public static void pushNetworkDelta(long previous, long current) {
        NETWORK.pushDelta(previous, current);
    }

    public static void clearNetwork() {
        NETWORK.clear();
    }

    public static void clearNative() {
        NATIVE.clear();
        lastNative = -1L;
    }

    /** 重置全部状态（断线/换角色）。 */
    public static void clear() {
        clearNetwork();
        clearNative();
    }

    /** 客户端每帧观测原版魂量变化；仅当 Goety 旧版没有自带弹字时由 BI 补显示。 */
    public static void trackNative(long current) {
        if (lastNative < 0L) {
            lastNative = current;
            return;
        }
        if (current == lastNative) return;
        if (!SoulHudRenderer.goetyHasSoulPopup()) {
            NATIVE.pushDelta(lastNative, current);
        }
        lastNative = current;
    }

    /** 网络魂弹字：画在网络灵魂条上方一行。 */
    public static void renderNetwork(GuiGraphics g, int barX, int barY) {
        NETWORK.render(g, barX + SoulHudRenderer.WIDTH / 2, barY - 10);
    }

    /** 原版魂弹字（仅旧版 Goety）：位置对齐原版弹字（原版条上方一行）。 */
    public static void renderNative(GuiGraphics g, int centerX, int screenHeight) {
        NATIVE.render(g, centerX, screenHeight - 20);
    }
}
