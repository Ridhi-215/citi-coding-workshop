-- Demo data for the presentation. Runs once, when the projects table is empty.
-- Dates are relative to CURRENT_DATE so the demo always shows current, overdue and upcoming work.

INSERT INTO team_members (full_name, email, job_title, department, location, employment_type, user_id) VALUES
  ('Mark Manager',     'manager@acme.com',          'Delivery Manager',     'PMO',         'London',    'employee',   (SELECT id FROM users WHERE email = 'manager@acme.com')),
  ('Cara Contributor', 'contributor@acme.com',      'Senior Developer',     'Engineering', 'Bengaluru', 'employee',   (SELECT id FROM users WHERE email = 'contributor@acme.com')),
  ('Priya Sharma',     'priya.sharma@acme.com',     'Program Manager',      'PMO',         'Bengaluru', 'employee',   NULL),
  ('Daniel Okafor',    'daniel.okafor@acme.com',    'Solutions Architect',  'Engineering', 'London',    'employee',   NULL),
  ('Sofia Martinez',   'sofia.martinez@acme.com',   'Backend Developer',    'Engineering', 'Madrid',    'employee',   NULL),
  ('Kenji Tanaka',     'kenji.tanaka@acme.com',     'Frontend Developer',   'Engineering', 'Remote',    'contractor', NULL),
  ('Aisha Rahman',     'aisha.rahman@acme.com',     'Data Engineer',        'Data',        'Hyderabad', 'employee',   NULL),
  ('Liam O''Connor',   'liam.oconnor@acme.com',     'QA Lead',              'Quality',     'Dublin',    'employee',   NULL),
  ('Mei Chen',         'mei.chen@acme.com',         'UX Designer',          'Design',      'Singapore', 'employee',   NULL),
  ('Rahul Verma',      'rahul.verma@acme.com',      'DevOps Engineer',      'Platform',    'Pune',      'employee',   NULL),
  ('Emma Fischer',     'emma.fischer@acme.com',     'Business Analyst',     'PMO',         'Berlin',    'contractor', NULL),
  ('Noah Williams',    'noah.williams@acme.com',    'Security Engineer',    'Platform',    'London',    'employee',   NULL);

INSERT INTO projects (code, name, description, status, priority, start_date, end_date, budget_planned, manager_id, created_by)
SELECT v.code, v.name, v.description, v.status, v.priority,
       CURRENT_DATE + v.start_offset, CURRENT_DATE + v.end_offset, v.budget,
       (SELECT id FROM team_members WHERE email = v.manager),
       (SELECT id FROM users WHERE email = 'admin@acme.com')
FROM (VALUES
  ('APOLLO',  'Apollo Customer Portal',       'Self-service portal for enterprise customers: billing, tickets and usage.',      'active',    'critical', -120,  60, 450000.00, 'priya.sharma@acme.com'),
  ('ORION',   'Orion Data Platform',          'Consolidate reporting into a single cloud data warehouse.',                       'active',    'high',      -90,  -5, 300000.00, 'manager@acme.com'),
  ('HERMES',  'Hermes Mobile App',            'Field-sales mobile app with offline ordering.',                                   'active',    'high',      -60,  90, 220000.00, 'manager@acme.com'),
  ('ATLAS',   'Atlas ERP Migration',          'Move finance and procurement from the on-prem ERP to SaaS.',                      'on_hold',   'medium',    -45, 150, 600000.00, 'priya.sharma@acme.com'),
  ('NOVA',    'Nova Design System',           'Shared UI component library and brand guidelines for all web products.',          'active',    'medium',    -30,  45,  80000.00, 'daniel.okafor@acme.com'),
  ('ZEUS',    'Zeus Security Hardening',      'SOC 2 readiness: SSO everywhere, secrets rotation and audit logging.',             'planned',   'high',       14, 120, 150000.00, 'daniel.okafor@acme.com'),
  ('IRIS',    'Iris Analytics Dashboards',    'Executive KPI dashboards for sales and operations.',                              'completed', 'low',      -200, -20,  60000.00, 'priya.sharma@acme.com'),
  ('TITAN',   'Titan Legacy Decommission',    'Retire the legacy order system after the ERP migration.',                         'cancelled', 'low',      -150, -30,  40000.00, 'manager@acme.com')
) AS v(code, name, description, status, priority, start_offset, end_offset, budget, manager);

-- Expenses: ORION is over budget, HERMES is close to budget, others are healthy.
INSERT INTO expenses (project_id, category, description, amount, incurred_on, created_by)
SELECT (SELECT id FROM projects WHERE code = v.code), v.category, v.description, v.amount, CURRENT_DATE + v.day_offset,
       (SELECT id FROM users WHERE email = 'manager@acme.com')
