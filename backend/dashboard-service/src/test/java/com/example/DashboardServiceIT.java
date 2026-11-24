package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.auth.Tokens;
import com.example.common.db.Migrations;
import com.example.common.http.Router;
import com.example.common.testing.TestApi;
import com.example.common.testing.TestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks the dashboard metrics against the demo seed data in the local PostgreSQL.
 */
class DashboardServiceIT {

    private static final Tokens TOKENS = new Tokens("it-secret-it-secret-it-secret-it-secret", Clock.systemUTC());
    private static TestApi api;
    private static String viewer;

    @BeforeAll
    static void setUp() throws Exception {
        TestDatabase.reset();
        api = new TestApi(Handler.routes(new Router("dashboard-service", () -> TOKENS, Migrations::ensure)));
        viewer = TestApi.tokenFor(TOKENS, "viewer@acme.com");
    }

    @Test
    void summaryAnswersTheBusinessQuestions() throws Exception {
        JsonNode s = api.call("GET", "/api/dashboard-service/summary", viewer, null, 200);
        JsonNode kpis = s.get("kpis");
        assertEquals(4, kpis.get("active_projects").asInt());
        assertEquals(2, kpis.get("at_risk_projects").asInt());
        assertEquals(3, kpis.get("over_allocated_members").asInt());
        assertTrue(kpis.get("budget_spent").decimalValue().signum() > 0);

        assertEquals("ORION", s.at("/at_risk_projects/0/code").asText());
        assertEquals("Sofia Martinez", s.at("/over_allocated_members/0/full_name").asText());
        assertTrue(s.at("/over_allocated_members/0/allocations").asText().contains("APOLLO 80%"));
        assertTrue(codes(s.get("budget_by_project")).containsAll(List.of("APOLLO", "ORION", "HERMES", "ATLAS", "NOVA")));
        assertTrue(s.get("overdue_deliverables").size() >= 3);
        assertTrue(names(s.get("blocking_deliverables")).contains("ETL pipelines"));
        assertTrue(s.get("projects_by_status").size() >= 4);
        assertTrue(s.get("deliverables_by_status").size() >= 3);
    }

    @Test
    void projectHealthAndErrors() throws Exception {
        JsonNode summary = api.call("GET", "/summary", viewer, null, 200);
        long orion = summary.at("/at_risk_projects/0/id").asLong();
        JsonNode h = api.call("GET", "/projects/" + orion + "/health", viewer, null, 200);
        assertEquals("ORION", h.at("/project/code").asText());
        assertEquals(4, h.get("deliverables").size());
        assertEquals(3, h.get("dependencies").size());
        assertTrue(h.get("team").size() >= 3);
        assertTrue(h.get("expenses_by_category").size() >= 2);
        api.call("GET", "/projects/999999/health", viewer, null, 404);
        api.call("GET", "/summary", null, null, 401);
    }

    private static List<String> codes(JsonNode rows) {
        List<String> out = new ArrayList<>();
        rows.forEach(r -> out.add(r.get("code").asText()));
        return out;
    }

    private static List<String> names(JsonNode rows) {
        List<String> out = new ArrayList<>();
        rows.forEach(r -> out.add(r.get("name").asText()));
        return out;
    }
}
