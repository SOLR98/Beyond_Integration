package com.solr98.beyondintegration.command.util;

import com.solr98.beyondintegration.command.CommandLang;

public class AuditFormat {

    /** Translate action name (BIND → 绑定) */
    public static String action(String enAction) {
        return CommandLang.get("action." + enAction);
    }

    /** Translate target type (BLOCK → 方块) */
    public static String targetType(String enType) {
        if (enType == null || enType.isEmpty() || "-".equals(enType)) return "";
        return CommandLang.get("target." + enType);
    }

    /** Format by-line: "by Steve" or "由 Steve" */
    public static String by(String playerName) {
        return CommandLang.get("ui.by") + " " + playerName;
    }

    /** Format success indicator */
    public static String status(boolean success) {
        return success ? "§a✓" : "§c✗";
    }

    /** Format timestamp */
    public static String time(long epochMs) {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(epochMs));
    }

    /** Format target appendix */
    public static String target(String type, String info) {
        if (type == null || "-".equals(type)) return "";
        if (info == null || "-".equals(info)) return "";
        return " §7[" + targetType(type) + " " + info + "]";
    }

    /** Format target appendix with detail (entity name etc.) */
    public static String targetFull(String type, String info, String detail) {
        if (type == null || "-".equals(type)) return "";
        String base = info != null && !"-".equals(info) ? info : "";
        String suffix = "";
        if (detail != null && !detail.isEmpty()) {
            suffix = " §8(" + detail + ")";
        }
        if (base.isEmpty() && suffix.isEmpty()) return "";
        return " §7[" + targetType(type) + (base.isEmpty() ? "" : " " + base) + "]" + suffix;
    }
}