FROM (VALUES
  ('APOLLO', 'labor',    'Sprint 1-4 development',            95000.00, -100),
  ('APOLLO', 'labor',    'Sprint 5-8 development',            88000.00,  -50),
  ('APOLLO', 'software', 'Identity provider licences',        18000.00,  -80),
  ('APOLLO', 'services', 'Penetration test',                  12500.00,  -10),
  ('ORION',  'labor',    'Data engineering team',            180000.00,  -60),
  ('ORION',  'software', 'Warehouse compute (annual commit)', 95000.00,  -85),
  ('ORION',  'services', 'Migration consultancy',             48000.00,  -20),
  ('HERMES', 'labor',    'Mobile developers',                150000.00,  -30),
  ('HERMES', 'hardware', 'Test devices',                       9500.00,  -55),
  ('HERMES', 'software', 'App store and MDM licences',        42000.00,  -15),
  ('ATLAS',  'services', 'Vendor selection workshop',         35000.00,  -40),
  ('NOVA',   'labor',    'Design and front-end',              28000.00,  -20),
  ('NOVA',   'software', 'Design tool seats',                  3200.00,  -28),
  ('IRIS',   'labor',    'BI developer',                      42000.00, -120),
  ('IRIS',   'software', 'BI licences',                       11000.00, -150),
  ('TITAN',  'travel',   'Data-centre site visit',             2400.00, -140)
) AS v(code, category, description, amount, day_offset);

INSERT INTO deliverables (project_id, name, description, owner_id, status, due_date, completed_at)
SELECT (SELECT id FROM projects WHERE code = v.code), v.name, v.description,
       (SELECT id FROM team_members WHERE email = v.owner), v.status, CURRENT_DATE + v.due_offset,
       CASE WHEN v.status = 'completed' THEN now() + make_interval(days => v.due_offset - 2) END
FROM (VALUES
  ('APOLLO', 'Requirements sign-off',       'Approved scope and acceptance criteria', 'emma.fischer@acme.com',   'completed',   -100),
  ('APOLLO', 'Solution architecture',       'Architecture and security review',       'daniel.okafor@acme.com',  'completed',    -80),
  ('APOLLO', 'Billing module',              'Invoices, payments and credit notes',    'sofia.martinez@acme.com', 'in_progress',   10),
  ('APOLLO', 'Support ticket module',       'Ticket creation and tracking',           'contributor@acme.com',    'in_progress',   20),
  ('APOLLO', 'Customer portal UI',          'Responsive web front end',               'kenji.tanaka@acme.com',   'in_progress',   25),
  ('APOLLO', 'UAT',                         'User acceptance testing with customers', 'liam.oconnor@acme.com',   'not_started',   45),
  ('APOLLO', 'Go-live',                     'Production launch',                      'rahul.verma@acme.com',    'not_started',   60),
  ('ORION',  'Source system inventory',     'Catalogue all reporting sources',        'aisha.rahman@acme.com',   'completed',    -70),
  ('ORION',  'Warehouse landing zone',      'Accounts, networking and IAM',           'rahul.verma@acme.com',    'completed',    -50),
  ('ORION',  'ETL pipelines',               'Ingest finance and sales data',          'aisha.rahman@acme.com',   'blocked',      -12),
  ('ORION',  'Reporting cut-over',          'Switch reports to the new warehouse',    'emma.fischer@acme.com',   'not_started',   -3),
  ('HERMES', 'UX prototypes',               'Clickable prototypes for field tests',   'mei.chen@acme.com',       'completed',    -35),
  ('HERMES', 'Offline sync engine',         'Conflict-free order sync',               'sofia.martinez@acme.com', 'in_progress',   -4),
  ('HERMES', 'App store release',           'iOS and Android publication',            'kenji.tanaka@acme.com',   'not_started',   80),
  ('ATLAS',  'Vendor selection',            'Shortlist and choose the SaaS ERP',      'priya.sharma@acme.com',   'completed',    -20),
  ('ATLAS',  'Data migration plan',         'Mapping and reconciliation approach',    'aisha.rahman@acme.com',   'not_started',   40),
  ('NOVA',   'Component inventory',         'Audit existing UI components',           'mei.chen@acme.com',       'completed',    -15),
  ('NOVA',   'Core component library',      'Buttons, forms, tables, dialogs',        'kenji.tanaka@acme.com',   'in_progress',   15),
  ('NOVA',   'Documentation site',          'Usage guidelines and examples',          'mei.chen@acme.com',       'not_started',   40),
  ('ZEUS',   'SSO rollout plan',            'Inventory apps and SSO approach',        'noah.williams@acme.com',  'not_started',   30),
  ('ZEUS',   'Audit logging',               'Central audit log for all services',     'rahul.verma@acme.com',    'not_started',   90),
  ('IRIS',   'Sales dashboard',             'Pipeline and bookings KPIs',             'aisha.rahman@acme.com',   'completed',    -40),
  ('IRIS',   'Operations dashboard',        'Fulfilment and SLA KPIs',                'aisha.rahman@acme.com',   'completed',    -25)
) AS v(code, name, description, owner, status, due_offset);

