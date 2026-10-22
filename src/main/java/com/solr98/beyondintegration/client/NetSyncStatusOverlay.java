package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.ClientConfig;
import com.solr98.beyondintegration.client.hud.HudElement;
import com.solr98.beyondintegration.client.hud.HudLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/**
 * 快捷栏上方的「主网络同步状态」HUD。
 * <p>显示：同步范围、连接状态、网络 ID、镜像版本号。
 * 仅在客户端开关开启且主网络同步启用（{@code SyncConfigClient.isActive()}）时显示。
 */
public class NetSyncStatusOverlay implements LayeredDraw.Layer {

    private static final int COLOR = 0xFF55FFFF;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        if (!ClientConfig.showNetSyncStatus()) return;
        if (!SyncConfigClient.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        PrimaryNetClientStorage mirror = PrimaryNetClientStorage.INSTANCE;
        String scope = SyncConfigClient.scope().name();
        Component text;
        if (mirror.hasNetwork()) {
            text = Component.translatable("hud.beyond_integration.net_sync.connected",
                    scope, mirror.netId(), mirror.version());
        } else {
            text = Component.translatable("hud.beyond_integration.net_sync.waiting", scope);
        }

        var font = mc.font;
        String s = text.getString();
        int screenWidth = g.guiWidth();
        int screenHeight = g.guiHeight();
        int defX = (screenWidth - font.width(s)) / 2;
        int defY = screenHeight - 45;
        int x = HudLayout.resolveX(HudElement.NET_SYNC, defX, screenWidth);
        int y = HudLayout.resolveY(HudElement.NET_SYNC, defY, screenHeight);
        g.drawString(font, s, x, y, COLOR, true);
    }
}
