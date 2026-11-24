package com.example.common.http;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds Lambda Function URL responses with a consistent JSON shape.
 *
 * <p>Errors always look like {@code {"error": {"code": "...", "message": "...", "details": {...}}}}.
 */
public final class Responses {

    private Responses() {
    }

    /**
     * 200 OK with a JSON body.
     *
     * @param body object to serialize
     * @return Lambda response map
     */
    public static Map<String, Object> ok(Object body) {
        return json(200, body);
    }

    /**
     * 201 Created with a JSON body.
     *
     * @param body created resource
     * @return Lambda response map
     */
    public static Map<String, Object> created(Object body) {
        return json(201, body);
    }

    /**
     * 204 No Content.
     *
     * @return Lambda response map with an empty body
     */
    public static Map<String, Object> noContent() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCode", 204);
        response.put("headers", Map.of());
        response.put("body", "");
        return response;
    }

    /**
     * Error response in the shared format.
     *
     * @param status  HTTP status code
     * @param code    machine-readable error code, e.g. {@code VALIDATION_FAILED}
     * @param message human-readable message
     * @param details optional extra information, e.g. per-field errors (may be null)
     * @return Lambda response map
     */
    public static Map<String, Object> error(int status, String code, String message, Object details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (details != null) {
            error.put("details", details);
        }
        return json(status, Map.of("error", error));
    }

    /**
     * Response with any status and a JSON body.
     *
     * @param status HTTP status code
     * @param body   object to serialize
     * @return Lambda response map
     */
    public static Map<String, Object> json(int status, Object body) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCode", status);
        response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", Json.write(body));
        return response;
    }
}
