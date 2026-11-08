package com.solr98.beyondintegration.client.hud;

import com.solr98.beyondintegration.ClientConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用 HUD 布局：以客户端配置 {@code hud_layout}（字符串列表）持久化各元素的位置。
 * <p>单项格式：
 * <ul>
 *   <li>{@code <id>=RATIO:<x>:<y>}：相对当前屏幕尺寸的比例（{@code x/y} 为 0..10000 的万分比）；
 *       编辑器保存时使用该模式，窗口缩放后按比例跟随；</li>
 *   <li>{@code <id>=RELATIVE:<dx>:<dy>}：相对元素默认位置的像素偏移（旧格式，兼容读取）；</li>
 *   <li>{@code <id>=ABSOLUTE:<x>:<y>}：屏幕绝对坐标（旧格式，兼容读取）。</li>
 * </ul>
 * 无该项 = 使用各 HUD 自身的默认位置。编辑器（{@code HudPositionScreen}）统一写入 {@code RATIO}。
 */
public final class HudLayout {

    /** 比例基准：10000 = 100%（万分比，保留约 1/100 像素精度）。 */
    public static final int RATIO_BASE = 10000;

    public enum Mode { RATIO, RELATIVE, ABSOLUTE }

    /** 单项布局：模式 + 坐标（RELATIVE 为像素偏移；RATIO 为万分比）。 */
    public record Entry(Mode mode, int x, int y) {
    }

    private record Parsed(String id, Entry entry) {
    }

    private HudLayout() {
    }

    /** 取某元素的布局项；无返回 null（用默认）。 */
    public static Entry get(HudElement e) {
        for (String s : ClientConfig.hudLayout()) {
            Parsed p = parse(s);
            if (p != null && p.id().equals(e.id())) return p.entry();
        }
        return null;
    }

    /** 某元素的布局模式；无布局返回 null（默认位置）。 */
    public static Mode mode(HudElement e) {
        Entry en = get(e);
        return en == null ? null : en.mode();
    }

    /** 某元素是否为绝对模式。 */
    public static boolean isAbsolute(HudElement e) {
        return mode(e) == Mode.ABSOLUTE;
    }

    /** 实际 X：RATIO 按屏幕宽度换算，ABSOLUTE 用绝对坐标，RELATIVE = 默认 X + 偏移。 */
    public static int resolveX(HudElement e, int defX, int screenWidth) {
        try {
            Entry en = get(e);
            if (en == null) return defX;
            return switch (en.mode()) {
                case ABSOLUTE -> en.x();
                case RATIO -> ratioToPixel(en.x(), screenWidth);
                case RELATIVE -> defX + en.x();
            };
        } catch (Throwable ignored) {
            // 配置值无法识别/解析异常：回退默认位置
            return defX;
        }
    }

    /** 实际 Y：RATIO 按屏幕高度换算，ABSOLUTE 用绝对坐标，RELATIVE = 默认 Y + 偏移。 */
    public static int resolveY(HudElement e, int defY, int screenHeight) {
        try {
            Entry en = get(e);
            if (en == null) return defY;
            return switch (en.mode()) {
                case ABSOLUTE -> en.y();
                case RATIO -> ratioToPixel(en.y(), screenHeight);
                case RELATIVE -> defY + en.y();
            };
        } catch (Throwable ignored) {
            // 配置值无法识别/解析异常：回退默认位置
            return defY;
        }
    }

    /** 屏幕像素 → 万分比（0..10000）。 */
    public static int pixelToRatio(int pixel, int screenSize) {
        if (screenSize <= 0) return 0;
        long v = Math.round(pixel * (double) RATIO_BASE / screenSize);
        return (int) Math.max(0L, Math.min(RATIO_BASE, v));
    }

    /** 万分比 → 屏幕像素。 */
    public static int ratioToPixel(int ratio, int screenSize) {
        if (screenSize <= 0) return 0;
        return (int) Math.round(Math.max(0, Math.min(RATIO_BASE, ratio))
                * (double) screenSize / RATIO_BASE);
    }

    public static void setRatio(HudElement e, int xRatio, int yRatio) {
        put(e, new Entry(Mode.RATIO, clampRatio(xRatio), clampRatio(yRatio)));
    }

    public static void setAbsolute(HudElement e, int x, int y) {
        put(e, new Entry(Mode.ABSOLUTE, Math.max(0, x), Math.max(0, y)));
    }

    public static void setRelative(HudElement e, int dx, int dy) {
        put(e, new Entry(Mode.RELATIVE, dx, dy));
    }

    /** 清除某元素布局（回到默认）。 */
    public static void clear(HudElement e) {
        put(e, null);
    }

    private static int clampRatio(int ratio) {
        return Math.max(0, Math.min(RATIO_BASE, ratio));
    }

    private static void put(HudElement e, Entry en) {
        List<String> out = new ArrayList<>();
        for (String s : ClientConfig.hudLayout()) {
            Parsed p = parse(s);
            if (p != null && p.id().equals(e.id())) continue;
            // 同时清理同 id 但格式损坏的旧项，避免无效值残留
            if (s != null) {
                int eq = s.indexOf('=');
                if (eq > 0 && s.substring(0, eq).trim().equals(e.id())) continue;
            }
            out.add(s);
        }
        if (en != null) {
            out.add(e.id() + "=" + en.mode().name() + ":" + en.x() + ":" + en.y());
        }
        ClientConfig.setHudLayout(out);
    }

    private static Parsed parse(String s) {
        if (s == null) return null;
        int eq = s.indexOf('=');
        if (eq <= 0) return null;
        String id = s.substring(0, eq).trim();
        if (id.isEmpty()) return null;
        String[] p = s.substring(eq + 1).split(":");
        if (p.length < 3) return null;
        try {
            Mode mode = Mode.valueOf(p[0].trim().toUpperCase());
            int x = Integer.parseInt(p[1].trim());
            int y = Integer.parseInt(p[2].trim());
            if (mode == Mode.RATIO) {
                // RATIO 必须为合法万分比；越界/异常视为无法识别 → 回退默认
                if (x < 0 || x > RATIO_BASE || y < 0 || y > RATIO_BASE) return null;
            } else {
                // 旧格式容错：异常大的坐标视为无法识别
                if (Math.abs((long) x) > 1_000_000L || Math.abs((long) y) > 1_000_000L) return null;
            }
            return new Parsed(id, new Entry(mode, x, y));
        } catch (Exception ex) {
            return null;
        }
    }
}
