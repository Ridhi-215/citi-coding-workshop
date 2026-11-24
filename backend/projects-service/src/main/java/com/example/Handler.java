package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.common.http.Router;
import com.example.common.security.Permission;
import java.util.Map;

/**
 * Lambda entry point of projects-service (handler name fixed by infra/locals.tf).
 *
 * <p>Projects with budget and risk roll-ups, and their expenses. Reads need any role;
 * create/update need contributor; delete needs manager or admin.
 */
public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Router ROUTER = routes(new Router("projects-service"));

    /**
     * Registers all routes of this service on a router.
     *
     * @param router router to configure
     * @return the same router
     */
    static Router routes(Router router) {
        ProjectsApi projects = new ProjectsApi();
        ExpensesApi expenses = new ExpensesApi();
        return router
            .get("/projects", Permission.READ, projects::list)
            .get("/projects/{id}", Permission.READ, projects::get)
            .post("/projects", Permission.CREATE, projects::create)
            .put("/projects/{id}", Permission.UPDATE, projects::update)
            .delete("/projects/{id}", Permission.DELETE, projects::delete)
            .get("/projects/{id}/expenses", Permission.READ, expenses::list)
            .post("/projects/{id}/expenses", Permission.CREATE, expenses::create)
            .put("/expenses/{id}", Permission.UPDATE, expenses::update)
            .delete("/expenses/{id}", Permission.DELETE, expenses::delete);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        return ROUTER.handle(event);
    }
}
