package com.example.common.query;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.http.ApiException;
import com.example.common.http.Request;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryBuilderTest {

    private static Request req(String... kv) {
        Map<String, String> q = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            q.put(kv[i], kv[i + 1]);
        }
        return new Request("GET", "/x", q, Map.of(), null);
    }

    @Test
    void buildsFilteredSortedPagedQuery() {
        Request r = req("sort", "name", "order", "desc", "limit", "10", "offset", "20");
        QueryBuilder q = new QueryBuilder("SELECT * FROM t")
            .search("50%_off", "name", "code")
            .eq("status", "active")
            .eq("skipped", null)
            .eqEnum("kind", "a", Set.of("a", "b"), "kind")
            .orderBy(r, Set.of("name"), "code")
            .page(r);
        assertEquals("SELECT q.*, count(*) OVER () AS total_count FROM (SELECT * FROM t WHERE (name ILIKE ? OR code ILIKE ?)"
            + " AND status = ? AND kind = ?) AS q ORDER BY q.name DESC NULLS LAST, q.id LIMIT ? OFFSET ?", q.sql());
        assertArrayEquals(new Object[] {"%50\\%\\_off%", "%50\\%\\_off%", "active", "a", 10, 20}, q.params());
        assertEquals(10, q.limit());
        assertEquals(20, q.offset());
    }

    @Test
    void defaultsAndBlankSearch() {
        QueryBuilder q = new QueryBuilder("SELECT * FROM t").search("  ", "name").orderBy(req(), Set.of("name"), "name");
        assertEquals("SELECT q.*, count(*) OVER () AS total_count FROM (SELECT * FROM t) AS q"
            + " ORDER BY q.name ASC NULLS LAST, q.id", q.sql());
        assertTrue(new QueryBuilder("SELECT 1").sql().endsWith("ORDER BY q.id"));
        assertEquals(50, new QueryBuilder("SELECT 1").page(req()).limit());
    }

    @Test
    void rejectsBadQueryParameters() {
        QueryBuilder q = new QueryBuilder("SELECT * FROM t");
        assertThrows(ApiException.class, () -> q.orderBy(req("sort", "password"), Set.of("name"), "name"));
        assertThrows(ApiException.class, () -> q.orderBy(req("order", "sideways"), Set.of("name"), "name"));
        assertThrows(ApiException.class, () -> q.eqEnum("kind", "z", Set.of("a"), "kind"));
        assertThrows(ApiException.class, () -> q.page(req("limit", "500")));
        assertThrows(ApiException.class, () -> q.page(req("offset", "-1")));
    }

    @Test
    void escapesLikeWildcards() {
        assertEquals("a\\\\b\\%c\\_d", QueryBuilder.escapeLike("a\\b%c_d"));
    }

    @Test
    void pageWrapsRowsAndDropsTotalColumn() {
        QueryBuilder q = new QueryBuilder("SELECT 1").page(req("limit", "2"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", i);
            row.put("total_count", 7L);
            rows.add(row);
        }
        Map<String, Object> page = Page.of(rows, q);
        assertEquals(7L, ((Number) page.get("total")).longValue());
        assertEquals(2, page.get("limit"));
        assertFalse(((List<?>) page.get("items")).stream().anyMatch(r -> ((Map<?, ?>) r).containsKey("total_count")));
        assertEquals(0L, ((Number) Page.of(new ArrayList<>(), q).get("total")).longValue());
        QueryBuilder past = new QueryBuilder("SELECT 1").page(req("offset", "40"));
        assertEquals(null, Page.of(new ArrayList<>(), past).get("total"));
    }
}
