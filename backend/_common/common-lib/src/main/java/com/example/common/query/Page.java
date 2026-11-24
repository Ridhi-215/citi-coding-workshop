package com.example.common.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Response envelope for list endpoints: {@code {"items": [...], "total": n, "limit": n, "offset": n}}.
 */
public final class Page {

    private Page() {
    }

    /**
     * Builds the envelope from rows produced by a {@link QueryBuilder} query.
     *
     * @param rows  rows including the {@code total_count} column (removed from each row)
     * @param query the builder, for limit and offset
     * @return envelope ready to serialize
     */
    public static Map<String, Object> of(List<Map<String, Object>> rows, QueryBuilder query) {
        long total = 0;
        for (Map<String, Object> row : rows) {
            Object count = row.remove("total_count");
            if (count instanceof Number n) {
                total = n.longValue();
            }
        }
        if (rows.isEmpty() && query.offset() > 0) {
            total = -1; // unknown: offset is past the end
        }
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("items", rows);
        page.put("total", total < 0 ? null : total);
        page.put("limit", query.limit());
        page.put("offset", query.offset());
        return page;
    }
}
