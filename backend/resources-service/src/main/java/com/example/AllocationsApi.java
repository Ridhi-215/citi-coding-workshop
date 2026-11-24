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
 * {@code /allocations}: a member's percentage of time on a project for a date range.
 *
 * <p>Over-allocation (more than 100% in total on any day) is allowed but reported, because
 * finding over-allocated people is one of the questions the app must answer. Each write
 * returns the member's peak total over the allocation's period.
 */
final class AllocationsApi {

    static final String SELECT = """
        SELECT a.id, a.team_member_id, m.full_name AS member_name, a.project_id, p.code AS project_code,
               p.name AS project_name, a.role_on_project, a.allocation_percent, a.start_date, a.end_date,
               (a.start_date <= CURRENT_DATE AND (a.end_date IS NULL OR a.end_date >= CURRENT_DATE)) AS is_current,
               a.created_at, a.updated_at
        FROM allocations a
        JOIN team_members m ON m.id = a.team_member_id
        JOIN projects p ON p.id = a.project_id""";

    private static final Set<String> SORTABLE = Set.of("member_name", "project_code", "allocation_percent",
        "start_date", "end_date");

    /**
     * Highest total allocation of a member on any day in [start, end] (open end = one year ahead):
     * totals only change on allocation start days, so checking those days is enough.
     */
    private static final String PEAK = """
        SELECT COALESCE(max(t.total), 0) FROM (
          SELECT day, (SELECT sum(a.allocation_percent) FROM allocations a
                       WHERE a.team_member_id = ? AND a.start_date <= day
                         AND (a.end_date IS NULL OR a.end_date >= day)) AS total
          FROM (SELECT ?::date AS day UNION
                SELECT start_date FROM allocations WHERE team_member_id = ?
                  AND start_date BETWEEN ?::date AND COALESCE(?::date, ?::date + 365)) days
        ) t""";

    /** {@code GET /allocations?member_id=&project_id=&current=&sort=&order=&limit=&offset=}. */
    Map<String, Object> list(Request req) throws Exception {
        QueryBuilder q = new QueryBuilder("SELECT * FROM (" + SELECT + ") a")
            .eq("team_member_id", req.queryId("member_id"))
            .eq("project_id", req.queryId("project_id"))
            .whereIf(req.queryBool("current"), "is_current = ?", req.queryBool("current"))
            .search(req.query("search"), "member_name", "project_code", "project_name", "role_on_project")
            .orderBy(req, SORTABLE, "start_date")
            .page(req);
        return Responses.ok(Page.of(Sql.query(Db.get(), q.sql(), q.params()), q));
    }

    /** {@code POST /allocations}. */
    Map<String, Object> create(Request req) throws Exception {
        Fields f = validate(req.json());
        long id = Sql.scalar(Db.get(),
            "INSERT INTO allocations (team_member_id, project_id, role_on_project, allocation_percent, start_date,"
                + " end_date) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
            f.memberId, f.projectId, f.role, f.percent, f.start, f.end);
        return Responses.created(withPeak(find(id)));
    }

    /** {@code PUT /allocations/{id}}. */
    Map<String, Object> update(Request req) throws Exception {
        long id = req.pathId("id");
        find(id);
        Fields f = validate(req.json());
        Sql.update(Db.get(),
            "UPDATE allocations SET team_member_id = ?, project_id = ?, role_on_project = ?, allocation_percent = ?,"
                + " start_date = ?, end_date = ?, updated_at = now() WHERE id = ?",
            f.memberId, f.projectId, f.role, f.percent, f.start, f.end, id);
        return Responses.ok(withPeak(find(id)));
    }

    /** {@code DELETE /allocations/{id}}. */
    Map<String, Object> delete(Request req) throws Exception {
        long id = req.pathId("id");
        if (Sql.update(Db.get(), "DELETE FROM allocations WHERE id = ?", id) == 0) {
            throw ApiException.notFound("Allocation", id);
        }
        return Responses.noContent();
    }

    private static Map<String, Object> find(long id) throws Exception {
        return Sql.queryOne(Db.get(), "SELECT * FROM (" + SELECT + ") a WHERE id = ?", id)
            .orElseThrow(() -> ApiException.notFound("Allocation", id));
    }

    private static Map<String, Object> withPeak(Map<String, Object> row) throws Exception {
        long member = ((Number) row.get("team_member_id")).longValue();
        Object start = row.get("start_date");
        Object end = row.get("end_date");
        long peak = Sql.scalar(Db.get(), PEAK, member, start, member, start, end, start);
        row.put("member_peak_allocation_pct", peak);
        row.put("member_over_allocated", peak > 100);
        return row;
    }

    private record Fields(long memberId, long projectId, String role, int percent, LocalDate start, LocalDate end) {
    }

    private static Fields validate(Map<String, Object> body) throws Exception {
        Validator v = new Validator(body);
        Long memberId = v.requiredId("team_member_id");
        Long projectId = v.requiredId("project_id");
        String role = v.optionalString("role_on_project", 120);
        Integer percent = v.requiredInt("allocation_percent", 1, 100);
        LocalDate start = v.requiredDate("start_date");
        LocalDate end = v.optionalDate("end_date");
        if (start != null && end != null) {
            v.check(!end.isBefore(start), "end_date", "must be on or after the start date");
        }
        if (memberId != null && !Sql.exists(Db.get(), "team_members", memberId)) {
            v.addError("team_member_id", "team member does not exist");
        }
        if (projectId != null && !Sql.exists(Db.get(), "projects", projectId)) {
            v.addError("project_id", "project does not exist");
        }
        v.throwIfInvalid();
        return new Fields(memberId, projectId, role, percent, start, end);
    }
}
