# ACME Project Tracker

A project management and tracking platform for ACME Inc. It answers:

* What is the status of active projects, and which projects are at risk (and why)?
* How are people allocated across projects, and who is over-allocated (above 100%)?
* Which deliverables are done, overdue or blocked, and what depends on what?
* How much budget has each project consumed versus plan?

The workshop guide this repository is based on is in [docs/README.md](./docs/README.md).

## Architecture

```text
Browser (React + MUI)
   │  /api/<service>/...           CloudFront (AWS) or the dev proxy on :3001 (local)
   ▼
AWS Lambda (Java 21 code on the java25 runtime, Function URLs)
   auth-service          register, login, refresh, current user, user admin
   projects-service      projects + expenses, budget/progress/risk roll-ups
   deliverables-service  deliverables, dependencies, dependency chains
   resources-service     team members, allocations, over-allocation
   dashboard-service     portfolio metrics for the dashboard (read-only)
   │  JDBC (one reused connection per Lambda container)
   ▼
PostgreSQL (local) / Aurora PostgreSQL Serverless v2 (AWS)
```

* **Backend** (`backend/`): one Maven module per service, all built on a shared library
  `backend/_common/common-lib` (router, JWT auth, role checks, validation, query builder,
  JSON responses, database access, migrations and seed data). No framework: plain JDBC,
  Jackson, java-jwt and jBCrypt keep cold starts around 0.3 s.
* **Database**: migrations in `common-lib/src/main/resources/db/migration` run automatically
  (under an advisory lock) on the first request to any service. Roll-ups live in SQL views
  (`project_overview`, `deliverable_overview`, `member_overview`) so every service and the
  dashboard use the same definitions. Demo data is seeded when the tables are empty.
* **Frontend** (`frontend/`): React 19, Material UI, MUI X Charts, React Router and
  react-responsive (sidebar collapses to a drawer and tables become cards on small screens).
* **Infrastructure** (`infra/`): Terraform discovers every `backend/*/pom.xml`, builds the
  shared library first, then each service, and deploys Lambdas, CloudFront and S3.

### Roles

| Role | Read | Create / update | Delete | Manage users |
|---|---|---|---|---|
| Admin | ✓ | ✓ | ✓ | ✓ |
| Manager | ✓ | ✓ | ✓ | |
| Contributor | ✓ | ✓ | | |
| Viewer | ✓ | | | |

Roles are enforced by the backend on every endpoint (403 otherwise); the frontend only hides
what a role cannot do. Self-registration always creates a Viewer; an Admin can change roles.
Only `GET /health`, `GET /api/auth-service/health/db`, `POST /login`, `POST /register` and
`POST /refresh` (which needs a valid refresh token) work without a token.

### API conventions

* Base path `/api/<service>`; list endpoints accept `search`, filters, `sort`, `order`,
  `limit` (1-200) and `offset`, and return `{ items, total, limit, offset }`.
* Status codes: 200, 201, 204, 400, 401, 403, 404, 409 (email already registered), 500.
* Errors always look like
  `{"error": {"code": "VALIDATION_FAILED", "message": "...", "details": {"field": "problem"}}}`;
  500 responses never include internal details.
* Access tokens last 30 minutes, refresh tokens 7 days; the frontend refreshes once
  automatically and sends the user to the login page when the session has expired.

## Demo accounts

Every environment is seeded with one account per role. All use the password `Demo@1234`.

| Email | Role |
|---|---|
| `admin@acme.com` | Admin |
| `manager@acme.com` | Manager |
| `contributor@acme.com` | Contributor |
| `viewer@acme.com` | Viewer |

These accounts are for demonstrations only: change their passwords or delete them before
using the system with real data.

To put an AWS environment back to exactly the demo data (this deletes **all** data,
including every account created since), invoke the auth Lambda directly with your AWS
credentials. This is not reachable through the website or the public API:

```sh
aws lambda invoke --function-name coding-workshop-auth-service-<participant-id> \
  --cli-binary-format raw-in-base64-out --payload '{"maintenance":"reset-demo-data"}' /dev/stdout
```

## Setup

### Prerequisites

