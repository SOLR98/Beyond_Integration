package com.solr98.beyondintegration.feature.bind.db;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.feature.bind.AuditEntry;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SqliteBackend implements DatabaseBackend {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PAGE_SIZE = 10;

    private final Path dbPath;
    private Connection conn;
    private ExecutorService writer;

    public SqliteBackend(Path dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public void init() {
        try {
            dbPath.getParent().toFile().mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
            conn.createStatement().execute("PRAGMA journal_mode=WAL");
            conn.createStatement().execute("PRAGMA synchronous=NORMAL");
            ensureSchema();
            writer = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "bd-sqlite-writer");
                t.setDaemon(true);
                return t;
            });
            LOGGER.info("[SQLite] Audit database initialized at {}", dbPath);
        } catch (SQLException e) {
            LOGGER.error("[SQLite] Failed to initialize database", e);
        }
    }

    private void ensureSchema() throws SQLException {
        conn.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS audit_log (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  time_ms BIGINT NOT NULL," +
            "  action VARCHAR(32) NOT NULL," +
            "  player VARCHAR(64)," +
            "  uuid VARCHAR(36)," +
            "  net_id INT NOT NULL," +
            "  target_type VARCHAR(32)," +
            "  target_info VARCHAR(512)," +
            "  success BOOLEAN NOT NULL DEFAULT 1," +
            "  detail TEXT" +
            ")"
        );
        conn.createStatement().execute(
            "CREATE INDEX IF NOT EXISTS idx_log_net ON audit_log(net_id)"
        );
        conn.createStatement().execute(
            "CREATE INDEX IF NOT EXISTS idx_log_player ON audit_log(player)"
        );
        conn.createStatement().execute(
            "CREATE INDEX IF NOT EXISTS idx_log_time ON audit_log(time_ms)"
        );
        conn.createStatement().execute(
            "CREATE INDEX IF NOT EXISTS idx_log_meter ON audit_log(net_id, action, time_ms)"
        );
    }

    public boolean isConnected() { return conn != null; }

    @Override
    public void log(AuditEntry entry) {
        if (conn == null || writer == null || writer.isShutdown()) return;
        writer.submit(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO audit_log (time_ms, action, player, uuid, net_id, target_type, target_info, success, detail) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, entry.timestamp());
                ps.setString(2, entry.action());
                ps.setString(3, entry.playerName());
                ps.setString(4, entry.playerUuid() != null ? entry.playerUuid().toString() : null);
                ps.setInt(5, entry.netId());
                ps.setString(6, entry.targetType());
                ps.setString(7, entry.targetInfo());
                ps.setBoolean(8, entry.success());
                ps.setString(9, entry.detail());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("[SQLite] Failed to insert log entry", e);
            }
        });
    }

    @Override
    public void flush() {
        // submissions are immediate; Executor drains naturally
    }

    @Override
    public List<AuditEntry> queryByNet(int netId, int page, int pageSize) {
        if (conn == null) return List.of();
        List<AuditEntry> result = new ArrayList<>();
        String sql = "SELECT * FROM audit_log WHERE net_id = ? ORDER BY time_ms DESC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, netId);
            ps.setInt(2, pageSize);
            ps.setInt(3, page * pageSize);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(rowToEntry(rs));
        } catch (SQLException e) {
            LOGGER.error("[SQLite] Query failed", e);
        }
        return result;
    }

    @Override
    public List<AuditEntry> queryByPlayer(String playerName, int page, int pageSize) {
        if (conn == null) return List.of();
        List<AuditEntry> result = new ArrayList<>();
        String sql = "SELECT * FROM audit_log WHERE player = ? ORDER BY time_ms DESC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ps.setInt(2, pageSize);
            ps.setInt(3, page * pageSize);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(rowToEntry(rs));
        } catch (SQLException e) {
            LOGGER.error("[SQLite] Query by player failed", e);
        }
        return result;
    }

    @Override
    public List<AuditEntry> queryRecent(int count) {
        if (conn == null) return List.of();
        List<AuditEntry> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM audit_log ORDER BY time_ms DESC LIMIT ?")) {
            ps.setInt(1, count);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(rowToEntry(rs));
        } catch (SQLException e) {
            LOGGER.error("[SQLite] Recent query failed", e);
        }
        return result;
    }

    @Override
    public int countByNet(int netId) {
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM audit_log WHERE net_id = ?")) {
            ps.setInt(1, netId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.error("[SQLite] Count failed", e);
        }
        return 0;
    }

    @Override
    public void close() {
        if (writer != null) {
            writer.shutdown();
            try { writer.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        LOGGER.info("[SQLite] Database closed");
    }

    private AuditEntry rowToEntry(ResultSet rs) throws SQLException {
        String uuidStr = rs.getString("uuid");
        java.util.UUID uuid = uuidStr != null ? java.util.UUID.fromString(uuidStr) : null;
        return new AuditEntry(
            rs.getLong("time_ms"),
            rs.getString("action"),
            rs.getString("player"),
            uuid,
            rs.getInt("net_id"),
            rs.getString("target_type"),
            rs.getString("target_info"),
            rs.getBoolean("success"),
            rs.getString("detail")
        );
    }
}
