package com.example.common.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.common.auth.Principal;
import com.example.common.auth.Tokens;
import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.http.Json;
import com.example.common.security.Role;
import com.example.common.http.Router;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends Function URL style events (payload v2) to a router and checks the status code.
 * Shared with the services' integration tests through the common-lib test jar.
 */
public final class TestApi {

    private final Router router;

    /**
     * @param router router under test
     */
    public TestApi(Router router) {
        this.router = router;
    }

    /**
     * Calls a route and asserts the status code.
     *
     * @param method        HTTP method
     * @param pathAndQuery  path, optionally with {@code ?a=1&b=2} (values are not URL-decoded)
     * @param token         bearer token or null
     * @param body          object serialized as the JSON body, or null
     * @param expected      expected status code
     * @return parsed response body, or null when empty
     * @throws Exception on JSON errors
     */
    public JsonNode call(String method, String pathAndQuery, String token, Object body, int expected)
        throws Exception {
        String path = pathAndQuery;
        Map<String, String> query = new HashMap<>();
        int q = pathAndQuery.indexOf('?');
        if (q >= 0) {
            path = pathAndQuery.substring(0, q);
            for (String pair : pathAndQuery.substring(q + 1).split("&")) {
                String[] kv = pair.split("=", 2);
                query.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
        }
        Map<String, Object> event = new HashMap<>();
        event.put("rawPath", path);
        event.put("requestContext", Map.of("http", Map.of("method", method, "path", path)));
        event.put("queryStringParameters", query);
        Map<String, Object> headers = new HashMap<>();
        if (token != null) {
            headers.put("authorization", "Bearer " + token);
        }
        event.put("headers", headers);
        if (body != null) {
            event.put("body", Json.write(body));
        }
        Map<String, Object> res = router.handle(event);
        String text = res.get("body") == null ? "" : res.get("body").toString();
        assertEquals(expected, res.get("statusCode"), method + " " + pathAndQuery + " -> " + text);
        return text.isEmpty() ? null : Json.MAPPER.readTree(text);
    }

    /**
     * Issues an access token for a seeded user, using the role stored in the database.
     *
     * @param tokens token issuer
     * @param email  user email
     * @return bearer token
     * @throws Exception if the user does not exist
     */
    public static String tokenFor(Tokens tokens, String email) throws Exception {
        Map<String, Object> u = Sql.queryOne(Db.get(), "SELECT id, full_name, role FROM users WHERE email = ?", email)
            .orElseThrow();
        return tokens.issue(new Principal(((Number) u.get("id")).longValue(), email, (String) u.get("full_name"),
            Role.from((String) u.get("role"))), Tokens.ACCESS);
    }
}
