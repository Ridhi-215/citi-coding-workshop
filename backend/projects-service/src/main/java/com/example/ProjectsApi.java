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
import java.util.regex.Pattern;

/**
 * {@code /projects}: CRUD plus budget, progress and risk from the {@code project_overview} view.
 */
final class ProjectsApi {

    static final Set<String> STATUSES = Set.of("planned", "active", "on_hold", "completed", "cancelled");
    static final Set<String> PRIORITIES = Set.of("low", "medium", "high", "critical");
    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "priority", "start_date",
        "end_date", "budget_planned", "budget_spent", "budget_used_pct", "progress_pct", "updated_at");
    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9-]{1,19}");

    /**
     * {@code GET /projects?search=&status=&priority=&manager_id=&at_risk=&sort=&order=&limit=&offset=}.
     */
    Map<String, Object> list(Request req) throws Exception {
        QueryBuilder q = new QueryBuilder("SELECT * FROM project_overview")
            .search(req.query("search"), "code", "name", "description", "manager_name")
            .eqEnum("status", req.query("status"), STATUSES, "status")
            .eqEnum("priority", req.query("priority"), PRIORITIES, "priority")
            .eq("manager_id", req.queryId("manager_id"))
            .eq("at_risk", req.queryBool("at_risk"))
            .orderBy(req, SORTABLE, "code")
            .page(req);
        return Responses.ok(Page.of(Sql.query(Db.get(), q.sql(), q.params()), q));
    }

    /** {@code GET /projects/{id}}. */
    Map<String, Object> get(Request req) throws Exception {
        return Responses.ok(find(req.pathId("id")));
    }

    /** {@code POST /projects}. */
    Map<String, Object> create(Request req) throws Exception {
        Fields f = validate(req.json(), null);
        long id = Sql.scalar(Db.get(),
            "INSERT INTO projects (code, name, description, status, priority, start_date, end_date,"
                + " budget_planned, manager_id, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
            f.code, f.name, f.description, f.status, f.priority, f.start, f.end, f.budget, f.managerId,
            req.principal().userId());
        return Responses.created(find(id));
    }

    /** {@code PUT /projects/{id}}: replaces all editable fields. */
    Map<String, Object> update(Request req) throws Exception {
        long id = req.pathId("id");
        find(id);
        Fields f = validate(req.json(), id);
        Sql.update(Db.get(),
            "UPDATE projects SET code = ?, name = ?, description = ?, status = ?, priority = ?, start_date = ?,"
                + " end_date = ?, budget_planned = ?, manager_id = ?, updated_at = now() WHERE id = ?",
            f.code, f.name, f.description, f.status, f.priority, f.start, f.end, f.budget, f.managerId, id);
        return Responses.ok(find(id));
    }

    /** {@code DELETE /projects/{id}}: also removes its expenses, deliverables and allocations. */
    Map<String, Object> delete(Request req) throws Exception {
        long id = req.pathId("id");
        if (Sql.update(Db.get(), "DELETE FROM projects WHERE id = ?", id) == 0) {
            throw ApiException.notFound("Project", id);
        }
        return Responses.noContent();
    }

    static Map<String, Object> find(long id) throws Exception {
        return Sql.queryOne(Db.get(), "SELECT * FROM project_overview WHERE id = ?", id)
            .orElseThrow(() -> ApiException.notFound("Project", id));
    }

    private record Fields(String code, String name, String description, String status, String priority,
                          LocalDate start, LocalDate end, BigDecimal budget, Long managerId) {
    }

    private static Fields validate(Map<String, Object> body, Long currentId) throws Exception {
        Validator v = new Validator(body);
        String code = v.requiredString("code", 20);
        if (code != null) {
            code = code.toUpperCase();
            v.check(CODE.matcher(code).matches(), "code", "use 2-20 letters, digits or '-'");
        }
        String name = v.requiredString("name", 120);
        String description = v.optionalString("description", 2000);
        String status = v.optionalEnum("status", STATUSES, "planned");
        String priority = v.optionalEnum("priority", PRIORITIES, "medium");
        LocalDate start = v.requiredDate("start_date");
        LocalDate end = v.requiredDate("end_date");
        if (start != null && end != null) {
            v.check(!end.isBefore(start), "end_date", "must be on or after the start date");
        }
        BigDecimal budget = v.money("budget_planned", BigDecimal.ZERO, true);
        Long managerId = v.optionalId("manager_id");
        if (managerId != null && !Sql.exists(Db.get(), "team_members", managerId)) {
            v.addError("manager_id", "team member does not exist");
        }
        if (code != null && !v.hasError("code") && Sql.queryOne(Db.get(),
                "SELECT 1 FROM projects WHERE code = ? AND id IS DISTINCT FROM ?", code, currentId).isPresent()) {
            v.addError("code", "another project already uses this code");
        }
        v.throwIfInvalid();
        return new Fields(code, name, description, status, priority, start, end, budget, managerId);
    }
}
