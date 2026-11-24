-- Read models shared by deliverables-, resources- and dashboard-service.

-- Deliverables with project/owner names, overdue flag and dependency counts.
CREATE VIEW deliverable_overview AS
SELECT d.id, d.project_id, p.code AS project_code, p.name AS project_name, p.status AS project_status,
       d.name, d.description, d.owner_id, m.full_name AS owner_name, d.status, d.due_date, d.completed_at,
       d.created_at, d.updated_at,
       (d.status <> 'completed' AND d.due_date < CURRENT_DATE) AS is_overdue,
       (SELECT count(*) FROM deliverable_dependencies dd WHERE dd.deliverable_id = d.id)::INT AS dependency_count,
       (SELECT count(*) FROM deliverable_dependencies dd JOIN deliverables pre ON pre.id = dd.depends_on_id
         WHERE dd.deliverable_id = d.id AND pre.status <> 'completed')::INT AS open_dependency_count,
       (SELECT count(*) FROM deliverable_dependencies dd WHERE dd.depends_on_id = d.id)::INT AS dependent_count
FROM deliverables d
JOIN projects p ON p.id = d.project_id
LEFT JOIN team_members m ON m.id = d.owner_id;

-- Team members with their allocation today (allocations active on CURRENT_DATE).
-- Over-allocated = more than 100% in total across projects.
CREATE VIEW member_overview AS
WITH current AS (
  SELECT a.team_member_id, sum(a.allocation_percent) AS pct, count(DISTINCT a.project_id) AS projects
  FROM allocations a
  WHERE a.start_date <= CURRENT_DATE AND (a.end_date IS NULL OR a.end_date >= CURRENT_DATE)
  GROUP BY a.team_member_id
)
SELECT m.id, m.full_name, m.email, m.job_title, m.department, m.location, m.employment_type, m.user_id,
       m.created_at, m.updated_at,
       COALESCE(c.pct, 0)::INT AS current_allocation_pct,
       COALESCE(c.projects, 0)::INT AS current_project_count,
       COALESCE(c.pct, 0) > 100 AS over_allocated
FROM team_members m
LEFT JOIN current c ON c.team_member_id = m.id;
