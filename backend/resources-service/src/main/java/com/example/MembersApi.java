package com.example;

import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.http.ApiException;
import com.example.common.http.Request;
import com.example.common.http.Responses;
import com.example.common.query.Page;
import com.example.common.query.QueryBuilder;
import com.example.common.validation.Validator;
import java.util.Map;
import java.util.Set;

/**
 * {@code /members}: team members over the {@code member_overview} view.
 */
final class MembersApi {

    static final Set<String> EMPLOYMENT = Set.of("employee", "contractor");
    private static final Set<String> SORTABLE = Set.of("full_name", "email", "department", "job_title",
        "current_allocation_pct", "current_project_count");

    /**
     * {@code GET /members?search=&department=&employment_type=&over_allocated=&sort=&order=&limit=&offset=}.
     */
    Map<String, Object> list(Request req) throws Exception {
        QueryBuilder q = new QueryBuilder("SELECT * FROM member_overview")
            .search(req.query("search"), "full_name", "email", "job_title", "department", "location")
            .eq("department", req.query("department"))
            .eqEnum("employment_type", req.query("employment_type"), EMPLOYMENT, "employment_type")
            .whereIf(req.queryBool("over_allocated"), "over_allocated = ?", req.queryBool("over_allocated"))
            .orderBy(req, SORTABLE, "full_name")
            .page(req);
        return Responses.ok(Page.of(Sql.query(Db.get(), q.sql(), q.params()), q));
    }

    /** {@code GET /members/{id}}: includes the member's allocations, current first. */
    Map<String, Object> get(Request req) throws Exception {
        long id = req.pathId("id");
        Map<String, Object> member = find(id);
        member.put("allocations", Sql.query(Db.get(),
            "SELECT * FROM (" + AllocationsApi.SELECT + ") a WHERE team_member_id = ?"
                + " ORDER BY is_current DESC, start_date DESC", id));
        return Responses.ok(member);
    }

    /** {@code POST /members}. */
    Map<String, Object> create(Request req) throws Exception {
        Fields f = validate(req.json(), null);
        long id = Sql.scalar(Db.get(),
            "INSERT INTO team_members (full_name, email, job_title, department, location, employment_type)"
                + " VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
            f.name, f.email, f.jobTitle, f.department, f.location, f.employment);
        return Responses.created(find(id));
    }

    /** {@code PUT /members/{id}}. */
    Map<String, Object> update(Request req) throws Exception {
        long id = req.pathId("id");
        find(id);
        Fields f = validate(req.json(), id);
        Sql.update(Db.get(),
            "UPDATE team_members SET full_name = ?, email = ?, job_title = ?, department = ?, location = ?,"
                + " employment_type = ?, updated_at = now() WHERE id = ?",
            f.name, f.email, f.jobTitle, f.department, f.location, f.employment, id);
        return Responses.ok(find(id));
    }

    /** {@code DELETE /members/{id}}: removes their allocations; owned deliverables become unassigned. */
    Map<String, Object> delete(Request req) throws Exception {
        long id = req.pathId("id");
        if (Sql.update(Db.get(), "DELETE FROM team_members WHERE id = ?", id) == 0) {
            throw ApiException.notFound("Team member", id);
        }
        return Responses.noContent();
    }

    static Map<String, Object> find(long id) throws Exception {
        return Sql.queryOne(Db.get(), "SELECT * FROM member_overview WHERE id = ?", id)
            .orElseThrow(() -> ApiException.notFound("Team member", id));
    }

    private record Fields(String name, String email, String jobTitle, String department, String location,
                          String employment) {
    }

    private static Fields validate(Map<String, Object> body, Long currentId) throws Exception {
        Validator v = new Validator(body);
        String name = v.requiredString("full_name", 120);
        String email = v.requiredEmail("email");
        String jobTitle = v.optionalString("job_title", 120);
        String department = v.optionalString("department", 80);
        String location = v.optionalString("location", 80);
        String employment = v.optionalEnum("employment_type", EMPLOYMENT, "employee");
        if (email != null && Sql.queryOne(Db.get(),
                "SELECT 1 FROM team_members WHERE email = ? AND id IS DISTINCT FROM ?", email, currentId).isPresent()) {
            v.addError("email", "another team member already uses this email");
        }
        v.throwIfInvalid();
        return new Fields(name, email, jobTitle, department, location, employment);
    }
}
