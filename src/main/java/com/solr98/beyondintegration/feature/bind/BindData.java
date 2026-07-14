package com.solr98.beyondintegration.feature.bind;

import java.util.Objects;
import java.util.UUID;

public class BindData {
    private final int netId;
    private final UUID token;
    private final UUID playerUuid;

    public BindData(int netId, UUID token, UUID playerUuid) {
        this.netId = netId;
        this.token = token;
        this.playerUuid = playerUuid;
    }

    public int netId() { return netId; }
    public UUID token() { return token; }
    public UUID playerUuid() { return playerUuid; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BindData b)) return false;
        return netId == b.netId && Objects.equals(token, b.token) && Objects.equals(playerUuid, b.playerUuid);
    }

    @Override
    public int hashCode() { return Objects.hash(netId, token, playerUuid); }

    @Override
    public String toString() {
        return "BindData{netId=" + netId + ", token=" + token + ", player=" + playerUuid + '}';
    }
}
