package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.common.db.Db;
import com.example.common.http.Responses;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lambda entry point for auth-service ({@code com.example.Handler::handleRequest},
 * the handler name Terraform configures for every Java service).
 *
 * <p>Packaging spike (J7): answers {@code /health} with a PostgreSQL round-trip and
 * echoes the request path it received, so the shared router can be built against
 * the real event shape locally and behind CloudFront.
 */
public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    /**
     * Handles a Lambda Function URL request (payload format 2.0).
     *
     * @param event   the Lambda event
     * @param context the Lambda context
     * @return a response map with statusCode, headers and body
     */
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "auth-service");
        body.put("rawPath", event == null ? null : event.get("rawPath"));
        body.put("javaVersion", System.getProperty("java.version"));
        try {
            Connection conn = Db.get();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT version(), current_database(), now()")) {
                rs.next();
                body.put("postgres", rs.getString(1));
                body.put("database", rs.getString(2));
                body.put("dbTime", rs.getString(3));
            }
            return Responses.ok(body);
        } catch (Exception e) {
            body.put("dbError", e.getClass().getSimpleName() + ": " + e.getMessage());
            return Responses.error(500, "DB_UNAVAILABLE", "Database round-trip failed", body);
        }
    }
}
