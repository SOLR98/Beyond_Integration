package com.solr98.beyondintegration.feature.bind.db;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.feature.bind.AuditEntry;
import org.slf4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MysqlBackend implements DatabaseBackend {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final String url;
    private final String user;
    private final String password;
    private Connection conn;
    private ExecutorService writer;

    public MysqlBackend(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public void init() {
        try {
            Properties props = new Properties();
            if (user != null && !user.isEmpty()) props.setProperty("user", user);
            if (password != null && !password.isEmpty()) props.setProperty("password", password);
            props.setProperty("useSSL", "false");
            props.setProperty("rewriteBatchedStatements", "true");

            conn = DriverManager.getConnection(url, props);
            ensureSchema();
            writer = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "bd-mysql-writer");
                t.setDaemon(true);
                return t;
            });
            LOGGER.info("[MySQL] Audit database connected");
        } catch (SQLException e) {
            LOGGER.error("[MySQL] Failed to connect", e);
        }
    }

    private void ensureSchema() throws SQLException {
        conn.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS audit_log (" +
            "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
            "  time_ms BIGINT NOT NULL," +
            "  action VARCHAR(32) NOT NULL," +
            "  player VARCHAR(64)," +
            "  uuid VARCHAR(36)," +
            "  net_id INT NOT NULL," +
            "  target_type VARCHAR(32)," +
            "  target_info VARCHAR(512)," +
            "  success BOOLEAN NOT NULL DEFAULT TRUE," +
            "  detail TEXT," +
            "  INDEX idx_net (net_id)," +
            "  INDEX idx_player (player)," +
            "  INDEX idx_time (time_ms)," +
            "  INDEX idx_meter (net_id, action, time_ms)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
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
                LOGGER.error("[MySQL] Insert failed", e);
            }
        });
    }

    @Override
    public void flush() {}

    @Override
    public List<AuditEntry> queryByNet(int netId, int page, int pageSize) {
        if (conn == null) return List.of();
        List<AuditEntry> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM audit_log WHERE net_id = ? ORDER BY time_ms DESC LIMIT ? OFFSET ?")) {
            ps.setInt(1, netId);
            ps.setInt(2, pageSize);
            ps.setInt(3, page * pageSize);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(rowToEntry(rs));
        } catch (SQLException e) {
            LOGGER.error("[MySQL] Query failed", e);
        }
        return result;
    }

    @Override
    public List<AuditEntry> queryByPlayer(String playerName, int page, int pageSize) {
        if (conn == null) return List.of();
        List<AuditEntry> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM audit_log WHERE player = ? ORDER BY time_ms DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, playerName);
            ps.setInt(2, pageSize);
            ps.setInt(3, page * pageSize);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(rowToEntry(rs));
        } catch (SQLException e) {
            LOGGER.error("[MySQL] Player query failed", e);
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
            LOGGER.error("[MySQL] Recent query failed", e);
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
            LOGGER.error("[MySQL] Count failed", e);
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
        LOGGER.info("[MySQL] Database closed");
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
