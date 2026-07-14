package com.solr98.beyondintegration.feature.bind;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.db.DatabaseBackend;
import com.solr98.beyondintegration.feature.bind.db.SqliteBackend;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class BindingAuditLog {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RING_BUFFER_SIZE = 5000;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    private static BindingAuditLog instance;

    private final Path logDir;
    private BufferedWriter writer;
    private long fileSize = 0;
    private int rotationIndex = 0;

    private final ConcurrentLinkedDeque<AuditEntry> ringBuffer = new ConcurrentLinkedDeque<>();
    private final Map<Integer, List<AuditEntry>> indexByNet = new ConcurrentHashMap<>();

    private DatabaseBackend dbBackend;

    private BindingAuditLog(ServerLevel level, Path worldDir) {
        this.logDir = worldDir.resolve("data").resolve("beyond_integration");
        try {
            Files.createDirectories(logDir);

            String storage = CommandConfig.auditStorage().toUpperCase();
            switch (storage) {
                case "SQLITE" -> {
                    String dbFile = CommandConfig.auditSqlitePath();
                    Path dbPath = dbFile.contains(":") || dbFile.startsWith("/")
                            ? Path.of(dbFile) : logDir.resolve(dbFile);
                    dbBackend = new SqliteBackend(dbPath);
                    dbBackend.init();
                }
                case "MYSQL" -> {
                    String url = CommandConfig.auditMysqlUrl();
                    if (!url.isEmpty()) {
                        dbBackend = new com.solr98.beyondintegration.feature.bind.db.MysqlBackend(
                                url, CommandConfig.auditMysqlUser(), CommandConfig.auditMysqlPassword());
                        dbBackend.init();
                    }
                }
                default -> {} // JSONL only
            }

            Path logPath = logDir.resolve("audit.log");
            if (storage.equals("JSONL") || storage.isEmpty()) {
                loadFromJsonl(logPath);
                this.writer = Files.newBufferedWriter(logPath, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                if (Files.exists(logPath)) this.fileSize = Files.size(logPath);
            }

            LOGGER.info("[BindingAuditLog] Initialized, storage={}, db={}", storage, dbBackend != null ? "yes" : "no");
        } catch (IOException e) {
            LOGGER.error("[BindingAuditLog] Failed to open log file", e);
        }
    }

    public static void initialize(ServerLevel level) {
        if (instance != null) return;
        instance = new BindingAuditLog(level, level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
    }

    public static BindingAuditLog getInstance() { return instance; }

    private synchronized void writeEntry(AuditEntry entry) {
        // JSONL file
        if (writer != null) {
            String json = entry.toJson();
            try {
                checkRotate();
                writer.write(json);
                writer.newLine();
                writer.flush();
                fileSize += json.length() + 1;
            } catch (IOException e) {
                LOGGER.error("[BindingAuditLog] Failed to write log entry", e);
            }
        }

        // Database (each backend checks conn internally)
        if (dbBackend != null) {
            dbBackend.log(entry);
        }

        // Ring buffer
        ringBuffer.addLast(entry);
        if (ringBuffer.size() > RING_BUFFER_SIZE) ringBuffer.pollFirst();
        indexByNet.computeIfAbsent(entry.netId(), k -> Collections.synchronizedList(new ArrayList<>())).add(entry);
    }

    public static void log(AuditEntry entry) {
        if (instance != null) instance.writeEntry(entry);
    }

    public static void log(Action action, String playerName, java.util.UUID playerUuid, int netId,
                           String targetType, String targetInfo, boolean success, String detail) {
        if (instance == null) return;
        AuditEntry entry = new AuditEntry(System.currentTimeMillis(), action.name(),
                playerName, playerUuid, netId, targetType, targetInfo, success, detail);
        instance.log(entry);
    }

    public static void logBatch(List<AuditEntry> entries) {
        if (instance == null || entries.isEmpty()) return;
        instance.writeBatch(entries);
    }

    private synchronized void writeBatch(List<AuditEntry> entries) {
        // JSONL file
        if (writer != null) {
            try {
                for (AuditEntry entry : entries) {
                    checkRotate();
                    String json = entry.toJson();
                    writer.write(json);
                    writer.newLine();
                    fileSize += json.length() + 1;
                }
                writer.flush();
            } catch (IOException e) {
                LOGGER.error("[BindingAuditLog] Failed to write batch log entries", e);
            }
        }

        // Database
        if (dbBackend != null) {
            for (AuditEntry entry : entries) {
                dbBackend.log(entry);
            }
        }

        // Ring buffer
        for (AuditEntry entry : entries) {
            ringBuffer.addLast(entry);
            if (ringBuffer.size() > RING_BUFFER_SIZE) ringBuffer.pollFirst();
            indexByNet.computeIfAbsent(entry.netId(), k -> Collections.synchronizedList(new ArrayList<>())).add(entry);
        }
    }

    public List<AuditEntry> queryByNet(int netId, int page, int pageSize) {
        if (dbBackend != null) return dbBackend.queryByNet(netId, page, pageSize);
        List<AuditEntry> entries = indexByNet.get(netId);
        if (entries == null || entries.isEmpty()) return List.of();
        int from = Math.max(0, entries.size() - (page + 1) * pageSize);
        int to = Math.max(0, entries.size() - page * pageSize);
        if (from >= to) return List.of();
        List<AuditEntry> result = new ArrayList<>(entries.subList(from, to));
        Collections.reverse(result);
        return result;
    }

    public List<AuditEntry> queryByPlayer(String playerName, int page, int pageSize) {
        if (dbBackend != null) return dbBackend.queryByPlayer(playerName, page, pageSize);
        List<AuditEntry> result = new ArrayList<>();
        for (AuditEntry e : ringBuffer) {
            if (playerName.equals(e.playerName())) result.add(e);
        }
        Collections.reverse(result);
        int from = page * pageSize;
        int to = Math.min(from + pageSize, result.size());
        if (from >= result.size()) return List.of();
        return result.subList(from, to);
    }

    public List<AuditEntry> queryRecent(int count) {
        if (dbBackend != null) return dbBackend.queryRecent(count);
        List<AuditEntry> result = new ArrayList<>(ringBuffer);
        Collections.reverse(result);
        return result.subList(0, Math.min(count, result.size()));
    }

    public int countByNet(int netId) {
        if (dbBackend != null) return dbBackend.countByNet(netId);
        List<AuditEntry> entries = indexByNet.get(netId);
        return entries != null ? entries.size() : 0;
    }

    private void checkRotate() throws IOException {
        if (fileSize < MAX_FILE_SIZE) return;
        writer.close();
        rotationIndex++;
        Path rotated = logDir.resolve("audit." + rotationIndex + ".log");
        Files.move(logDir.resolve("audit.log"), rotated);
        writer = Files.newBufferedWriter(logDir.resolve("audit.log"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        fileSize = 0;
        int maxArchives = 5;
        int oldestKept = rotationIndex - maxArchives;
        for (int i = 1; i <= oldestKept; i++) {
            Path old = logDir.resolve("audit." + i + ".log");
            if (Files.exists(old)) Files.delete(old);
        }
    }

    private void loadFromJsonl(Path logPath) {
        if (!Files.exists(logPath)) return;
        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                AuditEntry entry = AuditEntry.fromJson(line);
                if (entry != null) {
                    ringBuffer.addLast(entry);
                    if (ringBuffer.size() > RING_BUFFER_SIZE) ringBuffer.pollFirst();
                    indexByNet.computeIfAbsent(entry.netId(), k -> Collections.synchronizedList(new ArrayList<>())).add(entry);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[BindingAuditLog] Failed to load log file", e);
        }
    }

    public static void flush() {
        if (instance == null) return;
        if (instance.writer != null) {
            try { instance.writer.flush(); } catch (IOException ignored) {}
        }
    }

    public static void close() {
        if (instance == null) return;
        if (instance.writer != null) {
            try { instance.writer.close(); } catch (IOException ignored) {}
        }
        if (instance.dbBackend != null) {
            try { instance.dbBackend.close(); } catch (Exception ignored) {}
        }
        instance = null;
    }

    public enum Action {
        BIND, UNBIND, AUTO_BIND, RESET, DENY, MIGRATE, METER, CFG_CHG, MEMBER_ADD, MEMBER_REMOVE, OP_INTERVENE,
        GUI_INSERT, GUI_EXTRACT, GUI_CRAFT,
        NET_CREATE, NET_DESTROY, NET_MERGE,
        METER_SNAPSHOT
    }
}
