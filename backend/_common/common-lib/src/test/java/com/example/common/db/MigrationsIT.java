package com.example.common.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.auth.Passwords;
import com.example.common.testing.TestDatabase;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Applies the real migrations and seed to the local PostgreSQL.
 */
class MigrationsIT {

    private static Connection conn;

    @BeforeAll
    static void setUp() throws Exception {
        conn = TestDatabase.reset();
    }

    @Test
    void createsAllTables() throws Exception {
        List<Map<String, Object>> tables = Sql.query(conn,
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1");
        List<Object> names = tables.stream().map(t -> t.get("table_name")).toList();
        assertTrue(names.containsAll(List.of("users", "team_members", "projects", "expenses",
            "deliverables", "deliverable_dependencies", "allocations", "schema_migrations")), names.toString());
    }

    @Test
    void seedsOneUserPerRoleWithWorkingPassword() throws Exception {
        List<Map<String, Object>> users = Sql.query(conn, "SELECT email, role, password_hash FROM users ORDER BY id");
        assertEquals(List.of("admin", "manager", "contributor", "viewer"),
            users.stream().map(u -> u.get("role")).toList());
        assertTrue(Passwords.matches(Seeder.DEMO_PASSWORD, (String) users.get(0).get("password_hash")));
    }

    @Test
    void isIdempotent() throws Exception {
        Migrations.ensure(conn);
        Migrations.ensure(conn);
        assertEquals(4, Sql.scalar(conn, "SELECT count(*) FROM users"));
        assertEquals(Migrations.SCRIPTS.size(), Sql.scalar(conn, "SELECT count(*) FROM schema_migrations"));
    }
}
