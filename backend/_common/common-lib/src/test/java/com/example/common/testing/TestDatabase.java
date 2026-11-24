package com.example.common.testing;

import com.example.common.db.Db;
import com.example.common.db.DbConfig;
import com.example.common.db.Migrations;
import com.example.common.db.Sql;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Points {@link Db} at a throwaway {@code acme_test} database on the local PostgreSQL
 * and rebuilds it from the migrations. Shared with the services' integration tests
 * through the common-lib test jar.
 *
 * <p>Connection settings come from {@code TEST_POSTGRES_*} environment variables and
 * default to the local workshop setup (see infra/locals.tf).
 */
public final class TestDatabase {

    /** Name of the throwaway database. */
    public static final String NAME = "acme_test";

    private static boolean ready;

    private TestDatabase() {
    }

    /**
     * Recreates the test database once per JVM, applies migrations and the seed data,
     * and configures {@link Db} to use it.
     *
     * @return open connection to the test database
     * @throws Exception if PostgreSQL is not reachable
     */
    public static synchronized Connection reset() throws Exception {
        if (!ready) {
            recreate();
            Db.configure(config(NAME));
            Migrations.ensure();
            ready = true;
        }
        return Db.get();
    }

    /**
     * Runs a statement on the test database (e.g. to clean a table between tests).
     *
     * @param sql statement
     * @throws SQLException on database errors
     */
    public static void exec(String sql) throws SQLException {
        Sql.update(Db.get(), sql);
    }

    private static void recreate() throws SQLException {
        DbConfig admin = config(env("TEST_POSTGRES_ADMIN_DB", "postgres"));
        try (Connection conn = DriverManager.getConnection(admin.jdbcUrl(), admin.jdbcProperties());
             Statement st = conn.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + NAME + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + NAME);
        }
    }

    private static DbConfig config(String database) {
        return DbConfig.fromMap(Map.of(
            "POSTGRES_HOST", env("TEST_POSTGRES_HOST", "127.0.0.1"),
            "POSTGRES_PORT", env("TEST_POSTGRES_PORT", "5432"),
            "POSTGRES_NAME", database,
            "POSTGRES_USER", env("TEST_POSTGRES_USER", "postgres"),
            "POSTGRES_PASS", env("TEST_POSTGRES_PASS", "postgres123"),
            "IS_LOCAL", "true"));
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
