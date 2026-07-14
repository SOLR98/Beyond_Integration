package com.solr98.beyondintegration.feature.bind;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record AuditEntry(
        long timestamp,
        String action,
        String playerName,
        UUID playerUuid,
        int netId,
        String targetType,
        String targetInfo,
        boolean success,
        String detail
) {
    private static final String NULL = "-";

    public static AuditEntry bind(String playerName, UUID playerUuid, int netId, String targetType, String targetInfo) {
        return bind(playerName, playerUuid, netId, targetType, targetInfo, "");
    }

    public static AuditEntry bind(String playerName, UUID playerUuid, int netId, String targetType, String targetInfo, String detail) {
        return new AuditEntry(System.currentTimeMillis(), "BIND", playerName, playerUuid, netId, targetType, targetInfo, true, detail);
    }

    public static AuditEntry unbind(String playerName, UUID playerUuid, int netId, String targetType, String targetInfo) {
        return new AuditEntry(System.currentTimeMillis(), "UNBIND", playerName, playerUuid, netId, targetType, targetInfo, true, "");
    }

    public static AuditEntry reset(String playerName, UUID playerUuid, int netId) {
        return new AuditEntry(System.currentTimeMillis(), "RESET", playerName, playerUuid, netId, NULL, NULL, true, "由 " + playerName + " 手动重置");
    }

    public static AuditEntry deny(String playerName, UUID playerUuid, int netId, String targetType, String targetInfo, String reason) {
        return new AuditEntry(System.currentTimeMillis(), "DENY", playerName, playerUuid, netId, targetType, targetInfo, false, reason);
    }

    public static AuditEntry migrate(String playerName, UUID playerUuid, int netId, String targetType, String targetInfo) {
        return new AuditEntry(System.currentTimeMillis(), "MIGRATE", playerName, playerUuid, netId, targetType, targetInfo, true, "旧绑定已自动迁移");
    }

    public static AuditEntry meter(int netId, String targetType, String targetInfo, String detail) {
        return new AuditEntry(System.currentTimeMillis(), "METER", NULL, null, netId, targetType, targetInfo, true, detail);
    }

    public static AuditEntry autoBind(String playerName, UUID playerUuid, int netId, String targetType, String targetInfo) {
        return new AuditEntry(System.currentTimeMillis(), "AUTO_BIND", playerName, playerUuid, netId, targetType, targetInfo, true, "");
    }

    public static AuditEntry configChange(String playerName, UUID playerUuid, int netId, String targetType, String targetInfo, String detail) {
        return new AuditEntry(System.currentTimeMillis(), "CFG_CHG", playerName, playerUuid, netId, targetType, targetInfo, true, detail);
    }

    public String toJson() {
        return "{\"t\":" + timestamp
                + ",\"a\":\"" + action + '"'
                + ",\"p\":\"" + esc(playerName) + '"'
                + ",\"pu\":\"" + (playerUuid != null ? playerUuid.toString() : NULL) + '"'
                + ",\"n\":" + netId
                + ",\"tt\":\"" + targetType + '"'
                + ",\"ti\":\"" + esc(targetInfo) + '"'
                + ",\"s\":" + success
                + ",\"d\":\"" + esc(detail) + '"'
                + "}";
    }

    public static AuditEntry fromJson(String json) {
        try {
            String clean = json.trim();
            if (!clean.startsWith("{") || !clean.endsWith("}")) return null;
            long t = getLong(clean, "t");
            String a = getStr(clean, "a");
            String p = getStr(clean, "p");
            String pu = getStr(clean, "pu");
            int n = getInt(clean, "n");
            String tt = getStr(clean, "tt");
            String ti = getStr(clean, "ti");
            boolean s = getBool(clean, "s");
            String d = getStr(clean, "d");
            UUID uuid = pu.equals(NULL) ? null : UUID.fromString(pu);
            return new AuditEntry(t, a, p, uuid, n, tt, ti, s, d);
        } catch (Exception e) {
            return null;
        }
    }

    private static String esc(String s) {
        if (s == null || s.equals(NULL)) return NULL;
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static long getLong(String json, String key) {
        int idx = json.indexOf('"' + key + '"');
        if (idx < 0) return 0;
        int colon = json.indexOf(':', idx);
        int end = findEnd(json, colon + 1, false);
        return Long.parseLong(json.substring(colon + 1, end).trim());
    }

    private static int getInt(String json, String key) {
        return (int) getLong(json, key);
    }

    private static boolean getBool(String json, String key) {
        int idx = json.indexOf('"' + key + '"');
        if (idx < 0) return false;
        int colon = json.indexOf(':', idx);
        int end = findEnd(json, colon + 1, false);
        return Boolean.parseBoolean(json.substring(colon + 1, end).trim());
    }

    private static String getStr(String json, String key) {
        int idx = json.indexOf('"' + key + '"');
        if (idx < 0) return NULL;
        int colon = json.indexOf(':', idx);
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return NULL;
        int end = findEnd(json, start + 1, true);
        if (end < 0) return NULL;
        String raw = json.substring(start + 1, end);
        return raw.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static int findEnd(String json, int from, boolean inString) {
        if (inString) {
            for (int i = from; i < json.length(); i++) {
                if (json.charAt(i) == '\\') i++;
                else if (json.charAt(i) == '"') return i;
            }
            return -1;
        }
        for (int i = from; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']') return i;
        }
        return json.length();
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("t", timestamp);
        tag.putString("a", action);
        tag.putString("p", playerName);
        if (playerUuid != null) tag.putUUID("pu", playerUuid);
        tag.putInt("n", netId);
        tag.putString("tt", targetType);
        tag.putString("ti", targetInfo);
        tag.putBoolean("s", success);
        tag.putString("d", detail);
        return tag;
    }

    public static AuditEntry fromNbt(CompoundTag tag) {
        UUID uuid = tag.contains("pu") ? tag.getUUID("pu") : null;
        return new AuditEntry(
                tag.getLong("t"),
                tag.getString("a"),
                tag.getString("p"),
                uuid,
                tag.getInt("n"),
                tag.getString("tt"),
                tag.getString("ti"),
                tag.getBoolean("s"),
                tag.getString("d")
        );
    }
}
