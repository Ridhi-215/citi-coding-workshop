package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.common.http.Router;
import com.example.common.security.Permission;
import java.util.Map;

/**
 * Lambda entry point of deliverables-service (handler name fixed by infra/locals.tf).
 *
 * <p>Deliverables with completion tracking, dependencies between deliverables of the same
 * project (cycles rejected), and dependency chains / graphs.
 */
public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Router ROUTER = routes(new Router("deliverables-service"));

    /**
     * Registers all routes of this service on a router.
     *
     * @param router router to configure
     * @return the same router
     */
    static Router routes(Router router) {
        DeliverablesApi deliverables = new DeliverablesApi();
        DependenciesApi dependencies = new DependenciesApi();
        return router
            .get("/deliverables/graph", Permission.READ, dependencies::graph)
            .get("/deliverables", Permission.READ, deliverables::list)
            .get("/deliverables/{id}", Permission.READ, deliverables::get)
            .post("/deliverables", Permission.CREATE, deliverables::create)
            .put("/deliverables/{id}", Permission.UPDATE, deliverables::update)
            .delete("/deliverables/{id}", Permission.DELETE, deliverables::delete)
            .get("/deliverables/{id}/chain", Permission.READ, dependencies::chain)
            .post("/deliverables/{id}/dependencies", Permission.UPDATE, dependencies::add)
            .delete("/deliverables/{id}/dependencies/{dependsOnId}", Permission.DELETE, dependencies::remove);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        return ROUTER.handle(event);
    }
}
