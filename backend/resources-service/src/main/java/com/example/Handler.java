package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.common.http.Router;
import com.example.common.security.Permission;
import java.util.Map;

/**
 * Lambda entry point of resources-service (handler name fixed by infra/locals.tf).
 *
 * <p>Team members with their current allocation, and allocations of members to projects.
 */
public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Router ROUTER = routes(new Router("resources-service"));

    /**
     * Registers all routes of this service on a router.
     *
     * @param router router to configure
     * @return the same router
     */
    static Router routes(Router router) {
        MembersApi members = new MembersApi();
        AllocationsApi allocations = new AllocationsApi();
        return router
            .get("/members", Permission.READ, members::list)
            .get("/members/{id}", Permission.READ, members::get)
            .post("/members", Permission.CREATE, members::create)
            .put("/members/{id}", Permission.UPDATE, members::update)
            .delete("/members/{id}", Permission.DELETE, members::delete)
            .get("/allocations", Permission.READ, allocations::list)
            .post("/allocations", Permission.CREATE, allocations::create)
            .put("/allocations/{id}", Permission.UPDATE, allocations::update)
            .delete("/allocations/{id}", Permission.DELETE, allocations::delete);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        return ROUTER.handle(event);
    }
}
