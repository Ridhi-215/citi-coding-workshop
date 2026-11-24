package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.testing.TestDatabase;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The operator-only demo reset restores exactly the seed data.
 */
class MaintenanceIT {

    @BeforeAll
    static void setUp() throws Exception {
        TestDatabase.reset();
    }

    @Test
    void resetRestoresTheSeedData() throws Exception {
        long projects = Sql.scalar(Db.get(), "SELECT count(*) FROM projects");
        Sql.update(Db.get(), "INSERT INTO users (email, password_hash, full_name) VALUES ('smoke@example.com', 'x', 'Smoke')");
        Sql.update(Db.get(), "DELETE FROM projects WHERE code = 'APOLLO'");

        Map<String, Object> result = new Handler().handleRequest(Map.of("maintenance", "reset-demo-data"), null);

        assertEquals("ok", result.get("status"));
        assertEquals(4, Sql.scalar(Db.get(), "SELECT count(*) FROM users"));
        assertEquals(projects, Sql.scalar(Db.get(), "SELECT count(*) FROM projects"));
        assertEquals(1, Sql.scalar(Db.get(), "SELECT count(*) FROM projects WHERE code = 'APOLLO'"));
        assertEquals(1, Sql.scalar(Db.get(), "SELECT min(id) FROM users"));
    }

    @Test
    void unknownActionChangesNothing() throws Exception {
        long users = Sql.scalar(Db.get(), "SELECT count(*) FROM users");
        assertEquals("error", new Handler().handleRequest(Map.of("maintenance", "drop-everything"), null).get("status"));
        assertEquals(users, Sql.scalar(Db.get(), "SELECT count(*) FROM users"));
    }
}
