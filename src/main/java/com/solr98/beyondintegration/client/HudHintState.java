package com.solr98.beyondintegration.client;

import net.minecraft.network.chat.Component;

/** 快捷栏上方临时提示文本的状态（由服务端 {@code HudHintPacket} 触发）。 */
public final class HudHintState {

    private static final long DURATION_MS = 5000L;

    private static Component message;
    private static long expireAt;

    private HudHintState() {}

    public static void show(String translationKey, long arg1, long arg2) {
        message = Component.translatable(translationKey, arg1, arg2);
        expireAt = System.currentTimeMillis() + DURATION_MS;
    }

    /** 当前应显示的提示（过期/无则返回 null）。 */
    public static Component current() {
        if (message == null || System.currentTimeMillis() > expireAt) return null;
        return message;
    }
}
