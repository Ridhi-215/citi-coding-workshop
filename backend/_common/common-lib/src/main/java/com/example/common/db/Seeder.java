package com.example.common.db;

import com.example.common.auth.Passwords;
import java.sql.Connection;
import java.util.List;
import java.util.logging.Logger;

/**
 * Loads demo data so a fresh environment (local or AWS) is ready to present.
 *
 * <p>Users and domain data are seeded independently, each only when its table is empty,
 * so existing data is never overwritten.
 */
final class Seeder {

    private static final Logger LOG = Logger.getLogger(Seeder.class.getName());

    /**
     * Shared password of the demo accounts, documented in the README.
     * Demo-only trade-off: change or remove these accounts for any real use.
     */
    static final String DEMO_PASSWORD = "Demo@1234";

    /** One demo account per role: email, full name, role. */
    static final List<String[]> DEMO_USERS = List.of(
        new String[] {"admin@acme.com", "Alice Admin", "admin"},
        new String[] {"manager@acme.com", "Mark Manager", "manager"},
        new String[] {"contributor@acme.com", "Cara Contributor", "contributor"},
        new String[] {"viewer@acme.com", "Victor Viewer", "viewer"});

    private Seeder() {
    }

    /**
     * Seeds whatever is missing.
     *
     * @param conn connection inside the caller's transaction
     * @throws Exception on database errors
     */
    static void seed(Connection conn) throws Exception {
        if (Sql.scalar(conn, "SELECT count(*) FROM users") == 0) {
            String hash = Passwords.hash(DEMO_PASSWORD);
            for (String[] user : DEMO_USERS) {
                Sql.update(conn,
                    "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, ?, ?, ?)",
                    user[0], hash, user[1], user[2]);
            }
            LOG.info("Seeded " + DEMO_USERS.size() + " demo users");
        }
        if (Sql.scalar(conn, "SELECT count(*) FROM projects") == 0
            && Migrations.class.getClassLoader().getResource(Migrations.SEED_SCRIPT) != null) {
            Migrations.execute(conn, Migrations.load(Migrations.SEED_SCRIPT));
            LOG.info("Seeded demo projects");
        }
    }
}
