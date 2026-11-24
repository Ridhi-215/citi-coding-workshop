package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.http.Responses;
import com.example.common.http.Router;
import com.example.common.security.Permission;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lambda entry point of auth-service (handler name fixed by infra/locals.tf).
 *
 * <p>Public: {@code POST /register}, {@code POST /login}, {@code POST /refresh}.
 * Any signed-in user: {@code GET /me}. Admin only: {@code /users} CRUD.
 */
public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

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

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        return ROUTER.handle(event);
    }
}
