package com.example.common.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.auth.Principal;
import com.example.common.auth.Tokens;
import com.example.common.security.Permission;
import com.example.common.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RouterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final Tokens tokens = new Tokens(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
    private final AtomicInteger startups = new AtomicInteger();
    private Router router;

    @BeforeEach
    void setUp() {
        router = new Router("projects-service", () -> tokens, startups::incrementAndGet)
            .get("/projects", Permission.READ, req -> Responses.ok(Map.of("user", req.principal().email())))
            .get("/projects/{id}", Permission.READ, req -> Responses.ok(Map.of("id", req.pathId("id"))))
            .delete("/projects/{id}", Permission.DELETE, req -> Responses.noContent())
            .get("/public", null, req -> Responses.ok(Map.of("q", String.valueOf(req.query("q")))))
            .get("/boom", null, req -> {
                throw new IllegalStateException("secret detail");
            })
            .get("/dup", null, req -> {
                throw new SQLException("duplicate", "23505");
            });
    }

    @Test
    void stripsApiPrefixFromCloudFrontPaths() throws Exception {
        Map<String, Object> res = router.handle(event("GET", "/api/projects-service/projects/7", viewerToken()));
        assertEquals(200, res.get("statusCode"));
        assertEquals(7, body(res).get("id").asInt());
    }

    @Test
    void acceptsPathsWithoutPrefixFromLocalProxy() throws Exception {
        Map<String, Object> res = router.handle(event("GET", "/projects/7/", viewerToken()));
        assertEquals(200, res.get("statusCode"));
    }

    @Test
    void setsPrincipalFromToken() throws Exception {
        Map<String, Object> res = router.handle(event("GET", "/projects", viewerToken()));
        assertEquals("viewer@acme.com", body(res).get("user").asText());
    }

    @Test
    void missingTokenIs401() throws Exception {
        Map<String, Object> res = router.handle(event("GET", "/projects", null));
        assertEquals(401, res.get("statusCode"));
        assertEquals("UNAUTHORIZED", body(res).at("/error/code").asText());
    }

    @Test
    void malformedAuthorizationHeaderIs401() throws Exception {
        Map<String, Object> e = event("GET", "/projects", null);
        headers(e).put("Authorization", "Basic abc");
        assertEquals(401, router.handle(e).get("statusCode"));
    }

    @Test
    void expiredTokenIs401WithSpecificCode() throws Exception {
        Tokens old = new Tokens(SECRET, Clock.fixed(NOW.minus(Duration.ofHours(2)), ZoneOffset.UTC));
        String token = old.issue(new Principal(1, "a@acme.com", "A", Role.ADMIN), Tokens.ACCESS);
        Map<String, Object> res = router.handle(event("GET", "/projects", token));
        assertEquals(401, res.get("statusCode"));
        assertEquals("TOKEN_EXPIRED", body(res).at("/error/code").asText());
    }

    @Test
    void refreshTokenCannotCallApi() throws Exception {
        String token = tokens.issue(new Principal(1, "a@acme.com", "A", Role.ADMIN), Tokens.REFRESH);
        assertEquals(401, router.handle(event("GET", "/projects", token)).get("statusCode"));
    }

    @Test
    void roleWithoutPermissionIs403() throws Exception {
        Map<String, Object> res = router.handle(event("DELETE", "/projects/1", viewerToken()));
        assertEquals(403, res.get("statusCode"));
        assertEquals("FORBIDDEN", body(res).at("/error/code").asText());
    }

    @Test
    void managerMayDelete() throws Exception {
        String token = tokens.issue(new Principal(2, "m@acme.com", "M", Role.MANAGER), Tokens.ACCESS);
        assertEquals(204, router.handle(event("DELETE", "/projects/1", token)).get("statusCode"));
    }

    @Test
    void unknownRouteAndWrongMethodAre404() throws Exception {
        assertEquals(404, router.handle(event("GET", "/nope", null)).get("statusCode"));
        assertEquals(404, router.handle(event("PUT", "/public", null)).get("statusCode"));
    }

    @Test
    void invalidIdIs400() throws Exception {
        Map<String, Object> res = router.handle(event("GET", "/projects/abc", viewerToken()));
        assertEquals(400, res.get("statusCode"));
    }

    @Test
    void unexpectedErrorIs500WithoutLeakingDetails() throws Exception {
        Map<String, Object> res = router.handle(event("GET", "/boom", null));
        assertEquals(500, res.get("statusCode"));
        assertEquals("INTERNAL_ERROR", body(res).at("/error/code").asText());
        assertTrue(!res.get("body").toString().contains("secret detail"));
    }

    @Test
    void uniqueViolationIs400Duplicate() throws Exception {
        Map<String, Object> res = router.handle(event("GET", "/dup", null));
        assertEquals(400, res.get("statusCode"));
        assertEquals("DUPLICATE", body(res).at("/error/code").asText());
    }

    @Test
    void nonHttpEventIs400() {
        assertEquals(400, router.handle(Map.of("foo", "bar")).get("statusCode"));
    }

    @Test
    void healthSkipsStartupButOtherRoutesRunItOnce() throws Exception {
        router.handle(event("GET", "/health", null));
        assertEquals(0, startups.get());
        router.handle(event("GET", "/public", null));
        router.handle(event("GET", "/public", null));
        assertEquals(1, startups.get());
    }

    @Test
    void failedStartupIsRetriedOnNextRequest() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Router flaky = new Router("x", () -> tokens, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new SQLException("Aurora resuming", "08001");
            }
        }).get("/ok", null, req -> Responses.ok(Map.of()));
        assertEquals(500, flaky.handle(event("GET", "/ok", null)).get("statusCode"));
        assertEquals(200, flaky.handle(event("GET", "/ok", null)).get("statusCode"));
    }

    @Test
    void passesQueryParameters() throws Exception {
        Map<String, Object> e = event("GET", "/public", null);
        e.put("queryStringParameters", Map.of("q", "  apollo "));
        assertEquals("apollo", body(router.handle(e)).get("q").asText());
    }

    private String viewerToken() {
        return tokens.issue(new Principal(4, "viewer@acme.com", "Victor", Role.VIEWER), Tokens.ACCESS);
    }

    static Map<String, Object> event(String method, String path, String token) {
        Map<String, Object> event = new HashMap<>();
        event.put("rawPath", path);
        event.put("requestContext", Map.of("http", Map.of("method", method, "path", path)));
        Map<String, Object> headers = new HashMap<>();
        if (token != null) {
            headers.put("authorization", "Bearer " + token);
        }
        event.put("headers", headers);
        return event;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> headers(Map<String, Object> event) {
        return (Map<String, Object>) event.get("headers");
    }

    private static JsonNode body(Map<String, Object> res) throws Exception {
        return Json.MAPPER.readTree(res.get("body").toString());
    }
}
