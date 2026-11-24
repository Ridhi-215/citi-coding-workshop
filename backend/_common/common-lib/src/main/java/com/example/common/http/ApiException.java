package com.example.common.http;

import java.util.Map;

/**
 * An error that maps directly to an HTTP response in the shared error format.
 *
 * <p>Thrown anywhere in request handling; {@link Router} turns it into
 * {@code {"error": {"code", "message", "details"}}} with the given status.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final transient Object details;

    /**
     * Creates an API error.
     *
     * @param status  HTTP status code
     * @param code    machine-readable error code
     * @param message human-readable message
     * @param details optional structured details (may be null)
     */
    public ApiException(int status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    /**
     * 400 with per-field validation errors.
     *
     * @param fieldErrors field name to error message
     * @return exception to throw
     */
    public static ApiException validation(Map<String, String> fieldErrors) {
        return new ApiException(400, "VALIDATION_FAILED", "One or more fields are invalid", fieldErrors);
    }

    /**
     * 400 for a malformed request (bad JSON, bad query parameter, ...).
     *
     * @param message what was wrong
     * @return exception to throw
     */
    public static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message, null);
    }

    /**
     * 401 for missing, invalid or expired credentials.
     *
     * @param code    {@code UNAUTHORIZED}, {@code INVALID_TOKEN}, {@code TOKEN_EXPIRED} or {@code INVALID_CREDENTIALS}
     * @param message human-readable message
     * @return exception to throw
     */
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(401, code, message, null);
    }

    /**
     * 403 when the caller's role does not allow the action.
     *
     * @param message human-readable message
     * @return exception to throw
     */
    public static ApiException forbidden(String message) {
        return new ApiException(403, "FORBIDDEN", message, null);
    }

    /**
     * 404 for a missing resource.
     *
     * @param resource resource name, e.g. "Project"
     * @param id       requested id
     * @return exception to throw
     */
    public static ApiException notFound(String resource, Object id) {
        return new ApiException(404, "NOT_FOUND", resource + " " + id + " was not found", null);
    }

    /**
     * 409 Conflict: the request clashes with existing data (e.g. an email already registered).
     *
     * @param code    machine-readable code
     * @param message human-readable message
     * @param details field errors, or null
     * @return exception
     */
    public static ApiException conflict(String code, String message, Object details) {
        return new ApiException(409, code, message, details);
    }

    /**
     * Returns the HTTP status code.
     *
     * @return status code
     */
    public int status() {
        return status;
    }

    /**
     * Returns the machine-readable error code.
     *
     * @return error code
     */
    public String code() {
        return code;
    }

    /**
     * Returns the structured details, if any.
     *
     * @return details or null
     */
    public Object details() {
        return details;
    }
}
