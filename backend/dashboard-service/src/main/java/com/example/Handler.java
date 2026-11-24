package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.common.http.Router;
import com.example.common.security.Permission;
import java.util.Map;

/**
 * Lambda entry point of dashboard-service (handler name fixed by infra/locals.tf). Read-only.
 */
public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Router ROUTER = routes(new Router("dashboard-service"));

    /**
     * Registers all routes of this service on a router.
     *
     * @param router router to configure
     * @return the same router
     */
    static Router routes(Router router) {
        DashboardApi dashboard = new DashboardApi();
        return router
            .get("/summary", Permission.READ, dashboard::summary)
            .get("/projects/{id}/health", Permission.READ, dashboard::projectHealth);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        return ROUTER.handle(event);
    }
}
