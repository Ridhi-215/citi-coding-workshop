package com.example.common.http;

import com.example.common.auth.Principal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * An HTTP request parsed from a Lambda Function URL event (payload format 2.0).
 */
public final class Request {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final Map<String, String> headers;
    private final String body;
    private Map<String, String> pathParams = Map.of();
    private Principal principal;
    private Map<String, Object> jsonBody;

    /**
     * Creates a request (mainly for tests; handlers receive requests from {@link Router}).
     *
     * @param method  HTTP method, upper case
     * @param path    request path relative to the service, e.g. {@code /projects/1}
     * @param query   query string parameters
     * @param headers headers with lower-case names
     * @param body    raw body text (may be null)
     */
    public Request(String method, String path, Map<String, String> query, Map<String, String> headers, String body) {
        this.method = method;
        this.path = path;
        this.query = query == null ? Map.of() : Collections.unmodifiableMap(query);
        this.headers = headers == null ? Map.of() : Collections.unmodifiableMap(headers);
        this.body = body;
    }

    /**
     * Parses a Lambda Function URL event.
     *
     * @param event raw Lambda event
     * @return parsed request with the full raw path
     * @throws ApiException 400 if the event is not an HTTP event
     */
    @SuppressWarnings("unchecked")
    public static Request fromEvent(Map<String, Object> event) {
        if (event == null || !(event.get("requestContext") instanceof Map<?, ?> ctx)
            || !(ctx.get("http") instanceof Map<?, ?> http)) {
            throw ApiException.badRequest("Not an HTTP request event");
        }
        String method = String.valueOf(http.get("method")).toUpperCase(Locale.ROOT);
        String rawPath = event.get("rawPath") instanceof String p ? p : String.valueOf(http.get("path"));

        Map<String, String> headers = new HashMap<>();
        if (event.get("headers") instanceof Map<?, ?> h) {
            h.forEach((k, v) -> headers.put(String.valueOf(k).toLowerCase(Locale.ROOT), String.valueOf(v)));
        }
        Map<String, String> query = new HashMap<>();
        if (event.get("queryStringParameters") instanceof Map<?, ?> q) {
            q.forEach((k, v) -> query.put(String.valueOf(k), String.valueOf(v)));
        }
        String body = event.get("body") instanceof String b ? b : null;
        if (body != null && Boolean.TRUE.equals(event.get("isBase64Encoded"))) {
            body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        }
        return new Request(method, rawPath, query, headers, body);
    }

    /**
     * Returns the HTTP method.
     *
     * @return upper-case method
     */
    public String method() {
        return method;
    }

    /**
     * Returns the request path.
     *
     * @return path
     */
    public String path() {
        return path;
    }

    /**
     * Returns a header value.
     *
     * @param name header name (case-insensitive)
     * @return value or null
     */
    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns a query parameter, or null when missing or blank.
     *
     * @param name parameter name
     * @return trimmed value or null
     */
    public String query(String name) {
        String v = query.get(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /**
     * Returns an integer query parameter within bounds.
     *
     * @param name     parameter name
     * @param fallback value when missing
     * @param min      smallest allowed value
     * @param max      largest allowed value
     * @return parsed value
     * @throws ApiException 400 if not an integer or out of range
     */
    public int queryInt(String name, int fallback, int min, int max) {
        String v = query(name);
        if (v == null) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) {
                throw ApiException.badRequest("Query parameter '" + name + "' must be between " + min + " and " + max);
            }
            return n;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Query parameter '" + name + "' must be an integer");
        }
    }

    /**
     * Returns an optional positive id from the query string.
     *
     * @param name parameter name
     * @return id or null when missing
     * @throws ApiException 400 if present but not a positive integer
     */
    public Long queryId(String name) {
        String v = query(name);
        return v == null ? null : parseId(name, v);
    }

    /**
     * Returns a boolean query parameter ({@code true}/{@code false}).
     *
     * @param name parameter name
     * @return TRUE, FALSE or null when missing
     * @throws ApiException 400 if present but not a boolean
     */
    public Boolean queryBool(String name) {
        String v = query(name);
        if (v == null) {
            return null;
        }
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes" -> Boolean.TRUE;
            case "false", "0", "no" -> Boolean.FALSE;
            default -> throw ApiException.badRequest("Query parameter '" + name + "' must be true or false");
        };
    }

    /**
     * Returns a path parameter parsed as a positive id.
     *
     * @param name parameter name from the route pattern, e.g. {@code id} for {@code /projects/{id}}
     * @return id
     * @throws ApiException 400 if not a positive integer
     */
    public long pathId(String name) {
        return parseId(name, pathParams.get(name));
    }

    /**
     * Returns the JSON body as a map.
     *
     * @return parsed object (empty map for an empty body)
     * @throws ApiException 400 if the body is not a JSON object
     */
    public Map<String, Object> json() {
        if (jsonBody == null) {
            if (body == null || body.isBlank()) {
                jsonBody = new LinkedHashMap<>();
            } else {
                try {
                    jsonBody = Json.MAPPER.readValue(body, MAP_TYPE);
                } catch (JsonProcessingException e) {
                    throw new ApiException(400, "MALFORMED_JSON", "Request body must be a JSON object", null);
                }
            }
        }
        return jsonBody;
    }

    /**
     * Returns the authenticated caller.
     *
     * @return principal, or null on public routes
     */
    public Principal principal() {
        return principal;
    }

    void setPathParams(Map<String, String> params) {
        this.pathParams = params;
    }

    void setPrincipal(Principal principal) {
        this.principal = principal;
    }

    private static long parseId(String name, String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException | NullPointerException ignored) {
            // fall through to the error below
        }
        throw ApiException.badRequest("'" + name + "' must be a positive integer");
    }
}
