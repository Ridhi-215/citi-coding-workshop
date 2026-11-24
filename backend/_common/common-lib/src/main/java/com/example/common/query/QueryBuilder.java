package com.example.common.query;

import com.example.common.http.ApiException;
import com.example.common.http.Request;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds list queries with search, filters, sorting and paging from request parameters.
 *
 * <p>Only SQL fragments written in code are concatenated; every value from the request is
 * bound as a parameter. Sort columns are looked up in an allow-list, never taken verbatim.
 *
 * <pre>{@code
 * QueryBuilder q = new QueryBuilder("SELECT p.* FROM projects p")
 *     .search(req.query("q"), "p.name", "p.code")
 *     .eq("p.status", req.query("status"))
 *     .orderBy(req, Set.of("name", "end_date"), "id")
 *     .page(req);
 * List<Map<String, Object>> rows = Sql.query(conn, q.sql(), q.params());
 * return Responses.ok(Page.of(rows, q));
 * }</pre>
 *
 * <p>The query is wrapped so the result gets a {@code total_count} window column with the
 * number of matches before paging; {@link Page#of} moves it into the response envelope.
 * Every select must return an {@code id} column (used as the sort tie-breaker).
 */
public final class QueryBuilder {

    /** Default and maximum page sizes. */
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private final String select;
    private final List<String> conditions = new ArrayList<>();
    private final List<Object> params = new ArrayList<>();
    private String groupBy = "";
    private String orderBy = "";
    private String paging = "";
    private int limit = DEFAULT_LIMIT;
    private int offset;

    /**
     * Starts a query.
     *
     * @param select {@code SELECT ... FROM ... [JOIN ...]} without WHERE/ORDER BY
     */
    public QueryBuilder(String select) {
        this.select = select;
    }

    /**
     * Adds a case-insensitive "contains" search across columns (OR-ed together).
     *
     * @param term    search text, ignored when null
     * @param columns columns to search
     * @return this builder
     */
    public QueryBuilder search(String term, String... columns) {
        if (term == null || term.isBlank() || columns.length == 0) {
            return this;
        }
        String pattern = "%" + escapeLike(term.trim()) + "%";
        List<String> parts = new ArrayList<>();
        for (String column : columns) {
            parts.add(column + " ILIKE ?");
            params.add(pattern);
        }
        conditions.add("(" + String.join(" OR ", parts) + ")");
        return this;
    }

    /**
     * Adds {@code column = value} when the value is not null.
     *
     * @param column column expression
     * @param value  value to compare, ignored when null
     * @return this builder
     */
    public QueryBuilder eq(String column, Object value) {
        if (value != null) {
            conditions.add(column + " = ?");
            params.add(value);
        }
        return this;
    }

    /**
     * Adds an equality filter that must be one of the allowed values.
     *
     * @param column  column expression
     * @param value   value from the request, ignored when null
     * @param allowed allowed values
     * @param name    query parameter name for the error message
     * @return this builder
     * @throws ApiException 400 if the value is not allowed
     */
    public QueryBuilder eqEnum(String column, String value, Set<String> allowed, String name) {
        if (value != null && !allowed.contains(value)) {
            throw ApiException.badRequest("Query parameter '" + name + "' must be one of "
                + String.join(", ", allowed.stream().sorted().toList()));
        }
        return eq(column, value);
    }

    /**
     * Adds a raw condition written in code, with its parameters.
     *
     * @param condition SQL condition with {@code ?} placeholders
     * @param values    values for the placeholders
     * @return this builder
     */
    public QueryBuilder where(String condition, Object... values) {
        conditions.add("(" + condition + ")");
        params.addAll(List.of(values));
        return this;
    }

    /**
     * Adds a raw condition only when a flag is set.
     *
     * @param flag      condition is added only when TRUE
     * @param condition SQL condition with {@code ?} placeholders
     * @param values    values for the placeholders
     * @return this builder
     */
    public QueryBuilder whereIf(Boolean flag, String condition, Object... values) {
        return Boolean.TRUE.equals(flag) ? where(condition, values) : this;
    }

    /**
     * Sets a GROUP BY clause (written in code).
     *
     * @param columns group-by expression
     * @return this builder
     */
    public QueryBuilder groupBy(String columns) {
        this.groupBy = " GROUP BY " + columns;
        return this;
    }

    /**
     * Applies {@code sort} and {@code order} query parameters using an allow-list.
     *
     * @param request       request with optional {@code sort} and {@code order}
     * @param sortable      allowed sort names; each must be an output column of the select
     * @param defaultColumn output column used when no sort is given
     * @return this builder
     * @throws ApiException 400 for an unknown sort field or order
     */
    public QueryBuilder orderBy(Request request, Set<String> sortable, String defaultColumn) {
        String sort = request.query("sort");
        String order = request.query("order");
        String column = defaultColumn;
        if (sort != null) {
            if (!sortable.contains(sort)) {
                throw ApiException.badRequest("Cannot sort by '" + sort + "'; allowed: "
                    + String.join(", ", sortable.stream().sorted().toList()));
            }
            column = sort;
        }
        String direction = "ASC";
        if (order != null) {
            direction = switch (order.toLowerCase(Locale.ROOT)) {
                case "asc" -> "ASC";
                case "desc" -> "DESC";
                default -> throw ApiException.badRequest("Query parameter 'order' must be asc or desc");
            };
        }
        // Tie-breaker on id keeps paging stable.
        this.orderBy = " ORDER BY q." + column + " " + direction + " NULLS LAST, q.id";
        return this;
    }

    /**
     * Applies {@code limit} (1-200, default 50) and {@code offset} query parameters.
     *
     * @param request request with optional paging parameters
     * @return this builder
     */
    public QueryBuilder page(Request request) {
        this.limit = request.queryInt("limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        this.offset = request.queryInt("offset", 0, 0, Integer.MAX_VALUE);
        this.paging = " LIMIT ? OFFSET ?";
        return this;
    }

    /**
     * Returns the page size applied by {@link #page(Request)}.
     *
     * @return limit
     */
    public int limit() {
        return limit;
    }

    /**
     * Returns the offset applied by {@link #page(Request)}.
     *
     * @return offset
     */
    public int offset() {
        return offset;
    }

    /**
     * Returns the SQL. The filtered query is wrapped so that a {@code total_count} window
     * column (matches before paging) can be added and sorting uses output column names.
     *
     * @return SQL text with {@code ?} placeholders
     */
    public String sql() {
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return "SELECT q.*, count(*) OVER () AS total_count FROM (" + select + where + groupBy + ") AS q"
            + (orderBy.isEmpty() ? " ORDER BY q.id" : orderBy) + paging;
    }

    /**
     * Returns the values for the placeholders, in order.
     *
     * @return parameters
     */
    public Object[] params() {
        List<Object> all = new ArrayList<>(params);
        if (!paging.isEmpty()) {
            all.add(limit);
            all.add(offset);
        }
        return all.toArray();
    }

    /**
     * Escapes LIKE wildcards so user input matches literally.
     *
     * @param term raw search term
     * @return escaped term (backslash is PostgreSQL's default LIKE escape)
     */
    static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
