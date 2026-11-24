package com.example;

import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.http.ApiException;
import com.example.common.http.Request;
import com.example.common.http.Responses;
import com.example.common.query.Page;
import com.example.common.query.QueryBuilder;
import com.example.common.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Project expenses: the "budget consumed" side of budget vs. plan.
 */
final class ExpensesApi {

    static final Set<String> CATEGORIES = Set.of("labor", "software", "hardware", "travel", "services", "other");
    private static final Set<String> SORTABLE = Set.of("incurred_on", "amount", "category", "created_at");
    private static final String COLUMNS =
        "id, project_id, category, description, amount, incurred_on, created_by, created_at";
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    /** {@code GET /projects/{id}/expenses?category=&search=&sort=&order=&limit=&offset=}. */
    Map<String, Object> list(Request req) throws Exception {
        long projectId = req.pathId("id");
        ProjectsApi.find(projectId);
        QueryBuilder q = new QueryBuilder("SELECT " + COLUMNS + " FROM expenses")
            .eq("project_id", projectId)
            .eqEnum("category", req.query("category"), CATEGORIES, "category")
            .search(req.query("search"), "description")
            .orderBy(req, SORTABLE, "incurred_on")
            .page(req);
        return Responses.ok(Page.of(Sql.query(Db.get(), q.sql(), q.params()), q));
    }

    /** {@code POST /projects/{id}/expenses}. */
    Map<String, Object> create(Request req) throws Exception {
        long projectId = req.pathId("id");
        ProjectsApi.find(projectId);
        Fields f = validate(req.json());
        Map<String, Object> row = Sql.queryOne(Db.get(),
            "INSERT INTO expenses (project_id, category, description, amount, incurred_on, created_by)"
                + " VALUES (?, ?, ?, ?, ?, ?) RETURNING " + COLUMNS,
            projectId, f.category, f.description, f.amount, f.incurredOn, req.principal().userId()).orElseThrow();
        return Responses.created(row);
    }

    /** {@code PUT /expenses/{id}}. */
    Map<String, Object> update(Request req) throws Exception {
        long id = req.pathId("id");
        Fields f = validate(req.json());
        return Responses.ok(Sql.queryOne(Db.get(),
                "UPDATE expenses SET category = ?, description = ?, amount = ?, incurred_on = ? WHERE id = ?"
                    + " RETURNING " + COLUMNS,
                f.category, f.description, f.amount, f.incurredOn, id)
            .orElseThrow(() -> ApiException.notFound("Expense", id)));
    }

    /** {@code DELETE /expenses/{id}}. */
    Map<String, Object> delete(Request req) throws Exception {
        long id = req.pathId("id");
        if (Sql.update(Db.get(), "DELETE FROM expenses WHERE id = ?", id) == 0) {
            throw ApiException.notFound("Expense", id);
        }
        return Responses.noContent();
    }

    private record Fields(String category, String description, BigDecimal amount, LocalDate incurredOn) {
    }

    private static Fields validate(Map<String, Object> body) {
        Validator v = new Validator(body);
        String category = v.requiredEnum("category", CATEGORIES);
        String description = v.requiredString("description", 500);
        BigDecimal amount = v.money("amount", MIN_AMOUNT, true);
        LocalDate incurredOn = v.requiredDate("incurred_on");
        v.throwIfInvalid();
        return new Fields(category, description, amount, incurredOn);
    }
}
