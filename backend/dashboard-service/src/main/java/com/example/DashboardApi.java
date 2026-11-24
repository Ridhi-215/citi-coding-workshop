package com.example;

import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.http.ApiException;
import com.example.common.http.Request;
import com.example.common.http.Responses;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Portfolio metrics, one query per business question, all from the shared views
 * ({@code project_overview}, {@code deliverable_overview}, {@code member_overview}).
 */
final class DashboardApi {

    private static final int UPCOMING_DAYS = 14;

    /** {@code GET /summary}: everything the dashboard page shows, in one request. */
    Map<String, Object> summary(Request req) throws Exception {
        Connection c = Db.get();
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("kpis", Sql.queryOne(c, """
            SELECT
              (SELECT count(*) FROM project_overview WHERE status = 'active')::INT AS active_projects,
              (SELECT count(*) FROM project_overview WHERE at_risk)::INT AS at_risk_projects,
              (SELECT COALESCE(sum(budget_planned), 0) FROM project_overview
                 WHERE status IN ('active', 'on_hold')) AS budget_planned,
              (SELECT COALESCE(sum(budget_spent), 0) FROM project_overview
                 WHERE status IN ('active', 'on_hold')) AS budget_spent,
              (SELECT count(*) FROM deliverable_overview WHERE project_status <> 'cancelled')::INT AS deliverables_total,
              (SELECT count(*) FROM deliverable_overview
                 WHERE project_status <> 'cancelled' AND status = 'completed')::INT AS deliverables_completed,
              (SELECT count(*) FROM deliverable_overview
                 WHERE is_overdue AND project_status IN ('active', 'on_hold'))::INT AS deliverables_overdue,
              (SELECT count(*) FROM member_overview)::INT AS team_size,
              (SELECT count(*) FROM member_overview WHERE over_allocated)::INT AS over_allocated_members
            """).orElseThrow());

        // Status of projects
        body.put("projects_by_status", Sql.query(c,
            "SELECT status, count(*)::INT AS count FROM projects GROUP BY status ORDER BY count DESC, status"));

        // At-risk projects, worst first
        body.put("at_risk_projects", Sql.query(c, """
            SELECT id, code, name, status, priority, end_date, budget_planned, budget_spent, budget_used_pct,
                   progress_pct, deliverables_overdue, deliverables_blocked, risk_reasons, manager_name
            FROM project_overview WHERE at_risk
            ORDER BY cardinality(risk_reasons) DESC, budget_used_pct DESC NULLS LAST, code"""));

        // Budget consumed vs. planned (active and on-hold projects)
        body.put("budget_by_project", Sql.query(c, """
            SELECT id, code, name, budget_planned, budget_spent, budget_remaining, budget_used_pct
            FROM project_overview WHERE status IN ('active', 'on_hold') ORDER BY budget_planned DESC, code"""));

        // Deliverables and their completion
        body.put("deliverables_by_status", Sql.query(c, """
            SELECT status, count(*)::INT AS count FROM deliverable_overview
            WHERE project_status <> 'cancelled' GROUP BY status ORDER BY status"""));
        body.put("deliverables_by_project", Sql.query(c, """
            SELECT id, code, name, deliverables_total, deliverables_completed, deliverables_overdue, progress_pct
            FROM project_overview WHERE status IN ('active', 'on_hold') ORDER BY code"""));
        body.put("overdue_deliverables", Sql.query(c, """
            SELECT id, name, project_id, project_code, owner_name, status, due_date,
                   (CURRENT_DATE - due_date) AS days_overdue, dependent_count
            FROM deliverable_overview WHERE is_overdue AND project_status IN ('active', 'on_hold')
            ORDER BY due_date, id"""));
        body.put("upcoming_deliverables", Sql.query(c, """
            SELECT id, name, project_id, project_code, owner_name, status, due_date, open_dependency_count
            FROM deliverable_overview
            WHERE status <> 'completed' AND project_status IN ('active', 'on_hold')
              AND due_date BETWEEN CURRENT_DATE AND CURRENT_DATE + ?
            ORDER BY due_date, id""", UPCOMING_DAYS));

        // Dependency chains: unfinished work that other deliverables are waiting on
        body.put("blocking_deliverables", Sql.query(c, """
            SELECT id, name, project_id, project_code, owner_name, status, due_date, is_overdue, dependent_count
            FROM deliverable_overview
            WHERE status <> 'completed' AND dependent_count > 0 AND project_status IN ('active', 'on_hold')
              AND (status = 'blocked' OR is_overdue)
            ORDER BY dependent_count DESC, due_date, id"""));

        // Resource allocation and over-allocated members
        body.put("over_allocated_members", Sql.query(c, """
            SELECT m.id, m.full_name, m.job_title, m.department, m.current_allocation_pct, m.current_project_count,
                   (SELECT string_agg(p.code || ' ' || a.allocation_percent || '%', ', ' ORDER BY a.allocation_percent DESC)
                    FROM allocations a JOIN projects p ON p.id = a.project_id
                    WHERE a.team_member_id = m.id AND a.start_date <= CURRENT_DATE
                      AND (a.end_date IS NULL OR a.end_date >= CURRENT_DATE)) AS allocations
            FROM member_overview m WHERE m.over_allocated
            ORDER BY m.current_allocation_pct DESC, m.full_name"""));
        body.put("allocation_by_member", Sql.query(c, """
            SELECT id, full_name, department, current_allocation_pct, over_allocated
            FROM member_overview ORDER BY current_allocation_pct DESC, full_name"""));
        body.put("allocation_by_project", Sql.query(c, """
            SELECT p.id, p.code, p.name, count(a.id)::INT AS people, COALESCE(sum(a.allocation_percent), 0)::INT AS total_pct
            FROM projects p
            LEFT JOIN allocations a ON a.project_id = p.id AND a.start_date <= CURRENT_DATE
                 AND (a.end_date IS NULL OR a.end_date >= CURRENT_DATE)
            WHERE p.status IN ('active', 'on_hold', 'planned')
            GROUP BY p.id ORDER BY total_pct DESC, p.code"""));
        return Responses.ok(body);
    }

