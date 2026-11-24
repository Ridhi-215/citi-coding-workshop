package com.example.common.db;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds one PostgreSQL connection per Lambda container and reuses it across invocations.
 *
 * <p>A Lambda container serves one request at a time, so a pool adds nothing but
 * start-up cost and background threads that freeze between invocations. Instead the
 * connection is validated before each use and reopened when it has gone stale.
 */
public final class Db {

    private static final Logger LOG = Logger.getLogger(Db.class.getName());

    /** Seconds allowed for {@link Connection#isValid(int)} before the connection is replaced. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    /** Driver used directly, so loading never depends on ServiceLoader metadata in the shaded jar. */
    private static final Driver DRIVER = new org.postgresql.Driver();

    private static DbConfig config;
    private static Connection connection;

    private Db() {
    }

    /**
     * Returns a valid connection, opening a new one when needed.
     *
     * <p>Opening is retried once, because the first attempt can time out while
     * Aurora Serverless resumes from zero capacity.
     *
     * @return an open connection in auto-commit mode
     * @throws SQLException if the database cannot be reached after one retry
     */
    public static synchronized Connection get() throws SQLException {
        if (connection != null && isUsable(connection)) {
            return connection;
        }
        closeQuietly();
        DbConfig cfg = config();
        try {
            connection = open(cfg);
        } catch (SQLException first) {
            LOG.log(Level.WARNING, "Database connection failed, retrying once: {0}", first.getMessage());
            connection = open(cfg);
        }
        return connection;
    }

    /**
     * Overrides the configuration (used by integration tests) and drops any open connection.
     *
     * @param override configuration to use for subsequent connections
     */
    public static synchronized void configure(DbConfig override) {
        closeQuietly();
        config = override;
    }

    /** Closes the cached connection, ignoring errors. */
    public static synchronized void closeQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Connection is being discarded anyway.
            }
            connection = null;
        }
    }

    private static DbConfig config() {
        if (config == null) {
            config = DbConfig.fromEnv();
        }
        return config;
    }

    private static Connection open(DbConfig cfg) throws SQLException {
        Connection conn = DRIVER.connect(cfg.jdbcUrl(), cfg.jdbcProperties());
        if (conn == null) {
            throw new SQLException("PostgreSQL driver rejected URL " + cfg.jdbcUrl());
        }
        conn.setAutoCommit(true);
        return conn;
    }

    private static boolean isUsable(Connection conn) {
        try {
            return !conn.isClosed() && conn.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            return false;
        }
    }
}
