package com.solr98.beyondintegration.feature.bind.db;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.feature.bind.AuditEntry;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DatabaseAuditBackend implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final DatabaseManager dbManager;
    private final DatabaseConfig config;
    private final List<AuditDbEntry> batch = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ScheduledExecutorService scheduler;

    public DatabaseAuditBackend(DatabaseConfig config) {
        this.config = config;
        this.dbManager = new DatabaseManager(config);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BD-AuditDB-Flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush, config.flushIntervalMs(), config.flushIntervalMs(), TimeUnit.MILLISECONDS);
        LOGGER.info("[DB] Database audit backend started, flush interval={}ms, batchSize={}",
                config.flushIntervalMs(), config.batchSize());
    }

    public synchronized void log(AuditEntry entry) {
        if (!running.get()) return;
        batch.add(AuditDbEntry.fromAuditEntry(entry));
        if (batch.size() >= config.batchSize()) {
            flushInternal();
        }
    }

    public synchronized void flush() {
        if (running.get()) flushInternal();
    }

    private void flushInternal() {
        if (batch.isEmpty()) return;
        List<AuditDbEntry> toInsert = new ArrayList<>(batch);
        batch.clear();

        String sql = "INSERT INTO " + config.tableName()
                + " (timestamp, action, player_name, player_uuid, net_id, target_type, target_info, success, detail)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeBatch(sql, toInsert);
    }

    @Override
    public void close() {
        running.set(false);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        flush();
        dbManager.closeQuietly();
        LOGGER.info("[DB] Database audit backend closed");
    }

    public boolean isConnected() {
        try {
            return dbManager.getConnection() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
