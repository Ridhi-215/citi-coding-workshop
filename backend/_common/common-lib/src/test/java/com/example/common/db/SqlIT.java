package com.example.common.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.testing.TestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Column conversion, transactions and reconnects against the local PostgreSQL.
 */
class SqlIT {

    private static Connection conn;

    @BeforeAll
    static void setUp() throws Exception {
        conn = TestDatabase.reset();
    }

    @Test
    void convertsColumnTypes() throws Exception {
        Map<String, Object> row = Sql.queryOne(conn, "SELECT DATE '2026-01-02' AS d, now() AS ts,"
            + " '{\"a\":1}'::jsonb AS j, ARRAY['x','y'] AS arr, NULL::text AS n, 42 AS i").orElseThrow();
        assertEquals(LocalDate.of(2026, 1, 2), row.get("d"));
        assertTrue(row.get("ts") instanceof OffsetDateTime);
        assertEquals(1, ((JsonNode) row.get("j")).get("a").asInt());
        assertEquals(List.of("x", "y"), row.get("arr"));
        assertEquals(null, row.get("n"));
        assertEquals(42, row.get("i"));
        assertFalse(Sql.queryOne(conn, "SELECT 1 WHERE false").isPresent());
    }

    @Test
    void existsAndScalar() throws Exception {
        long id = Sql.scalar(conn, "SELECT id FROM users ORDER BY id LIMIT 1");
        assertTrue(Sql.exists(conn, "users", id));
        assertFalse(Sql.exists(conn, "users", -1));
        assertEquals(0, Sql.scalar(conn, "SELECT count(*) FROM users WHERE false"));
    }

    @Test
    void transactionRollsBackOnError() throws Exception {
        long before = Sql.scalar(conn, "SELECT count(*) FROM team_members");
        assertThrows(SQLException.class, () -> Tx.run(conn, c -> {
            Sql.update(c, "INSERT INTO team_members (full_name, email) VALUES ('Tx Test', 'tx@acme.com')");
            return Sql.update(c, "INSERT INTO team_members (full_name, email) VALUES ('Tx Test', 'tx@acme.com')");
        }));
        assertEquals(before, Sql.scalar(conn, "SELECT count(*) FROM team_members"));
        assertTrue(conn.getAutoCommit());
        assertEquals(1, (int) Tx.run(conn, c -> Sql.update(c,
            "INSERT INTO team_members (full_name, email) VALUES ('Tx Ok', 'tx.ok@acme.com')")));
        assertEquals(before + 1, Sql.scalar(conn, "SELECT count(*) FROM team_members"));
    }

    @Test
    void reconnectsAfterTheConnectionIsLost() throws Exception {
        Connection first = Db.get();
        first.close();
        Connection second = Db.get();
        assertTrue(second.isValid(2));
        assertEquals(1, Sql.scalar(second, "SELECT 1"));
        conn = second;
    }
}
