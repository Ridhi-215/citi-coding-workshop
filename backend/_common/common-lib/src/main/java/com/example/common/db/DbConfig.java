package com.example.common.db;

import java.util.Map;
import java.util.Properties;

/**
 * PostgreSQL connection settings read from the environment variables that
 * Terraform injects into every Lambda (see infra/locals.tf {@code env_vars}).
 *
 * @param host     PostgreSQL hostname ({@code POSTGRES_HOST})
 * @param port     PostgreSQL port ({@code POSTGRES_PORT})
 * @param database database name ({@code POSTGRES_NAME})
 * @param user     username ({@code POSTGRES_USER})
 * @param password password ({@code POSTGRES_PASS})
 * @param local    true when running on LocalStack ({@code IS_LOCAL}); disables SSL
 */
public record DbConfig(String host, int port, String database, String user, String password, boolean local) {

    /** Seconds to wait for a TCP connection; Aurora Serverless v2 can take ~15s to resume from 0 ACU. */
    static final int CONNECT_TIMEOUT_SECONDS = 30;

    /** Seconds a single statement may block on the socket before failing. */
    static final int SOCKET_TIMEOUT_SECONDS = 60;

    /**
     * Builds a configuration from the process environment.
     *
     * @return configuration for the current environment
     */
    public static DbConfig fromEnv() {
        return fromMap(System.getenv());
    }

    /**
     * Builds a configuration from an environment-like map (used by tests).
     *
     * @param env variable name to value map
     * @return parsed configuration, using local-development defaults for missing values
     */
    public static DbConfig fromMap(Map<String, String> env) {
        return new DbConfig(
            value(env, "POSTGRES_HOST", "localhost"),
            Integer.parseInt(value(env, "POSTGRES_PORT", "5432")),
            value(env, "POSTGRES_NAME", "postgres"),
            value(env, "POSTGRES_USER", "postgres"),
            value(env, "POSTGRES_PASS", ""),
            !"false".equalsIgnoreCase(value(env, "IS_LOCAL", "true"))
        );
    }

    /**
     * Returns the JDBC URL for this configuration.
     *
     * @return JDBC URL without credentials
     */
    public String jdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }

    /**
     * Returns the JDBC driver properties, including SSL settings for AWS Aurora.
     *
     * @return driver properties (contains the password; never log this)
     */
    public Properties jdbcProperties() {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
        props.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_SECONDS));
        props.setProperty("ApplicationName", "acme-tracker");
        props.setProperty("sslmode", local ? "disable" : "require");
        return props;
    }

    /**
     * Hides the password when the record is printed or logged.
     *
     * @return description safe for logs
     */
    @Override
    public String toString() {
        return "DbConfig[" + jdbcUrl() + ", user=" + user + ", local=" + local + "]";
    }

    private static String value(Map<String, String> env, String key, String fallback) {
        String v = env.get(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }
}