Run `./bin/setup-environment.sh` once (see [bin/README.md](./bin/README.md)); it installs
Node.js, Docker, LocalStack, Terraform, the AWS CLI and a JDK. It does not install Maven;
install Maven 3.9 without root access like this:

```sh
mkdir -p ~/.local/share ~/.local/bin
curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz \
  | tar -xz -C ~/.local/share
ln -sf ~/.local/share/apache-maven-3.9.9/bin/mvn ~/.local/bin/mvn
export PATH="$HOME/.local/bin:$PATH"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # a JDK 21 or newer
mvn -version
```

PostgreSQL must accept connections from the LocalStack Lambda containers. `start-dev.sh`
configures this with `sudo` (it sets `listen_addresses = '*'` and allows `172.16.0.0/12`);
if it cannot prompt for a password, run its PostgreSQL step in a normal terminal.

### Run locally

```sh
./bin/start-dev.sh
```

This starts PostgreSQL and LocalStack, builds and deploys all services to LocalStack,
writes `frontend/.env.local`, starts the API proxy on http://localhost:3001 and the app on
http://localhost:3000. The first request creates the tables and seeds the demo data.

### Tests and coverage

```sh
# Backend: unit tests + integration tests against the local PostgreSQL (database acme_test),
# JaCoCo reports in backend/*/target/site/jacoco; the build fails below 80% line coverage.
mvn -f backend/_common/pom.xml verify

# Frontend: lint, Jest + Testing Library, coverage report in frontend/coverage
cd frontend
npm install
npm run lint
npm run test:coverage
```

Integration tests connect to `127.0.0.1:5432` as `postgres` / `postgres123` by default;
override with `TEST_POSTGRES_HOST`, `TEST_POSTGRES_PORT`, `TEST_POSTGRES_USER` and
`TEST_POSTGRES_PASS`.

### Deploy to AWS

```sh
export AWS_REGION=<your workshop region>
./bin/deploy-backend.sh aws
./bin/deploy-frontend.sh aws
```

The JWT signing secret is generated by Terraform (`random_password.jwt_secret`) and passed
to the Lambdas as `JWT_SECRET`; it is never stored in the repository. The website and API
are both served from the CloudFront URL that `deploy-frontend.sh` prints.

## Design decisions and trade-offs

* **Plain Java instead of Spring.** Lambda cold starts stay around 0.3 s at 1024 MB with a
  small shaded jar. The price is a small hand-written router and validator in `common-lib`.
* **One reused JDBC connection per Lambda container** instead of a pool: a Lambda container
  handles one request at a time. The connection is validated and reopened when needed, and
  timeouts allow for Aurora Serverless resuming from 0 ACU (the first request after an idle
  period takes ~15-20 s).
* **Migrations and seeding run on first request** rather than as a deploy step, so a fresh
  environment (local or AWS) works without manual database setup. An advisory lock keeps
  concurrent cold starts safe.
* **Budget consumed = sum of expenses.** Allocations are not costed (no rates in scope).
* **At risk** = active or on-hold and any of: over budget, at least 90% of budget used, past
  end date, an overdue deliverable, or a blocked deliverable. The reasons are returned with
  each project.
* **Over-allocation is allowed but reported**: finding over-allocated people is one of the
  questions the tool must answer, so saving an allocation that pushes someone above 100%
  succeeds and returns a warning.
* **Dependencies** stay within one project, cycles are rejected, and a deliverable cannot
  be completed while its prerequisites are open.
* **Tokens in `localStorage`** keep the frontend simple; the access token is short-lived.
  An HttpOnly-cookie session would be stronger against XSS.
* **Least privilege for sign-up**: anyone can register, but only as a Viewer.

## Project layout

```text
backend/_common/          parent POM + common-lib (shared code, migrations, seed data)
backend/<name>-service/   one Lambda per service (com.example.Handler)
frontend/src/             pages, components, services (API calls), context (auth, notifications)
infra/                    Terraform (Lambda, CloudFront, S3, Aurora)
bin/                      setup, local development and deployment scripts
```

## License

This library is licensed under the MIT-0 License.
See the [LICENSE](./LICENSE) resource for more details.
