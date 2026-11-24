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
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs resources-service routes against the local PostgreSQL ({@code acme_test}).
 */
class ResourcesServiceIT {

    private static final Tokens TOKENS = new Tokens("it-secret-it-secret-it-secret-it-secret", Clock.systemUTC());
    private static TestApi api;
    private static String manager;
    private static String contributor;
    private static String viewer;

    @BeforeAll
    static void setUp() throws Exception {
        TestDatabase.reset();
        api = new TestApi(Handler.routes(new Router("resources-service", () -> TOKENS, Migrations::ensure)));
        manager = TestApi.tokenFor(TOKENS, "manager@acme.com");
        contributor = TestApi.tokenFor(TOKENS, "contributor@acme.com");
        viewer = TestApi.tokenFor(TOKENS, "viewer@acme.com");
    }

    @Test
    void seededOverAllocatedMembersAreReported() throws Exception {
        JsonNode page = api.call("GET", "/api/resources-service/members?over_allocated=true&sort=current_allocation_pct&order=desc",
            viewer, null, 200);
        assertTrue(page.get("total").asInt() >= 3, page.toString());
        assertEquals("Sofia Martinez", page.at("/items/0/full_name").asText());
        assertEquals(130, page.at("/items/0/current_allocation_pct").asInt());
    }

    @Test
    void memberCrudWithRolesAndValidation() throws Exception {
        Map<String, Object> body = member("it.person@acme.com");
        api.call("POST", "/members", viewer, body, 403);
        long id = api.call("POST", "/members", contributor, body, 201).get("id").asLong();
        JsonNode dup = api.call("POST", "/members", contributor, body, 400);
        assertTrue(dup.at("/error/details").has("email"));
        api.call("POST", "/members", contributor, Map.of("email", "bad", "employment_type", "intern"), 400);

        body.put("job_title", "Lead");
        assertEquals("Lead", api.call("PUT", "/members/" + id, contributor, body, 200).get("job_title").asText());
        JsonNode search = api.call("GET", "/members?search=it.person", viewer, null, 200);
        assertEquals(1, search.get("total").asInt());

        api.call("DELETE", "/members/" + id, contributor, null, 403);
        api.call("DELETE", "/members/" + id, manager, null, 204);
        api.call("GET", "/members/" + id, viewer, null, 404);
    }

    @Test
    void allocationsReportOverAllocation() throws Exception {
        long member = api.call("POST", "/members", manager, member("it.alloc@acme.com"), 201).get("id").asLong();
        long p1 = project("IT-RA1");
        long p2 = project("IT-RA2");

        JsonNode first = api.call("POST", "/allocations", contributor, allocation(member, p1, 70), 201);
        assertEquals(70, first.get("member_peak_allocation_pct").asInt());
        assertFalse(first.get("member_over_allocated").asBoolean());

        JsonNode second = api.call("POST", "/allocations", contributor, allocation(member, p2, 50), 201);
        assertEquals(120, second.get("member_peak_allocation_pct").asInt());
        assertTrue(second.get("member_over_allocated").asBoolean());

        JsonNode m = api.call("GET", "/members/" + member, viewer, null, 200);
        assertEquals(120, m.get("current_allocation_pct").asInt());
        assertTrue(m.get("over_allocated").asBoolean());
        assertEquals(2, m.get("allocations").size());

        Map<String, Object> lower = allocation(member, p2, 30);
        JsonNode updated = api.call("PUT", "/allocations/" + second.get("id").asLong(), contributor, lower, 200);
        assertEquals(100, updated.get("member_peak_allocation_pct").asInt());

        JsonNode list = api.call("GET", "/allocations?member_id=" + member + "&current=true", viewer, null, 200);
        assertEquals(2, list.get("total").asInt());

        // duplicate (same member, project, start date) and invalid values
        api.call("POST", "/allocations", contributor, allocation(member, p1, 10), 400);
        Map<String, Object> bad = allocation(member, p1, 150);
        bad.put("end_date", LocalDate.now().minusDays(30).toString());
        JsonNode err = api.call("POST", "/allocations", contributor, bad, 400);
        assertTrue(err.at("/error/details").has("allocation_percent"));
        assertTrue(err.at("/error/details").has("end_date"));

        api.call("DELETE", "/allocations/" + first.get("id").asLong(), contributor, null, 403);
        api.call("DELETE", "/allocations/" + first.get("id").asLong(), manager, null, 204);
    }

    private static long project(String code) throws Exception {
        return Sql.scalar(Db.get(), "INSERT INTO projects (code, name, start_date, end_date)"
            + " VALUES (?, ?, CURRENT_DATE - 10, CURRENT_DATE + 60) RETURNING id", code, code);
    }

    private static Map<String, Object> member(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("full_name", "IT Person");
        body.put("email", email);
        body.put("department", "Engineering");
        return body;
    }

    private static Map<String, Object> allocation(long member, long project, int pct) {
        Map<String, Object> body = new HashMap<>();
        body.put("team_member_id", member);
        body.put("project_id", project);
        body.put("allocation_percent", pct);
        body.put("start_date", LocalDate.now().minusDays(5).toString());
        body.put("end_date", LocalDate.now().plusDays(30).toString());
        return body;
    }
}
