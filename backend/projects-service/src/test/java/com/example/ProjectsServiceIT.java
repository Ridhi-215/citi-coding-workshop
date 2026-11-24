package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.auth.Principal;
import com.example.common.auth.Tokens;
import com.example.common.db.Db;
import com.example.common.db.Migrations;
import com.example.common.db.Sql;
import com.example.common.http.Router;
import com.example.common.security.Role;
import com.example.common.testing.TestApi;
import com.example.common.testing.TestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs projects-service routes against the local PostgreSQL ({@code acme_test}).
 */
class ProjectsServiceIT {

    private static final Tokens TOKENS = new Tokens("it-secret-it-secret-it-secret-it-secret", Clock.systemUTC());
    private static TestApi api;
    private static String admin;
    private static String manager;
    private static String contributor;
    private static String viewer;

    @BeforeAll
    static void setUp() throws Exception {
        TestDatabase.reset();
        api = new TestApi(Handler.routes(new Router("projects-service", () -> TOKENS, Migrations::ensure)));
        admin = token("admin@acme.com", Role.ADMIN);
        manager = token("manager@acme.com", Role.MANAGER);
        contributor = token("contributor@acme.com", Role.CONTRIBUTOR);
        viewer = token("viewer@acme.com", Role.VIEWER);
    }

    @Test
    void crudWithRoleChecks() throws Exception {
        Map<String, Object> body = project("it-crud");
        api.call("POST", "/projects", viewer, body, 403);
        JsonNode created = api.call("POST", "/api/projects-service/projects", contributor, body, 201);
        long id = created.get("id").asLong();
        assertEquals("IT-CRUD", created.get("code").asText());
        assertEquals("planned", created.get("status").asText());
        assertEquals(0, created.get("budget_spent").decimalValue().signum());

        api.call("GET", "/projects/" + id, viewer, null, 200);
        body.put("status", "active");
        body.put("name", "Renamed");
        JsonNode updated = api.call("PUT", "/projects/" + id, contributor, body, 200);
        assertEquals("Renamed", updated.get("name").asText());

        api.call("DELETE", "/projects/" + id, contributor, null, 403);
        api.call("DELETE", "/projects/" + id, manager, null, 204);
        api.call("GET", "/projects/" + id, viewer, null, 404);
    }

    @Test
    void validatesFields() throws Exception {
        Map<String, Object> body = project("bad code!");
        body.put("end_date", "2020-01-01");
        body.put("budget_planned", -5);
        body.put("status", "done");
        body.put("manager_id", 999999);
        JsonNode err = api.call("POST", "/projects", admin, body, 400);
        JsonNode details = err.at("/error/details");
        for (String field : new String[] {"code", "end_date", "budget_planned", "status", "manager_id"}) {
            assertTrue(details.has(field), field + " in " + details);
        }
        api.call("POST", "/projects", admin, Map.of(), 400);
    }

    @Test
    void duplicateCodeIsRejected() throws Exception {
        api.call("POST", "/projects", admin, project("IT-DUP"), 201);
        JsonNode err = api.call("POST", "/projects", admin, project("it-dup"), 400);
        assertTrue(err.at("/error/details").has("code"));
    }

    @Test
    void expensesDriveBudgetAndRisk() throws Exception {
        Map<String, Object> body = project("IT-BUDGET");
        body.put("status", "active");
        body.put("budget_planned", 1000);
        long id = api.call("POST", "/projects", manager, body, 201).get("id").asLong();

        JsonNode exp = api.call("POST", "/projects/" + id + "/expenses", contributor,
            Map.of("category", "software", "description", "Licences", "amount", "950.50",
                "incurred_on", LocalDate.now().toString()), 201);
        JsonNode p = api.call("GET", "/projects/" + id, viewer, null, 200);
        assertEquals(0, p.get("budget_spent").decimalValue().compareTo(new java.math.BigDecimal("950.50")));
        assertTrue(p.get("at_risk").asBoolean());
        assertEquals("budget_warning", p.get("risk_reasons").get(0).asText());

        api.call("PUT", "/expenses/" + exp.get("id").asLong(), contributor,
            Map.of("category", "software", "description", "Licences", "amount", 1200,
                "incurred_on", LocalDate.now().toString()), 200);
        p = api.call("GET", "/projects/" + id, viewer, null, 200);
        assertEquals("over_budget", p.get("risk_reasons").get(0).asText());
        assertTrue(p.get("budget_used_pct").asDouble() > 100);

        JsonNode page = api.call("GET", "/projects/" + id + "/expenses?category=software", viewer, null, 200);
        assertEquals(1, page.get("total").asInt());
        api.call("POST", "/projects/" + id + "/expenses", contributor,
            Map.of("category", "food", "description", "", "amount", 0), 400);

        api.call("DELETE", "/expenses/" + exp.get("id").asLong(), contributor, null, 403);
        api.call("DELETE", "/expenses/" + exp.get("id").asLong(), manager, null, 204);
        p = api.call("GET", "/projects/" + id, viewer, null, 200);
        assertFalse(p.get("at_risk").asBoolean());
    }

    @Test
    void listFiltersSearchesSortsAndPages() throws Exception {
        Map<String, Object> body = project("IT-ZEBRA");
        body.put("name", "Zebra migration");
        body.put("priority", "critical");
        api.call("POST", "/projects", admin, body, 201);

        JsonNode page = api.call("GET", "/projects?search=zebra&priority=critical", viewer, null, 200);
        assertEquals(1, page.get("total").asInt());
        assertEquals("IT-ZEBRA", page.at("/items/0/code").asText());

        JsonNode sorted = api.call("GET", "/projects?sort=code&order=desc&limit=2", viewer, null, 200);
        assertEquals(2, sorted.get("items").size());
        assertTrue(sorted.at("/items/0/code").asText().compareTo(sorted.at("/items/1/code").asText()) > 0);

        api.call("GET", "/projects?sort=password", viewer, null, 400);
        api.call("GET", "/projects?status=done", viewer, null, 400);
        JsonNode risky = api.call("GET", "/projects?at_risk=true", viewer, null, 200);
        risky.get("items").forEach(p -> assertTrue(p.get("at_risk").asBoolean()));
    }

    private static Map<String, Object> project(String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Project " + code);
        body.put("start_date", LocalDate.now().minusDays(10).toString());
        body.put("end_date", LocalDate.now().plusDays(60).toString());
        body.put("budget_planned", "50000.00");
        return body;
    }

    private static String token(String email, Role role) throws Exception {
        long id = Sql.scalar(Db.get(), "SELECT id FROM users WHERE email = ?", email);
        return TOKENS.issue(new Principal(id, email, email, role), Tokens.ACCESS);
    }
}