-- Dependency chains (deliverable depends on prerequisite).
INSERT INTO deliverable_dependencies (deliverable_id, depends_on_id)
SELECT d.id, pre.id
FROM (VALUES
  ('APOLLO', 'Solution architecture', 'Requirements sign-off'),
  ('APOLLO', 'Billing module',        'Solution architecture'),
  ('APOLLO', 'Support ticket module', 'Solution architecture'),
  ('APOLLO', 'Customer portal UI',    'Solution architecture'),
  ('APOLLO', 'UAT',                   'Billing module'),
  ('APOLLO', 'UAT',                   'Support ticket module'),
  ('APOLLO', 'UAT',                   'Customer portal UI'),
  ('APOLLO', 'Go-live',               'UAT'),
  ('ORION',  'Warehouse landing zone', 'Source system inventory'),
  ('ORION',  'ETL pipelines',          'Warehouse landing zone'),
  ('ORION',  'Reporting cut-over',     'ETL pipelines'),
  ('HERMES', 'Offline sync engine',    'UX prototypes'),
  ('HERMES', 'App store release',      'Offline sync engine'),
  ('ATLAS',  'Data migration plan',    'Vendor selection'),
  ('NOVA',   'Core component library', 'Component inventory'),
  ('NOVA',   'Documentation site',     'Core component library'),
  ('ZEUS',   'Audit logging',          'SSO rollout plan')
) AS v(code, name, prerequisite)
JOIN projects p ON p.code = v.code
JOIN deliverables d ON d.project_id = p.id AND d.name = v.name
JOIN deliverables pre ON pre.project_id = p.id AND pre.name = v.prerequisite;

-- Allocations: Sofia (130%), Aisha (120%) and Kenji (110%) are over-allocated today.
INSERT INTO allocations (team_member_id, project_id, role_on_project, allocation_percent, start_date, end_date)
SELECT (SELECT id FROM team_members WHERE email = v.member), (SELECT id FROM projects WHERE code = v.code),
       v.role, v.pct, CURRENT_DATE + v.start_offset, CURRENT_DATE + v.end_offset
FROM (VALUES
  ('priya.sharma@acme.com',   'APOLLO', 'Program manager',      50, -120,  60),
  ('priya.sharma@acme.com',   'ATLAS',  'Program manager',      30,  -45, 150),
  ('manager@acme.com',        'ORION',  'Delivery manager',     50,  -90,  30),
  ('manager@acme.com',        'HERMES', 'Delivery manager',     40,  -60,  90),
  ('daniel.okafor@acme.com',  'APOLLO', 'Architect',            40, -110,  60),
  ('daniel.okafor@acme.com',  'NOVA',   'Lead',                 30,  -30,  45),
  ('sofia.martinez@acme.com', 'APOLLO', 'Backend developer',    80,  -90,  60),
  ('sofia.martinez@acme.com', 'HERMES', 'Sync engine developer', 50, -40,  30),
  ('contributor@acme.com',    'APOLLO', 'Developer',            70,  -90,  60),
  ('contributor@acme.com',    'NOVA',   'Developer',            20,  -20,  45),
  ('kenji.tanaka@acme.com',   'APOLLO', 'Front-end developer',  60,  -60,  60),
  ('kenji.tanaka@acme.com',   'NOVA',   'Front-end developer',  50,  -30,  45),
  ('aisha.rahman@acme.com',   'ORION',  'Data engineer',        80,  -90,  30),
  ('aisha.rahman@acme.com',   'ATLAS',  'Data migration',       40,  -10, 150),
  ('liam.oconnor@acme.com',   'APOLLO', 'QA lead',              50,  -30,  60),
  ('liam.oconnor@acme.com',   'HERMES', 'QA',                   30,  -30,  90),
  ('mei.chen@acme.com',       'NOVA',   'Designer',             60,  -30,  45),
  ('mei.chen@acme.com',       'HERMES', 'Designer',             20,  -60,  20),
  ('rahul.verma@acme.com',    'ORION',  'Platform engineer',    40,  -60,  30),
  ('rahul.verma@acme.com',    'APOLLO', 'Release engineer',     30,  -10,  60),
  ('emma.fischer@acme.com',   'ORION',  'Business analyst',     50,  -90,  30),
  ('noah.williams@acme.com',  'ZEUS',   'Security lead',        60,   14, 120),
  ('aisha.rahman@acme.com',   'IRIS',   'BI developer',         50, -200, -20)
) AS v(member, code, role, pct, start_offset, end_offset);
