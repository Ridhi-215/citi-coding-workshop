package com.example.common.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestTest {

    private static Request withQuery(String... kv) {
        Map<String, String> q = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            q.put(kv[i], kv[i + 1]);
        }
        return new Request("GET", "/x", q, Map.of(), null);
    }

    @Test
    void parsesFunctionUrlEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("rawPath", "/api/svc/items");
        event.put("requestContext", Map.of("http", Map.of("method", "post", "path", "/ignored")));
        event.put("headers", Map.of("Content-Type", "application/json"));
        event.put("queryStringParameters", Map.of("q", "x"));
        event.put("body", Base64.getEncoder().encodeToString("{\"a\":1}".getBytes(StandardCharsets.UTF_8)));
        event.put("isBase64Encoded", true);
        Request r = Request.fromEvent(event);
        assertEquals("POST", r.method());
        assertEquals("/api/svc/items", r.path());
        assertEquals("application/json", r.header("content-type"));
        assertEquals("x", r.query("q"));
        assertEquals(1, r.json().get("a"));
    }

    @Test
    void fallsBackToContextPathAndRejectsNonHttpEvents() {
        Request r = Request.fromEvent(Map.of("requestContext", Map.of("http", Map.of("method", "GET", "path", "/p"))));
        assertEquals("/p", r.path());
        assertTrue(r.json().isEmpty());
        assertThrows(ApiException.class, () -> Request.fromEvent(null));
        assertThrows(ApiException.class, () -> Request.fromEvent(Map.of("requestContext", "x")));
    }

    @Test
    void blankQueryValuesAreAbsentAndValuesTrimmed() {
        Request r = withQuery("a", "  ", "b", " v ");
        assertNull(r.query("a"));
        assertEquals("v", r.query("b"));
        assertNull(r.query("missing"));
    }

    @Test
    void typedQueryParameters() {
        Request r = withQuery("n", "5", "id", "3", "t", "yes", "f", "0");
        assertEquals(5, r.queryInt("n", 1, 1, 10));
        assertEquals(7, r.queryInt("missing", 7, 1, 10));
        assertEquals(3L, r.queryId("id"));
        assertNull(r.queryId("missing"));
        assertTrue(r.queryBool("t"));
        assertFalse(r.queryBool("f"));
        assertNull(r.queryBool("missing"));
        assertThrows(ApiException.class, () -> withQuery("n", "x").queryInt("n", 1, 1, 10));
        assertThrows(ApiException.class, () -> withQuery("n", "50").queryInt("n", 1, 1, 10));
        assertThrows(ApiException.class, () -> withQuery("id", "0").queryId("id"));
        assertThrows(ApiException.class, () -> withQuery("t", "maybe").queryBool("t"));
    }

    @Test
    void malformedJsonIs400() {
        ApiException e = assertThrows(ApiException.class,
            () -> new Request("POST", "/x", Map.of(), Map.of(), "[1,2]").json());
        assertEquals("MALFORMED_JSON", e.code());
        assertThrows(ApiException.class, () -> new Request("POST", "/x", Map.of(), Map.of(), "{bad").json());
    }
}
