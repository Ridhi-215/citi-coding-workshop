-- Project roll-ups shared by projects-service and dashboard-service.
-- Budget consumed = sum of expenses (approved trade-off: allocations are not costed).
-- A project is at risk when it is active/on hold and any of these hold:
--   over_budget      expenses exceed budget_planned
--   budget_warning   expenses reached 90% of budget_planned
--   past_end_date    end_date has passed
--   overdue_work     an unfinished deliverable is past its due date
--   blocked_work     a deliverable is blocked
CREATE VIEW project_overview AS
WITH spend AS (
  SELECT project_id, sum(amount) AS spent FROM expenses GROUP BY project_id
), work AS (
  SELECT project_id,
         count(*) AS total,
         count(*) FILTER (WHERE status = 'completed') AS completed,
         count(*) FILTER (WHERE status <> 'completed' AND due_date < CURRENT_DATE) AS overdue,
         count(*) FILTER (WHERE status = 'blocked') AS blocked
  FROM deliverables GROUP BY project_id
), base AS (
  SELECT p.id, p.code, p.name, p.description, p.status, p.priority, p.start_date, p.end_date,
         p.budget_planned, p.manager_id, m.full_name AS manager_name,
         p.created_by, p.created_at, p.updated_at,
         COALESCE(s.spent, 0)::NUMERIC(14,2) AS budget_spent,
         COALESCE(w.total, 0)::INT     AS deliverables_total,
         COALESCE(w.completed, 0)::INT AS deliverables_completed,
         COALESCE(w.overdue, 0)::INT   AS deliverables_overdue,
         COALESCE(w.blocked, 0)::INT   AS deliverables_blocked
  FROM projects p
  LEFT JOIN team_members m ON m.id = p.manager_id
  LEFT JOIN spend s ON s.project_id = p.id
  LEFT JOIN work w ON w.project_id = p.id
), flagged AS (
  SELECT b.*,
         (b.budget_planned - b.budget_spent)::NUMERIC(14,2) AS budget_remaining,
         CASE WHEN b.budget_planned > 0
              THEN round(b.budget_spent * 100 / b.budget_planned, 1) END AS budget_used_pct,
         CASE WHEN b.deliverables_total > 0
              THEN round(b.deliverables_completed * 100.0 / b.deliverables_total, 1) ELSE 0.0 END AS progress_pct,
         CASE WHEN b.status IN ('active', 'on_hold') THEN array_remove(ARRAY[
           CASE WHEN b.budget_spent > b.budget_planned THEN 'over_budget' END,
           CASE WHEN b.budget_spent <= b.budget_planned AND b.budget_planned > 0
                     AND b.budget_spent >= b.budget_planned * 0.9 THEN 'budget_warning' END,
           CASE WHEN b.end_date < CURRENT_DATE THEN 'past_end_date' END,
           CASE WHEN b.deliverables_overdue > 0 THEN 'overdue_work' END,
           CASE WHEN b.deliverables_blocked > 0 THEN 'blocked_work' END
         ], NULL) ELSE ARRAY[]::TEXT[] END AS risk_reasons
  FROM base b
)
SELECT f.*, cardinality(f.risk_reasons) > 0 AS at_risk FROM flagged f;
