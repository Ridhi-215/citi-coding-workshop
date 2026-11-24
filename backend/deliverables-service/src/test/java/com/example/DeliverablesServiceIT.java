package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.auth.Tokens;
import com.example.common.db.Db;
import com.example.common.db.Migrations;
import com.example.common.db.Sql;
import com.example.common.http.Router;
import com.example.common.testing.TestApi;
import com.example.common.testing.TestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs deliverables-service routes against the local PostgreSQL ({@code acme_test}).
 */
class DeliverablesServiceIT {

    private static final Tokens TOKENS = new Tokens("it-secret-it-secret-it-secret-it-secret", Clock.systemUTC());
    private static TestApi api;
    private static String manager;
    private static String contributor;
    private static String viewer;
    private static long projectId;

    @BeforeAll
    static void setUp() throws Exception {
        TestDatabase.reset();
        api = new TestApi(Handler.routes(new Router("deliverables-service", () -> TOKENS, Migrations::ensure)));
        manager = TestApi.tokenFor(TOKENS, "manager@acme.com");
        contributor = TestApi.tokenFor(TOKENS, "contributor@acme.com");
        viewer = TestApi.tokenFor(TOKENS, "viewer@acme.com");
        projectId = Sql.scalar(Db.get(), "INSERT INTO projects (code, name, start_date, end_date)"
            + " VALUES ('IT-DEL', 'Deliverables IT', CURRENT_DATE - 30, CURRENT_DATE + 90) RETURNING id");
    }

    @Test
    void crudWithRolesAndCompletionTimestamp() throws Exception {
        api.call("POST", "/deliverables", viewer, deliverable("Viewer cannot", 5), 403);
        JsonNode created = api.call("POST", "/api/deliverables-service/deliverables", contributor,
            deliverable("Spec", 5), 201);
        long id = created.get("id").asLong();
        assertEquals("IT-DEL", created.get("project_code").asText());
        assertTrue(created.get("completed_at").isNull());

        Map<String, Object> body = deliverable("Spec v2", 5);
        body.put("status", "completed");
        JsonNode done = api.call("PUT", "/deliverables/" + id, contributor, body, 200);
        assertEquals("completed", done.get("status").asText());
        assertFalse(done.get("completed_at").isNull());

        api.call("DELETE", "/deliverables/" + id, contributor, null, 403);
        api.call("DELETE", "/deliverables/" + id, manager, null, 204);
        api.call("GET", "/deliverables/" + id, viewer, null, 404);
    }

    @Test
    void validatesInput() throws Exception {
        JsonNode err = api.call("POST", "/deliverables", manager,
            Map.of("project_id", 999999, "status", "done", "owner_id", 999999), 400);
        JsonNode details = err.at("/error/details");
        for (String field : List.of("project_id", "status", "owner_id", "name", "due_date")) {
            assertTrue(details.has(field), field + " in " + details);
        }
    }

    @Test
    void overdueFilterAndSearch() throws Exception {
        long late = create("Late item", -3);
        create("Future item", 30);
        JsonNode page = api.call("GET", "/deliverables?project_id=" + projectId + "&overdue=true", viewer, null, 200);
        assertTrue(page.get("items").findValuesAsText("id").contains(String.valueOf(late)));
        page.get("items").forEach(d -> assertTrue(d.get("is_overdue").asBoolean()));
        JsonNode search = api.call("GET", "/deliverables?search=future&project_id=" + projectId, viewer, null, 200);
        assertEquals(1, search.get("total").asInt());
    }

    @Test
    void dependencyChainsAndCycleDetection() throws Exception {
        long a = create("A design", 10);
        long b = create("B build", 20);
        long c = create("C test", 30);
        api.call("POST", "/deliverables/" + b + "/dependencies", contributor, Map.of("depends_on_id", a), 201);
        api.call("POST", "/deliverables/" + c + "/dependencies", contributor, Map.of("depends_on_id", b), 201);

        // C -> B -> A: making A depend on C would close a cycle.
        JsonNode cycle = api.call("POST", "/deliverables/" + a + "/dependencies", contributor,
            Map.of("depends_on_id", c), 400);
        assertTrue(cycle.at("/error/details/depends_on_id").asText().contains("circular"));
        api.call("POST", "/deliverables/" + a + "/dependencies", contributor, Map.of("depends_on_id", a), 400);
        api.call("POST", "/deliverables/" + c + "/dependencies", contributor, Map.of("depends_on_id", b), 400);

        JsonNode chain = api.call("GET", "/deliverables/" + c + "/chain", viewer, null, 200);
        assertEquals(2, chain.get("upstream").size());
        assertEquals(b, chain.at("/upstream/0/id").asLong());
        assertEquals(2, chain.at("/upstream/1/depth").asInt());
        assertEquals(2, chain.get("blocking").size());
        assertEquals(2, api.call("GET", "/deliverables/" + a + "/chain", viewer, null, 200).get("downstream").size());

        // C cannot be completed while B and A are open.
        Map<String, Object> done = deliverable("C test", 30);
        done.put("status", "completed");
        api.call("PUT", "/deliverables/" + c, contributor, done, 400);

        JsonNode graph = api.call("GET", "/deliverables/graph?project_id=" + projectId, viewer, null, 200);
        assertTrue(graph.get("edges").size() >= 2);
        api.call("GET", "/deliverables/graph", viewer, null, 400);

        JsonNode detail = api.call("GET", "/deliverables/" + b, viewer, null, 200);
        assertEquals(a, detail.at("/depends_on/0/id").asLong());
        assertEquals(c, detail.at("/dependents/0/id").asLong());

        api.call("DELETE", "/deliverables/" + c + "/dependencies/" + b, contributor, null, 403);
        api.call("DELETE", "/deliverables/" + c + "/dependencies/" + b, manager, null, 204);
        api.call("DELETE", "/deliverables/" + c + "/dependencies/" + b, manager, null, 404);
    }

    @Test
    void dependenciesMustStayInsideOneProject() throws Exception {
        long other = Sql.scalar(Db.get(), "SELECT id FROM deliverables WHERE project_id <> ? LIMIT 1", projectId);
        long mine = create("Mine", 10);
        JsonNode err = api.call("POST", "/deliverables/" + mine + "/dependencies", contributor,
            Map.of("depends_on_id", other), 400);
        assertTrue(err.at("/error/details/depends_on_id").asText().contains("same project"));
    }

    private static long create(String name, int dueInDays) throws Exception {
        return api.call("POST", "/deliverables", manager, deliverable(name, dueInDays), 201).get("id").asLong();
    }

    private static Map<String, Object> deliverable(String name, int dueInDays) {
        Map<String, Object> body = new HashMap<>();
        body.put("project_id", projectId);
        body.put("name", name);
        body.put("due_date", LocalDate.now().plusDays(dueInDays).toString());
        return body;
    }
}
