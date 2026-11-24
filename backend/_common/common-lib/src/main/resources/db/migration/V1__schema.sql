-- ACME Project Tracker - initial schema.
-- Applied once by com.example.common.db.Migrations. Never edit after deploy; add V2__... instead.

CREATE TABLE users (
  id            SERIAL PRIMARY KEY,
  email         TEXT NOT NULL UNIQUE CHECK (email = lower(email)),
  password_hash TEXT NOT NULL,
  full_name     TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'viewer'
                CHECK (role IN ('admin','manager','contributor','viewer')),
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE team_members (
  id              SERIAL PRIMARY KEY,
  full_name       TEXT NOT NULL,
  email           TEXT NOT NULL UNIQUE,
  job_title       TEXT,
  department      TEXT,
  location        TEXT,
  employment_type TEXT NOT NULL DEFAULT 'employee'
                  CHECK (employment_type IN ('employee','contractor')),
  user_id         INT UNIQUE REFERENCES users(id) ON DELETE SET NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE projects (
  id             SERIAL PRIMARY KEY,
  code           TEXT NOT NULL UNIQUE,
  name           TEXT NOT NULL,
  description    TEXT,
  status         TEXT NOT NULL DEFAULT 'planned'
                 CHECK (status IN ('planned','active','on_hold','completed','cancelled')),
  priority       TEXT NOT NULL DEFAULT 'medium'
                 CHECK (priority IN ('low','medium','high','critical')),
  start_date     DATE NOT NULL,
  end_date       DATE NOT NULL,
  budget_planned NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (budget_planned >= 0),
  manager_id     INT REFERENCES team_members(id) ON DELETE SET NULL,
  created_by     INT REFERENCES users(id) ON DELETE SET NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (end_date >= start_date)
);

CREATE TABLE expenses (
  id          SERIAL PRIMARY KEY,
  project_id  INT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  category    TEXT NOT NULL
              CHECK (category IN ('labor','software','hardware','travel','services','other')),
  description TEXT NOT NULL,
  amount      NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  incurred_on DATE NOT NULL,
  created_by  INT REFERENCES users(id) ON DELETE SET NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE deliverables (
  id           SERIAL PRIMARY KEY,
  project_id   INT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  description  TEXT,
  owner_id     INT REFERENCES team_members(id) ON DELETE SET NULL,
  status       TEXT NOT NULL DEFAULT 'not_started'
               CHECK (status IN ('not_started','in_progress','blocked','completed')),
  due_date     DATE NOT NULL,
  completed_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE deliverable_dependencies (
  deliverable_id INT NOT NULL REFERENCES deliverables(id) ON DELETE CASCADE,
  depends_on_id  INT NOT NULL REFERENCES deliverables(id) ON DELETE CASCADE,
  PRIMARY KEY (deliverable_id, depends_on_id),
  CHECK (deliverable_id <> depends_on_id)
);

CREATE TABLE allocations (
  id                 SERIAL PRIMARY KEY,
  team_member_id     INT NOT NULL REFERENCES team_members(id) ON DELETE CASCADE,
  project_id         INT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  role_on_project    TEXT,
  allocation_percent INT NOT NULL CHECK (allocation_percent BETWEEN 1 AND 100),
  start_date         DATE NOT NULL,
  end_date           DATE,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (team_member_id, project_id, start_date),
  CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_expenses_project        ON expenses(project_id);
CREATE INDEX idx_deliverables_project    ON deliverables(project_id);
CREATE INDEX idx_deliverables_status_due ON deliverables(status, due_date);
CREATE INDEX idx_dependencies_depends_on ON deliverable_dependencies(depends_on_id);
CREATE INDEX idx_allocations_member      ON allocations(team_member_id);
CREATE INDEX idx_allocations_project     ON allocations(project_id);
CREATE INDEX idx_projects_status         ON projects(status);
