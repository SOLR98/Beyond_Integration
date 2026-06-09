package com.solr98.beyondintegration.handler;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NetworkNotificationTracker {

    private static final Set<UUID> notifiedPlayers = new HashSet<>();

    public static boolean tryNotify(UUID playerUuid) {
        return notifiedPlayers.add(playerUuid);
    }

    public static void clear(UUID playerUuid) {
        notifiedPlayers.remove(playerUuid);
    }

    public static void clearAll() {
        notifiedPlayers.clear();
    }
}
