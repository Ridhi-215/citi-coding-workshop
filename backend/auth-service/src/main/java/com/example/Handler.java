package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.common.db.Db;
import com.example.common.db.Migrations;
import com.example.common.db.Sql;
import com.example.common.http.Responses;
import com.example.common.http.Router;
import com.example.common.security.Permission;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lambda entry point of auth-service (handler name fixed by infra/locals.tf).
 *
 * <p>Public: {@code POST /register}, {@code POST /login}, {@code POST /refresh}.
 * Any signed-in user: {@code GET /me}. Admin only: {@code /users} CRUD.
 */
public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger LOG = Logger.getLogger(Handler.class.getName());

    private static final Router ROUTER = routes(new Router("auth-service"));

    /**
     * Registers all routes of this service on a router (tests pass a router with a fixed clock).
     *
     * @param router router to configure
     * @return the same router
     */
    static Router routes(Router router) {
        AuthApi auth = new AuthApi(router::tokens);
        UsersApi users = new UsersApi();
        return router
            .get("/health/db", null, req -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", "ok");
                body.put("database", Sql.scalar(Db.get(), "SELECT 1") == 1 ? "ok" : "error");
                return Responses.ok(body);
            })
            .post("/register", null, auth::register)
            .post("/login", null, auth::login)
            .post("/refresh", null, auth::refresh)
            .get("/me", Permission.READ, auth::me)
            .get("/users", Permission.MANAGE_USERS, users::list)
            .get("/users/{id}", Permission.MANAGE_USERS, users::get)
            .post("/users", Permission.MANAGE_USERS, users::create)
            .put("/users/{id}", Permission.MANAGE_USERS, users::update)
            .delete("/users/{id}", Permission.MANAGE_USERS, users::delete);
    }

    /** Event key of maintenance actions; Function URL (HTTP) events can never contain it. */
    static final String MAINTENANCE = "maintenance";

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        if (event != null && event.containsKey(MAINTENANCE)) {
            return maintenance(event.get(MAINTENANCE));
        }
        return ROUTER.handle(event);
    }

    /**
     * Operator actions, run with {@code aws lambda invoke} (needs IAM permission to invoke the
     * function; the public Function URL wraps every request in an HTTP event instead).
     * {@code {"maintenance": "reset-demo-data"}} wipes all data and reloads the demo seed.
     */
    static Map<String, Object> maintenance(Object action) {
        if (!"reset-demo-data".equals(action)) {
            return Map.of("status", "error", "message", "Unknown maintenance action");
        }
        try {
            Migrations.resetDemoData(Db.get());
            return Map.of("status", "ok", "action", "reset-demo-data");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Demo data reset failed", e);
            return Map.of("status", "error", "message", "Reset failed; nothing was changed");
        }
    }
}
