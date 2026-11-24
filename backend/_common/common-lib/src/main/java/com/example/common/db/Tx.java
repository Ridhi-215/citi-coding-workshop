package com.example.common.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs work in a single database transaction.
 */
public final class Tx {

    /**
     * Work to run inside a transaction.
     *
     * @param <T> result type
     */
    @FunctionalInterface
    public interface Work<T> {
        /**
         * Runs the work.
         *
         * @param conn connection with auto-commit off
         * @return result
         * @throws Exception any error; the transaction is rolled back
         */
        T run(Connection conn) throws Exception;
    }

    private Tx() {
    }

    /**
     * Runs work in a transaction: commits on success, rolls back on any exception.
     *
     * @param conn open connection (auto-commit is restored afterwards)
     * @param work work to run
     * @param <T>  result type
     * @return the work's result
     * @throws Exception the work's exception, after rollback
     */
    public static <T> T run(Connection conn, Work<T> work) throws Exception {
        boolean previous = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            T result = work.run(conn);
            conn.commit();
            return result;
        } catch (Exception e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            conn.setAutoCommit(previous);
        }
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // The original error is more useful to the caller.
        }
    }
}
