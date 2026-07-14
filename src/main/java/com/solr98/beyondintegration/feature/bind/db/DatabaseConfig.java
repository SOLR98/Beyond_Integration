package com.solr98.beyondintegration.feature.bind.db;

public class DatabaseConfig {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String tablePrefix;
    private final int batchSize;
    private final long flushIntervalMs;
    private final int maxRetries;
    private final long retryDelayMs;

    public DatabaseConfig(String jdbcUrl, String username, String password) {
        this(jdbcUrl, username, password, "beyond_", 50, 5000L, 3, 3000L);
    }

    public DatabaseConfig(String jdbcUrl, String username, String password,
                          String tablePrefix, int batchSize, long flushIntervalMs,
                          int maxRetries, long retryDelayMs) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix;
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMs = Math.max(1000, flushIntervalMs);
        this.maxRetries = Math.max(0, maxRetries);
        this.retryDelayMs = Math.max(500, retryDelayMs);
    }

    public String jdbcUrl() { return jdbcUrl; }
    public String username() { return username; }
    public String password() { return password; }
    public String tablePrefix() { return tablePrefix; }
    public String tableName() { return tablePrefix + "audit_log"; }
    public int batchSize() { return batchSize; }
    public long flushIntervalMs() { return flushIntervalMs; }
    public int maxRetries() { return maxRetries; }
    public long retryDelayMs() { return retryDelayMs; }
}
