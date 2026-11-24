package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.http.Responses;
import com.example.common.http.Router;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lambda entry point for auth-service ({@code com.example.Handler::handleRequest},
 * the handler name Terraform configures for every Java service).
 */
public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    /** Built once per container and reused across invocations. */
    private static final Router ROUTER = new Router("auth-service")
        .get("/health/db", null, req -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "ok");
            body.put("postgres", Sql.scalarText(Db.get(), "SELECT version()"));
            body.put("users", Sql.scalar(Db.get(), "SELECT count(*) FROM users"));
            body.put("javaVersion", System.getProperty("java.version"));
            return Responses.ok(body);
        });

    /**
     * Handles a Lambda Function URL request (payload format 2.0).
     *
     * @param event   the Lambda event
     * @param context the Lambda context
     * @return a response map with statusCode, headers and body
     */
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        return ROUTER.handle(event);
    }
}
