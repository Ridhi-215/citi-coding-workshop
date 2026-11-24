package com.example.common.db;

import com.example.common.http.Json;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Small JDBC helpers: parameterized queries returning rows as ordered maps.
 *
 * <p>Values are always bound as parameters, never concatenated into SQL. Dates come
 * back as {@link LocalDate}, timestamps as {@link OffsetDateTime} and JSON columns as
 * parsed trees, so rows serialize directly to API responses.
 */
public final class Sql {

    private Sql() {
    }

    /**
     * Runs a query and returns all rows.
     *
     * @param conn   open connection
     * @param sql    SQL with {@code ?} placeholders
     * @param params values for the placeholders
     * @return rows keyed by column label, in select order
     * @throws SQLException on database errors
     */
    public static List<Map<String, Object>> query(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement st = prepare(conn, sql, params); ResultSet rs = st.executeQuery()) {
            return readAll(rs);
        }
    }

    /**
     * Runs a query expected to return at most one row.
     *
     * @param conn   open connection
     * @param sql    SQL with {@code ?} placeholders
     * @param params values for the placeholders
     * @return the first row, if any
     * @throws SQLException on database errors
     */
    public static Optional<Map<String, Object>> queryOne(Connection conn, String sql, Object... params)
        throws SQLException {
        List<Map<String, Object>> rows = query(conn, sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Runs a query returning a single number, e.g. {@code SELECT count(*)}.
     *
     * @param conn   open connection
     * @param sql    SQL with {@code ?} placeholders
     * @param params values for the placeholders
     * @return the value of the first column of the first row, or 0
     * @throws SQLException on database errors
     */
    public static long scalar(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement st = prepare(conn, sql, params); ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /**
     * Runs a query returning a single text value.
     *
     * @param conn   open connection
     * @param sql    SQL with {@code ?} placeholders
     * @param params values for the placeholders
     * @return the first column of the first row, or null
     * @throws SQLException on database errors
     */
    public static String scalarText(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement st = prepare(conn, sql, params); ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /**
     * Runs an INSERT, UPDATE or DELETE.
     *
     * @param conn   open connection
     * @param sql    SQL with {@code ?} placeholders
     * @param params values for the placeholders
     * @return number of affected rows
     * @throws SQLException on database errors
     */
    public static int update(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement st = prepare(conn, sql, params)) {
            return st.executeUpdate();
        }
    }

    /**
     * Checks whether a row with the given id exists in a table.
     *
     * @param conn  open connection
     * @param table table name (a constant from code, never user input)
     * @param id    primary key
     * @return true if found
     * @throws SQLException on database errors
     */
    public static boolean exists(Connection conn, String table, long id) throws SQLException {
        return scalar(conn, "SELECT count(*) FROM " + table + " WHERE id = ?", id) > 0;
    }

    private static PreparedStatement prepare(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement st = conn.prepareStatement(sql);
        try {
            for (int i = 0; i < params.length; i++) {
                st.setObject(i + 1, params[i]);
            }
            return st;
        } catch (SQLException e) {
            st.close();
            throw e;
        }
    }

    private static List<Map<String, Object>> readAll(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                row.put(meta.getColumnLabel(i), read(rs, i, meta.getColumnTypeName(i)));
            }
            rows.add(row);
        }
        return rows;
    }

    private static Object read(ResultSet rs, int i, String typeName) throws SQLException {
        Object raw = rs.getObject(i);
        if (raw == null) {
            return null;
        }
        return switch (typeName) {
            case "date" -> rs.getObject(i, LocalDate.class);
            case "timestamptz", "timestamp" -> rs.getObject(i, OffsetDateTime.class);
            case "json", "jsonb" -> parseJson(raw.toString());
            default -> raw;
        };
    }

    private static Object parseJson(String text) throws SQLException {
        try {
            return Json.MAPPER.readTree(text);
        } catch (Exception e) {
            throw new SQLException("Invalid JSON returned by query", e);
        }
    }
}
