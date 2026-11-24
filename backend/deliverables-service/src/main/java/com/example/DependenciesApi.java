package com.example;

import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.http.ApiException;
import com.example.common.http.Request;
import com.example.common.http.Responses;
import com.example.common.validation.Validator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependencies between deliverables: "deliverable X depends on Y" means Y must finish first.
 */
final class DependenciesApi {

    /** Transitive prerequisites (upstream) of a deliverable with their distance. */
    private static final String UPSTREAM = """
        WITH RECURSIVE chain(id, depth) AS (
          SELECT depends_on_id, 1 FROM deliverable_dependencies WHERE deliverable_id = ?
          UNION
          SELECT dd.depends_on_id, c.depth + 1 FROM deliverable_dependencies dd
          JOIN chain c ON dd.deliverable_id = c.id WHERE c.depth < 50
        )
        SELECT o.*, x.depth FROM (SELECT id, min(depth) AS depth FROM chain GROUP BY id) x
        JOIN deliverable_overview o ON o.id = x.id ORDER BY x.depth, o.due_date, o.id""";

    /** Transitive dependents (downstream) of a deliverable with their distance. */
    private static final String DOWNSTREAM = """
        WITH RECURSIVE chain(id, depth) AS (
          SELECT deliverable_id, 1 FROM deliverable_dependencies WHERE depends_on_id = ?
          UNION
          SELECT dd.deliverable_id, c.depth + 1 FROM deliverable_dependencies dd
          JOIN chain c ON dd.depends_on_id = c.id WHERE c.depth < 50
        )
        SELECT o.*, x.depth FROM (SELECT id, min(depth) AS depth FROM chain GROUP BY id) x
        JOIN deliverable_overview o ON o.id = x.id ORDER BY x.depth, o.due_date, o.id""";

    /** Counts whether the second id is a (transitive) prerequisite of the first: used for cycle checks. */
    private static final String REACHES = """
        WITH RECURSIVE chain(id) AS (
          SELECT depends_on_id FROM deliverable_dependencies WHERE deliverable_id = ?
          UNION
          SELECT dd.depends_on_id FROM deliverable_dependencies dd JOIN chain c ON dd.deliverable_id = c.id
        )
        SELECT count(*) FROM chain WHERE id = ?""";

    /**
     * {@code GET /deliverables/{id}/chain}: everything this deliverable waits on (upstream),
     * everything waiting on it (downstream), and the unfinished upstream items blocking it.
     */
    Map<String, Object> chain(Request req) throws Exception {
        long id = req.pathId("id");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deliverable", DeliverablesApi.find(id));
        List<Map<String, Object>> upstream = Sql.query(Db.get(), UPSTREAM, id);
        body.put("upstream", upstream);
        body.put("downstream", Sql.query(Db.get(), DOWNSTREAM, id));
        body.put("blocking", upstream.stream().filter(d -> !"completed".equals(d.get("status"))).toList());
        return Responses.ok(body);
    }

    /** {@code GET /deliverables/graph?project_id=}: nodes and edges for one project. */
    Map<String, Object> graph(Request req) throws Exception {
        Long projectId = req.queryId("project_id");
        if (projectId == null) {
            throw ApiException.badRequest("Query parameter 'project_id' is required");
        }
        if (!Sql.exists(Db.get(), "projects", projectId)) {
            throw ApiException.notFound("Project", projectId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nodes", Sql.query(Db.get(),
            "SELECT * FROM deliverable_overview WHERE project_id = ? ORDER BY due_date, id", projectId));
        body.put("edges", Sql.query(Db.get(),
            "SELECT dd.depends_on_id AS source, dd.deliverable_id AS target FROM deliverable_dependencies dd"
                + " JOIN deliverables d ON d.id = dd.deliverable_id WHERE d.project_id = ? ORDER BY 1, 2", projectId));
        return Responses.ok(body);
    }

    /** {@code POST /deliverables/{id}/dependencies} with {@code {"depends_on_id": n}}. */
    Map<String, Object> add(Request req) throws Exception {
        long id = req.pathId("id");
        Validator v = new Validator(req.json());
        Long dependsOn = v.requiredId("depends_on_id");
        v.throwIfInvalid();

        Map<String, Object> deliverable = DeliverablesApi.find(id);
        Map<String, Object> prerequisite = Sql.queryOne(Db.get(),
            "SELECT id, project_id FROM deliverables WHERE id = ?", dependsOn).orElse(null);
        if (prerequisite == null) {
            v.addError("depends_on_id", "deliverable does not exist");
        } else if (dependsOn == id) {
            v.addError("depends_on_id", "a deliverable cannot depend on itself");
        } else if (!prerequisite.get("project_id").equals(deliverable.get("project_id"))) {
            v.addError("depends_on_id", "must belong to the same project");
        } else if (Sql.scalar(Db.get(), REACHES, dependsOn, id) > 0) {
            v.addError("depends_on_id", "would create a circular dependency");
        } else if (Sql.queryOne(Db.get(), "SELECT 1 FROM deliverable_dependencies"
                + " WHERE deliverable_id = ? AND depends_on_id = ?", id, dependsOn).isPresent()) {
            v.addError("depends_on_id", "dependency already exists");
        }
        v.throwIfInvalid();
        Sql.update(Db.get(), "INSERT INTO deliverable_dependencies (deliverable_id, depends_on_id) VALUES (?, ?)",
            id, dependsOn);
        return Responses.created(Map.of("deliverable_id", id, "depends_on_id", dependsOn));
    }

    /** {@code DELETE /deliverables/{id}/dependencies/{dependsOnId}}. */
    Map<String, Object> remove(Request req) throws Exception {
        long id = req.pathId("id");
        long dependsOn = req.pathId("dependsOnId");
        if (Sql.update(Db.get(), "DELETE FROM deliverable_dependencies WHERE deliverable_id = ? AND depends_on_id = ?",
                id, dependsOn) == 0) {
            throw ApiException.notFound("Dependency", id + "->" + dependsOn);
        }
        return Responses.noContent();
    }
}
