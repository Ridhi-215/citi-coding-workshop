package com.example;

import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.http.ApiException;
import com.example.common.http.Request;
import com.example.common.http.Responses;
import com.example.common.query.Page;
import com.example.common.query.QueryBuilder;
import com.example.common.validation.Validator;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * {@code /deliverables}: CRUD over the {@code deliverable_overview} view.
 */
final class DeliverablesApi {

    static final Set<String> STATUSES = Set.of("not_started", "in_progress", "blocked", "completed");
    private static final Set<String> SORTABLE = Set.of("name", "status", "due_date", "project_code", "owner_name",
        "updated_at");

    /**
     * {@code GET /deliverables?project_id=&owner_id=&status=&overdue=&search=&sort=&order=&limit=&offset=}.
     */
    Map<String, Object> list(Request req) throws Exception {
        QueryBuilder q = new QueryBuilder("SELECT * FROM deliverable_overview")
            .search(req.query("search"), "name", "description", "project_code", "project_name", "owner_name")
            .eq("project_id", req.queryId("project_id"))
            .eq("owner_id", req.queryId("owner_id"))
            .eqEnum("status", req.query("status"), STATUSES, "status")
            .eq("is_overdue", req.queryBool("overdue"))
            .orderBy(req, SORTABLE, "due_date")
            .page(req);
        return Responses.ok(Page.of(Sql.query(Db.get(), q.sql(), q.params()), q));
    }

    /** {@code GET /deliverables/{id}}: includes direct prerequisites and dependents. */
    Map<String, Object> get(Request req) throws Exception {
        long id = req.pathId("id");
        Map<String, Object> row = find(id);
        row.put("depends_on", Sql.query(Db.get(),
            "SELECT o.id, o.name, o.status, o.due_date, o.is_overdue FROM deliverable_dependencies dd"
                + " JOIN deliverable_overview o ON o.id = dd.depends_on_id WHERE dd.deliverable_id = ? ORDER BY o.due_date",
            id));
        row.put("dependents", Sql.query(Db.get(),
            "SELECT o.id, o.name, o.status, o.due_date, o.is_overdue FROM deliverable_dependencies dd"
                + " JOIN deliverable_overview o ON o.id = dd.deliverable_id WHERE dd.depends_on_id = ? ORDER BY o.due_date",
            id));
        return Responses.ok(row);
    }

    /** {@code POST /deliverables}. */
    Map<String, Object> create(Request req) throws Exception {
        Fields f = validate(req.json(), null);
        long id = Sql.scalar(Db.get(),
            "INSERT INTO deliverables (project_id, name, description, owner_id, status, due_date, completed_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, CASE WHEN ? = 'completed' THEN now() END) RETURNING id",
            f.projectId, f.name, f.description, f.ownerId, f.status, f.dueDate, f.status);
        return Responses.created(find(id));
    }

    /** {@code PUT /deliverables/{id}}: replaces editable fields; the project cannot change. */
    Map<String, Object> update(Request req) throws Exception {
        long id = req.pathId("id");
        Map<String, Object> current = find(id);
        Map<String, Object> body = req.json();
        body.put("project_id", current.get("project_id"));
        Fields f = validate(body, id);
        Sql.update(Db.get(),
            "UPDATE deliverables SET name = ?, description = ?, owner_id = ?, status = ?, due_date = ?,"
                + " completed_at = CASE WHEN ? = 'completed' THEN COALESCE(completed_at, now()) END,"
                + " updated_at = now() WHERE id = ?",
            f.name, f.description, f.ownerId, f.status, f.dueDate, f.status, id);
        return Responses.ok(find(id));
    }

    /** {@code DELETE /deliverables/{id}}: its dependency links are removed with it. */
    Map<String, Object> delete(Request req) throws Exception {
        long id = req.pathId("id");
        if (Sql.update(Db.get(), "DELETE FROM deliverables WHERE id = ?", id) == 0) {
            throw ApiException.notFound("Deliverable", id);
        }
        return Responses.noContent();
    }

    static Map<String, Object> find(long id) throws Exception {
        return Sql.queryOne(Db.get(), "SELECT * FROM deliverable_overview WHERE id = ?", id)
            .orElseThrow(() -> ApiException.notFound("Deliverable", id));
    }

    private record Fields(long projectId, String name, String description, Long ownerId, String status,
                          LocalDate dueDate) {
    }

    private static Fields validate(Map<String, Object> body, Long currentId) throws Exception {
        Validator v = new Validator(body);
        Long projectId = v.requiredId("project_id");
        String name = v.requiredString("name", 160);
        String description = v.optionalString("description", 2000);
        Long ownerId = v.optionalId("owner_id");
        String status = v.optionalEnum("status", STATUSES, "not_started");
        LocalDate dueDate = v.requiredDate("due_date");
        if (projectId != null && currentId == null && !Sql.exists(Db.get(), "projects", projectId)) {
            v.addError("project_id", "project does not exist");
        }
        if (ownerId != null && !Sql.exists(Db.get(), "team_members", ownerId)) {
            v.addError("owner_id", "team member does not exist");
        }
        if ("completed".equals(status) && currentId != null) {
            long open = Sql.scalar(Db.get(),
                "SELECT count(*) FROM deliverable_dependencies dd JOIN deliverables pre ON pre.id = dd.depends_on_id"
                    + " WHERE dd.deliverable_id = ? AND pre.status <> 'completed'", currentId);
            v.check(open == 0, "status", "complete its " + open + " open prerequisite(s) first");
        }
        v.throwIfInvalid();
        return new Fields(projectId, name, description, ownerId, status, dueDate);
    }
}
