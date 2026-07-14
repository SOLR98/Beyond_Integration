package com.solr98.beyondintegration.feature.bind.db;

import com.solr98.beyondintegration.feature.bind.AuditEntry;

import java.util.List;

public interface DatabaseBackend extends AutoCloseable {
    void init();
    void log(AuditEntry entry);
    void flush();
    List<AuditEntry> queryByNet(int netId, int page, int pageSize);
    List<AuditEntry> queryByPlayer(String playerName, int page, int pageSize);
    List<AuditEntry> queryRecent(int count);
    int countByNet(int netId);
    void close();
}
