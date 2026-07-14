package com.solr98.beyondintegration.feature.bind.db;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.*;
import java.util.Properties;

public class DatabaseManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final DatabaseConfig config;
    private Connection connection;
    private long lastConnectionAttempt = 0;
    private int consecutiveFailures = 0;

    public DatabaseManager(DatabaseConfig config) {
        this.config = config;
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed() && connection.isValid(2)) {
            return connection;
        }
        closeQuietly();
        connection = createConnection();
        return connection;
    }

    private Connection createConnection() throws SQLException {
        long now = System.currentTimeMillis();
        if (now - lastConnectionAttempt < config.retryDelayMs() && consecutiveFailures > 0) {
            throw new SQLException("Skipping connection attempt (cooldown), failures=" + consecutiveFailures);
        }
        lastConnectionAttempt = now;

        Properties props = new Properties();
        if (config.username() != null && !config.username().isEmpty()) {
            props.setProperty("user", config.username());
        }
        if (config.password() != null && !config.password().isEmpty()) {
            props.setProperty("password", config.password());
        }
        props.setProperty("autoReconnect", "true");
        props.setProperty("useSSL", "false");
        props.setProperty("characterEncoding", "utf8mb4");

        try {
            Connection conn = DriverManager.getConnection(config.jdbcUrl(), props);
            consecutiveFailures = 0;
            LOGGER.info("[DB] Connected to database: {}", maskUrl(config.jdbcUrl()));
            initTable(conn);
            return conn;
        } catch (SQLException e) {
            consecutiveFailures++;
            LOGGER.error("[DB] Failed to connect (attempt {}): {}", consecutiveFailures, e.getMessage());
            throw e;
        }
    }

    private void initTable(Connection conn) throws SQLException {
        String tableName = config.tableName();
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "timestamp BIGINT NOT NULL,"
                + "action VARCHAR(32) NOT NULL,"
                + "player_name VARCHAR(64),"
                + "player_uuid CHAR(36),"
                + "net_id INT NOT NULL,"
                + "target_type VARCHAR(32),"
                + "target_info VARCHAR(512),"
                + "success BOOLEAN NOT NULL,"
                + "detail TEXT,"
                + "INDEX idx_net_id (net_id),"
                + "INDEX idx_player_name (player_name),"
                + "INDEX idx_action (action),"
                + "INDEX idx_timestamp (timestamp)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        // Try InnoDB first, fallback to default engine if MySQL-specific syntax fails
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            LOGGER.info("[DB] Table {} initialized", tableName);
        } catch (SQLException e) {
            // Fallback for non-MySQL databases (PostgreSQL, H2, etc.)
            String fallbackSql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "timestamp BIGINT NOT NULL,"
                    + "action VARCHAR(32) NOT NULL,"
                    + "player_name VARCHAR(64),"
                    + "player_uuid VARCHAR(36),"
                    + "net_id INT NOT NULL,"
                    + "target_type VARCHAR(32),"
                    + "target_info VARCHAR(512),"
                    + "success BOOLEAN NOT NULL,"
                    + "detail TEXT"
                    + ")";
            try (Statement stmt2 = conn.createStatement()) {
                stmt2.execute(fallbackSql);
                LOGGER.info("[DB] Table {} initialized (fallback mode)", tableName);
            } catch (SQLException e2) {
                LOGGER.error("[DB] Failed to create table: {}", e2.getMessage());
                throw e2;
            }
        }
    }

    public void executeBatch(String sql, java.util.List<AuditDbEntry> entries) {
        int retries = 0;
        while (retries <= config.maxRetries()) {
            try {
                Connection conn = getConnection();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (AuditDbEntry e : entries) {
                        ps.setLong(1, e.timestamp());
                        ps.setString(2, e.action());
                        ps.setString(3, e.playerName());
                        ps.setString(4, e.playerUuid());
                        ps.setInt(5, e.netId());
                        ps.setString(6, e.targetType());
                        ps.setString(7, e.targetInfo());
                        ps.setBoolean(8, e.success());
                        ps.setString(9, e.detail());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                return;
            } catch (SQLException e) {
                retries++;
                if (retries > config.maxRetries()) {
                    LOGGER.error("[DB] Batch insert failed after {} retries: {}", retries, e.getMessage());
                    return;
                }
                LOGGER.warn("[DB] Batch insert failed (retry {}/{}): {}", retries, config.maxRetries(), e.getMessage());
                closeQuietly();
                try { Thread.sleep(config.retryDelayMs()); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
    }

    public void closeQuietly() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) connection.close();
            } catch (SQLException ignored) {}
            connection = null;
        }
    }

    private static String maskUrl(String url) {
        int at = url.indexOf('@');
        if (at > 0) {
            int colon = url.indexOf(':', url.indexOf("//") + 2);
            if (colon > 0 && colon < at) {
                return url.substring(0, url.indexOf("//") + 2) + "****:****" + url.substring(at);
            }
        }
        return url;
    }
}
