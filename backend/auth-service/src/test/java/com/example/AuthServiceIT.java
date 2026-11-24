package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.auth.Tokens;
import com.example.common.db.Migrations;
import com.example.common.http.Router;
import com.example.common.testing.TestApi;
import com.example.common.testing.TestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs auth-service routes against the local PostgreSQL ({@code acme_test}).
 */
class AuthServiceIT {

    private static final String PASSWORD = "Demo@1234";
    private static final Tokens TOKENS = new Tokens("it-secret-it-secret-it-secret-it-secret", Clock.systemUTC());
    private static TestApi api;

    @BeforeAll
    static void setUp() throws Exception {
        TestDatabase.reset();
        api = new TestApi(Handler.routes(new Router("auth-service", () -> TOKENS, Migrations::ensure)));
    }

    @Test
    void loginReturnsTokensAndPermissions() throws Exception {
        JsonNode body = call("POST", "/api/auth-service/login", null,
            Map.of("email", "Admin@acme.com", "password", PASSWORD), 200);
        assertEquals("admin", body.at("/user/role").asText());
        assertTrue(body.at("/user/permissions").toString().contains("MANAGE_USERS"));
        assertFalse(body.get("user").has("password_hash"));
        assertEquals("admin@acme.com", TOKENS.verify(body.get("access_token").asText(), Tokens.ACCESS).email());
    }

    @Test
    void wrongPasswordAndUnknownEmailAreSame401() throws Exception {
        JsonNode a = call("POST", "/login", null, Map.of("email", "admin@acme.com", "password", "nope"), 401);
        JsonNode b = call("POST", "/login", null, Map.of("email", "ghost@acme.com", "password", "nope"), 401);
        assertEquals("INVALID_CREDENTIALS", a.at("/error/code").asText());
        assertEquals(a, b);
    }

    @Test
    void loginValidatesInput() throws Exception {
        JsonNode body = call("POST", "/login", null, Map.of(), 400);
        assertEquals("VALIDATION_FAILED", body.at("/error/code").asText());
        assertTrue(body.at("/error/details").has("email"));
    }

    @Test
    void registerCreatesViewerAndRejectsDuplicates() throws Exception {
        Map<String, Object> req = Map.of("email", "new.person@acme.com", "password", "Secret123",
            "full_name", "New Person", "role", "admin");
        JsonNode body = call("POST", "/register", null, req, 201);
        assertEquals("viewer", body.at("/user/role").asText());
        JsonNode dup = call("POST", "/register", null, req, 400);
        assertTrue(dup.at("/error/details").has("email"));
    }

    @Test
    void registerRejectsWeakPassword() throws Exception {
        JsonNode body = call("POST", "/register", null,
            Map.of("email", "weak@acme.com", "password", "short", "full_name", "W"), 400);
        assertTrue(body.at("/error/details/password").asText().contains("8"));
    }

    @Test
    void refreshIssuesNewTokensButAccessTokenIsNotAccepted() throws Exception {
        JsonNode login = call("POST", "/login", null, Map.of("email", "viewer@acme.com", "password", PASSWORD), 200);
        JsonNode refreshed = call("POST", "/refresh", null,
            Map.of("refresh_token", login.get("refresh_token").asText()), 200);
        assertEquals("viewer", refreshed.at("/user/role").asText());
        call("POST", "/refresh", null, Map.of("refresh_token", login.get("access_token").asText()), 401);
    }

    @Test
    void meNeedsToken() throws Exception {
        call("GET", "/me", null, null, 401);
        JsonNode me = call("GET", "/me", token("manager@acme.com"), null, 200);
        assertEquals("Mark Manager", me.get("full_name").asText());
    }

    @Test
    void onlyAdminsManageUsers() throws Exception {
        call("GET", "/users", token("manager@acme.com"), null, 403);
        JsonNode page = call("GET", "/users?role=viewer", token("admin@acme.com"), null, 200);
        assertTrue(page.get("total").asInt() >= 1);
        page.get("items").forEach(u -> assertEquals("viewer", u.get("role").asText()));
        call("GET", "/users?role=boss", token("admin@acme.com"), null, 400);
    }

    @Test
    void adminCreatesUpdatesAndDeletesUser() throws Exception {
        String admin = token("admin@acme.com");
        JsonNode created = call("POST", "/users", admin, Map.of("email", "temp.user@acme.com",
            "password", "Temp1234", "full_name", "Temp User", "role", "contributor"), 201);
        long id = created.get("id").asLong();

        JsonNode updated = call("PUT", "/users/" + id, admin, Map.of("role", "manager", "is_active", false), 200);
        assertEquals("manager", updated.get("role").asText());
        call("POST", "/login", null, Map.of("email", "temp.user@acme.com", "password", "Temp1234"), 401);

        call("PUT", "/users/" + id, admin, Map.of("role", "owner"), 400);
        call("DELETE", "/users/" + id, admin, null, 204);
        call("DELETE", "/users/" + id, admin, null, 404);
        call("GET", "/users/" + id, admin, null, 404);
    }

    @Test
    void adminCannotLockThemselvesOut() throws Exception {
        String admin = token("admin@acme.com");
        long self = TOKENS.verify(admin, Tokens.ACCESS).userId();
        call("PUT", "/users/" + self, admin, Map.of("role", "viewer"), 400);
        call("PUT", "/users/" + self, admin, Map.of("is_active", false), 400);
        call("DELETE", "/users/" + self, admin, null, 400);
    }

    private static String token(String email) throws Exception {
        return call("POST", "/login", null, Map.of("email", email, "password", PASSWORD), 200)
            .get("access_token").asText();
    }

    private static JsonNode call(String method, String path, String token, Object body, int expected)
        throws Exception {
        return api.call(method, path, token, body, expected);
    }
}
