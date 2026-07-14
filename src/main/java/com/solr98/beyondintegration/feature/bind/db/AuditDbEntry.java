package com.solr98.beyondintegration.feature.bind.db;

import com.solr98.beyondintegration.feature.bind.AuditEntry;

public record AuditDbEntry(
        long timestamp,
        String action,
        String playerName,
        String playerUuid,
        int netId,
        String targetType,
        String targetInfo,
        boolean success,
        String detail
) {
    public static AuditDbEntry fromAuditEntry(AuditEntry e) {
        return new AuditDbEntry(
                e.timestamp(),
                e.action(),
                e.playerName(),
                e.playerUuid() != null ? e.playerUuid().toString() : null,
                e.netId(),
                e.targetType(),
                e.targetInfo(),
                e.success(),
                e.detail()
        );
    }
}
