package com.solr98.beyondintegration.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.solr98.beyondintegration.ClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 客户端快捷键管理。
 * <p>当前仅保留 HUD 位置可视化调整（工作站打开快捷键随自绘工作台界面一并移除，
 * 待 BD 0.8 工作台页面重做后再接回）。
 */
public final class BDKeyBindings {

    /** 按键所属分类（控制设置中显示） */
    public static final String CATEGORY = "key.categories.beyond_integration";

    /** 打开 HUD 位置可视化调整界面（初始未绑定） */
    public static final KeyMapping OPEN_SOUL_HUD_EDITOR = new KeyMapping(
            "key.beyond_integration.open_soul_hud_editor", InputConstants.UNKNOWN.getValue(), CATEGORY);

    private BDKeyBindings() {
    }

    /** 每客户端 tick 检测快捷键；关闭界面/无玩家时不处理。 */
    public static void handleTick() {
        openSoulHudEditorIfPressed();
    }

    /** 按下快捷键打开 HUD 位置可视化调整界面（受客户端配置 {@code hud_visual_edit} 控制）。 */
    private static void openSoulHudEditorIfPressed() {
        if (!OPEN_SOUL_HUD_EDITOR.consumeClick()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (ClientConfig.hudVisualEdit()) {
            mc.setScreen(new com.solr98.beyondintegration.client.gui.HudPositionScreen());
        } else {
            mc.player.displayClientMessage(
                    Component.translatable("message.beyond_integration.hud_pos.disabled"), true);
        }
    }
}
