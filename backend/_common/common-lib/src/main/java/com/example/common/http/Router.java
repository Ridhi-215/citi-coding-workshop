package com.example.common.http;

import com.example.common.auth.Principal;
import com.example.common.auth.Tokens;
import com.example.common.db.Migrations;
import com.example.common.security.Permission;
import com.example.common.security.Permissions;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal HTTP router for a Lambda Function URL service.
 *
 * <p>Responsibilities, in order:
 * <ol>
 *   <li>Parse the event and strip an optional {@code /api/<service>} prefix (CloudFront
 *       forwards the full path; the local dev proxy strips it).</li>
 *   <li>Match {@code METHOD /path/{param}} routes.</li>
 *   <li>For protected routes: verify the Bearer access token (401) and the role's
 *       permission (403).</li>
 *   <li>Run database migrations once per container before the first database route.</li>
 *   <li>Map every exception to the shared JSON error format.</li>
 * </ol>
 */
public final class Router {

    private static final Logger LOG = Logger.getLogger(Router.class.getName());

    /** Hook run once before the first routed request (migrations by default). */
    @FunctionalInterface
    public interface Startup {
        /**
         * Runs the hook.
         *
         * @throws Exception on failure; the request fails with 500 and the hook runs again next time
         */
        void run() throws Exception;
    }

    private record RouteDef(String method, String[] segments, Permission permission, Route route) {
    }

    private final String prefix;
    private final Supplier<Tokens> tokensSupplier;
    private final Startup startup;
    private final List<RouteDef> routes = new ArrayList<>();
    private Tokens tokens;
    private boolean started;

    /**
     * Creates a router for a deployed service, using {@code JWT_SECRET} and running migrations.
     *
     * @param serviceName folder name of the service, e.g. {@code projects-service}
     */
    public Router(String serviceName) {
        this(serviceName, Tokens::fromEnv, Migrations::ensure);
    }

    /**
     * Creates a router with explicit dependencies (used by tests).
     *
     * @param serviceName    folder name of the service
     * @param tokensSupplier creates the token verifier on first use
     * @param startup        hook run once before the first request
     */
    public Router(String serviceName, Supplier<Tokens> tokensSupplier, Startup startup) {
        this.prefix = "/api/" + serviceName;
        this.tokensSupplier = tokensSupplier;
        this.startup = startup;
        get("/health", null, req -> Responses.ok(Map.of("status", "ok", "service", serviceName)));
    }

    /**
     * Registers a GET route.
     *
     * @param pattern    path pattern, e.g. {@code /projects/{id}}
     * @param permission required permission, or null for a public route
     * @param route      handler
     * @return this router
     */
    public Router get(String pattern, Permission permission, Route route) {
        return add("GET", pattern, permission, route);
    }

    /**
     * Registers a POST route.
     *
     * @param pattern    path pattern
     * @param permission required permission, or null for a public route
     * @param route      handler
     * @return this router
     */
    public Router post(String pattern, Permission permission, Route route) {
        return add("POST", pattern, permission, route);
    }

    /**
     * Registers a PUT route.
     *
     * @param pattern    path pattern
     * @param permission required permission, or null for a public route
     * @param route      handler
     * @return this router
     */
    public Router put(String pattern, Permission permission, Route route) {
        return add("PUT", pattern, permission, route);
    }

    /**
     * Registers a DELETE route.
     *
     * @param pattern    path pattern
     * @param permission required permission, or null for a public route
     * @param route      handler
     * @return this router
     */
    public Router delete(String pattern, Permission permission, Route route) {
        return add("DELETE", pattern, permission, route);
    }

    /**
     * Handles a Lambda event end to end. Never throws.
     *
     * @param event raw Lambda Function URL event
     * @return Lambda response map
     */
    public Map<String, Object> handle(Map<String, Object> event) {
        try {
            Request request = Request.fromEvent(event);
            return dispatch(request);
        } catch (ApiException e) {
            return Responses.error(e.status(), e.code(), e.getMessage(), e.details());
        } catch (SQLException e) {
            return sqlError(e);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unhandled error", e);
            return Responses.error(500, "INTERNAL_ERROR", "An unexpected error occurred", null);
        }
    }

    private Map<String, Object> dispatch(Request request) throws Exception {
        String path = normalize(request.path());
        String[] parts = split(path);

        for (RouteDef def : routes) {
            if (!def.method().equals(request.method())) {
                continue;
            }
            Map<String, String> params = match(def.segments(), parts);
            if (params == null) {
                continue;
            }
            request.setPathParams(params);
            if (def.permission() != null) {
                Principal principal = authenticate(request);
                Permissions.require(principal, def.permission());
                request.setPrincipal(principal);
            }
            ensureStarted(path);
            return def.route().handle(request);
        }
        throw new ApiException(404, "NOT_FOUND", "No route for " + request.method() + " " + path, null);
    }

    private Principal authenticate(Request request) {
        String header = request.header("authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() <= 7) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Authentication is required");
        }
        if (tokens == null) {
            tokens = tokensSupplier.get();
        }
        return tokens.verify(header.substring(7).trim(), Tokens.ACCESS);
    }

    private synchronized void ensureStarted(String path) throws Exception {
        if (started || "/health".equals(path)) {
            return;
        }
        startup.run();
        started = true;
    }

    private Router add(String method, String pattern, Permission permission, Route route) {
        routes.add(new RouteDef(method, split(pattern), permission, route));
        return this;
    }

    private String normalize(String rawPath) {
        String path = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
        if (path.equals(prefix) || path.startsWith(prefix + "/")) {
            path = path.substring(prefix.length());
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/" : path;
    }

    private static String[] split(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    private static Map<String, String> match(String[] pattern, String[] parts) {
        if (pattern.length != parts.length) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            String p = pattern[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                params.put(p.substring(1, p.length() - 1), parts[i]);
            } else if (!p.equals(parts[i])) {
                return null;
            }
        }
        return params;
    }

    /**
     * Maps PostgreSQL errors to client errors where the client caused them.
     * Codes: https://www.postgresql.org/docs/current/errcodes-appendix.html
     */
    private static Map<String, Object> sqlError(SQLException e) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        return switch (state) {
            case "23505" -> Responses.error(400, "DUPLICATE", "A record with the same unique value already exists", null);
            case "23503" -> Responses.error(400, "INVALID_REFERENCE", "A referenced record does not exist", null);
            case "23514", "23502" -> Responses.error(400, "CONSTRAINT_VIOLATION", "A value breaks a data rule", null);
            default -> {
                if (state.startsWith("22")) {
                    yield Responses.error(400, "INVALID_VALUE", "A value has the wrong format", null);
                }
                LOG.log(Level.SEVERE, "Database error " + state.toLowerCase(Locale.ROOT), e);
                yield Responses.error(500, "DATABASE_ERROR", "A database error occurred", null);
            }
        };
    }
}