    /** {@code GET /projects/{id}/health}: one project's budget, deliverables, team and risk in one call. */
    Map<String, Object> projectHealth(Request req) throws Exception {
        long id = req.pathId("id");
        Connection c = Db.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("project", Sql.queryOne(c, "SELECT * FROM project_overview WHERE id = ?", id)
            .orElseThrow(() -> ApiException.notFound("Project", id)));
        body.put("expenses_by_category", Sql.query(c,
            "SELECT category, sum(amount) AS amount FROM expenses WHERE project_id = ? GROUP BY category ORDER BY 2 DESC",
            id));
        body.put("deliverables", Sql.query(c,
            "SELECT * FROM deliverable_overview WHERE project_id = ? ORDER BY due_date, id", id));
        body.put("dependencies", Sql.query(c, """
            SELECT dd.depends_on_id AS source, dd.deliverable_id AS target FROM deliverable_dependencies dd
            JOIN deliverables d ON d.id = dd.deliverable_id WHERE d.project_id = ? ORDER BY 1, 2""", id));
        body.put("team", Sql.query(c, """
            SELECT a.id AS allocation_id, m.id, m.full_name, m.job_title, a.role_on_project, a.allocation_percent,
                   a.start_date, a.end_date, m.current_allocation_pct, m.over_allocated
            FROM allocations a JOIN member_overview m ON m.id = a.team_member_id
            WHERE a.project_id = ? AND (a.end_date IS NULL OR a.end_date >= CURRENT_DATE)
            ORDER BY a.allocation_percent DESC, m.full_name""", id));
        return Responses.ok(body);
    }
}
