package com.example.common.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;

/**
 * Creates and upgrades the database schema, then seeds demo data into an empty database.
 *
 * <p>Every service calls {@link #ensure()} before its first request. A PostgreSQL advisory
 * lock makes concurrent cold starts wait for each other, and applied versions are recorded
 * in {@code schema_migrations}, so each script runs exactly once.
 */
public final class Migrations {

    private static final Logger LOG = Logger.getLogger(Migrations.class.getName());

    /** Arbitrary constant shared by all services for the advisory lock. */
    private static final long LOCK_KEY = 4_242_001L;

    /** Migration scripts on the classpath, applied in order. Never edit one once deployed; add a new one. */
    static final List<String> SCRIPTS = List.of("db/migration/V1__schema.sql");

    /** Domain demo data, applied when the database has no projects. */
    static final String SEED_SCRIPT = "db/seed/seed.sql";

    private Migrations() {
    }

    /**
     * Applies pending migrations and seeds an empty database, using the shared connection.
     *
     * @throws Exception if a migration or the seed fails (nothing partial is committed)
     */
    public static void ensure() throws Exception {
        ensure(Db.get());
    }

    /**
     * Applies pending migrations and seeds an empty database.
     *
     * @param conn open connection
     * @throws Exception if a migration or the seed fails
     */
    public static void ensure(Connection conn) throws Exception {
        Sql.query(conn, "SELECT pg_advisory_lock(?)", LOCK_KEY);
        try {
            Sql.update(conn, """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                  version    TEXT PRIMARY KEY,
                  applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )""");
            for (String script : SCRIPTS) {
                if (Sql.scalar(conn, "SELECT count(*) FROM schema_migrations WHERE version = ?", script) == 0) {
                    LOG.info("Applying migration " + script);
                    Tx.run(conn, c -> {
                        execute(c, load(script));
                        Sql.update(c, "INSERT INTO schema_migrations (version) VALUES (?)", script);
                        return null;
                    });
                }
            }
            // Seeder only fills tables that are still empty.
            Tx.run(conn, c -> {
                Seeder.seed(c);
                return null;
            });
        } finally {
            Sql.query(conn, "SELECT pg_advisory_unlock(?)", LOCK_KEY);
        }
    }

    /**
     * Runs a multi-statement SQL script (simple query protocol, no parameters).
     *
     * @param conn   open connection
     * @param script SQL text
     * @throws SQLException on database errors
     */
    static void execute(Connection conn, String script) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(script);
        }
    }

    /**
     * Loads a text resource from the classpath.
     *
     * @param path resource path
     * @return file contents
     * @throws IOException if the resource is missing
     */
    static String load(String path) throws IOException {
        try (InputStream in = Migrations.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing classpath resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
