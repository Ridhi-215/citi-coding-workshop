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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        JsonNode dup = call("POST", "/register", null, req, 409);
        assertEquals("EMAIL_TAKEN", dup.at("/error/code").asText());
        assertTrue(dup.at("/error/details").has("email"));
        // same email in different case is still a duplicate
        call("POST", "/register", null, Map.of("email", "NEW.Person@acme.com", "password", "Secret123",
            "full_name", "Again"), 409);
        // the new account can sign in and stays a viewer even though "role": "admin" was sent
        JsonNode login = call("POST", "/login", null, Map.of("email", "new.person@acme.com", "password", "Secret123"), 200);
        assertEquals("viewer", login.at("/user/role").asText());
        assertEquals("[\"READ\"]", login.at("/user/permissions").toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "lettersonly", "12345678"})
    void registerEnforcesPasswordRules(String password) throws Exception {
        JsonNode body = call("POST", "/register", null,
            Map.of("email", "weak@acme.com", "password", password, "full_name", "W"), 400);
        assertTrue(body.at("/error/details").has("password"), body.toString());
    }

    @Test
    void registerValidatesEveryField() throws Exception {
        JsonNode body = call("POST", "/register", null, Map.of("email", "not-an-email"), 400);
        JsonNode details = body.at("/error/details");
        assertTrue(details.has("email") && details.has("password") && details.has("full_name"), details.toString());
    }

    @Test
    void healthDbIsPublicButRevealsNoData() throws Exception {
        JsonNode body = call("GET", "/health/db", null, null, 200);
        assertEquals("ok", body.get("database").asText());
        assertFalse(body.has("users"));
    }

    @Test
    void adminCreatingDuplicateEmailGets409() throws Exception {
        JsonNode err = call("POST", "/users", token("admin@acme.com"), Map.of("email", "viewer@acme.com",
            "password", "Secret123", "full_name", "Dup"), 409);
        assertEquals("EMAIL_TAKEN", err.at("/error/code").asText());
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

    @ParameterizedTest
    @ValueSource(strings = {"manager@acme.com", "contributor@acme.com", "viewer@acme.com"})
    void nonAdminsGet403OnEveryUsersEndpoint(String email) throws Exception {
        String t = token(email);
        long adminId = TOKENS.verify(token("admin@acme.com"), Tokens.ACCESS).userId();
        Map<String, Object> body = Map.of("email", "x@acme.com", "password", "Secret123", "full_name", "X");
        for (String[] route : new String[][] {
            {"GET", "/users"}, {"GET", "/users/" + adminId}, {"POST", "/users"},
            {"PUT", "/users/" + adminId}, {"DELETE", "/users/" + adminId}}) {
            JsonNode err = call(route[0], "/api/auth-service" + route[1], t, "GET".equals(route[0]) ? null : body, 403);
            assertEquals("FORBIDDEN", err.at("/error/code").asText());
        }
        call("PUT", "/users/" + adminId, t, Map.of("role", "viewer"), 403);
    }

    @Test
    void lastActiveAdminCannotBeDemotedDeactivatedOrDeletedByAnotherAdmin() throws Exception {
        String admin = token("admin@acme.com");
        long firstId = TOKENS.verify(admin, Tokens.ACCESS).userId();
        long secondId = call("POST", "/users", admin, Map.of("email", "second.admin@acme.com",
            "password", "Second123", "full_name", "Second Admin", "role", "admin"), 201).get("id").asLong();
        String second = token("second.admin@acme.com", "Second123");

        // Two admins: the second may demote the first, and the first is then a manager.
        call("PUT", "/users/" + firstId, second, Map.of("role", "manager"), 200);
        // Now the second is the only admin and nobody can remove that role.
        call("PUT", "/users/" + secondId, second, Map.of("role", "viewer"), 400);
        call("PUT", "/users/" + secondId, second, Map.of("is_active", false), 400);
        call("DELETE", "/users/" + secondId, second, null, 400);

        // Restore the seed admin and remove the extra one.
        call("PUT", "/users/" + firstId, second, Map.of("role", "admin"), 200);
        call("DELETE", "/users/" + secondId, token("admin@acme.com"), null, 204);
    }

    @Test
    void deactivatedUserCannotRefresh() throws Exception {
        String admin = token("admin@acme.com");
        long id = call("POST", "/users", admin, Map.of("email", "leaver@acme.com", "password", "Leaver123",
            "full_name", "Leaver"), 201).get("id").asLong();
        String refresh = call("POST", "/login", null, Map.of("email", "leaver@acme.com", "password", "Leaver123"), 200)
            .get("refresh_token").asText();
        call("PUT", "/users/" + id, admin, Map.of("is_active", false), 200);
        call("POST", "/refresh", null, Map.of("refresh_token", refresh), 401);
    }

    private static String token(String email, String password) throws Exception {
        return call("POST", "/login", null, Map.of("email", email, "password", password), 200)
            .get("access_token").asText();
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
