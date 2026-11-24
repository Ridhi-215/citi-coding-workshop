# ACME Project Tracker - Progress

Branch: `feature/acme-tracker`. Stack: React + Material UI frontend, Java 21 Lambda
backend (runs on the `java25` runtime), PostgreSQL (local) / Aurora PostgreSQL (AWS).

## Status

| Phase | Item | Status |
|---|---|---|
| 1 | Java packaging check (J7) on LocalStack | Done - jar must be in `lib/` (see below) |
| 1 | Template fixes J3-J6, shared build step, #1, #2, #4 | Done |
| 1 | Local Postgres round-trip from Lambda | Blocked - Postgres listens on 127.0.0.1 only (needs `sudo`) |
| 1 | First AWS deploy + Aurora round-trip | Done - Java 25.0.5 runtime, Aurora 17.7 over SSL, migrations + seed applied |
| 1 | CloudFront: API via `/api/auth-service`, API 404 passes through as JSON 404 | Done |
| 2 | Shared module (router, auth, validation, query builder) | Written; router unit tests (17) + migrations IT (3) pass |
| 2 | auth / projects / deliverables / resources / dashboard services | Not started |
| 3 | Seed data, JUnit/Mockito unit tests, integration tests, JaCoCo 80% | Not started |
| 4 | Frontend | Not started |
| 5 | AWS deploy, README | Not started |

Java fallback timebox: started 2026-11-24 07:40 UTC, 1.5 h to have the Java path working
locally and in AWS (otherwise recommend switching the backend to Python).

## Findings and fixes

- **J7 - jar location.** A shaded jar at the root of the Lambda zip gives
  `ClassNotFoundException: com.example.Handler`; the Java runtime only loads jars from
  `/var/task/lib`. The build step now moves the jar to `target/lib/` (`infra/locals.tf`).
  Verified on LocalStack (Java 25.0.5 runtime).
- **J3 - cleanup command** `find ./target ! -name '<svc>*.jar' -delete` exited 1 (it tried
  to delete `target/` itself). Fixed with `-mindepth 1`.
- **J4 - rebuild trigger** hashed file sizes only and ignored shared code. Now hashes file
  contents of the service and `backend/_common`, plus the build command.
- **J5 - JDBC driver** discovery: shade keeps `META-INF/services` (ServicesResourceTransformer)
  and `Db` uses `org.postgresql.Driver` directly.
- **J6 - memory** 1024 MB for Java services (128 MB for others); `JAVA_TOOL_OPTIONS`
  stops the JIT at tier 1 for faster cold starts.
- **Shared module** `backend/_common` (parent POM + `common-lib`) is installed to `~/.m2`
  by `null_resource.java_common_build` before any service builds.
- **#1 CloudFront** no longer rewrites every 404 (including API 404s) to `index.html`;
  a CloudFront Function on the S3 behavior serves `index.html` for app routes.
- **#2 local proxy** now forwards `Authorization` and other request headers.
- **#4 JWT secret** `random_password.jwt_secret` is passed to every Lambda as `JWT_SECRET`.
- **Setup script** `bin/setup-environment.sh` wrote `JAVA_HOME` with an unexpanded
  `$JAVA_VERSION`; fixed. It also does not install Maven (installed manually to
  `~/.local/share/apache-maven-3.9.9`, linked from `~/.local/bin/mvn`).

- **Aurora resume timeout.** The first request after Aurora paused (0 ACU) failed: pgjdbc
  waits only 5 s for the SSL handshake reply (`sslResponseTimeout`). Raised to 30 s
  together with `loginTimeout`, plus one connection retry.
- **AWS region.** `~/.bashrc` sets `AWS_REGION='ap-south-1'` without `export`, so scripts
  fell back to `us-east-1` and `terraform init` failed with a 301 on the state bucket.
  Deploys currently pass `AWS_REGION=ap-south-1` explicitly.

## AWS endpoints

- Website / API base: https://dbi1uqut4q6ch.cloudfront.net
- Health: `GET /api/auth-service/health/db`

## Measurements

- Java cold start (Init Duration) on 1024 MB: ~320 ms; warm DB request via CloudFront: ~90 ms.

## Event shape (for the router)

- Direct Function URL (LocalStack and AWS) and CloudFront: `rawPath` is the full path,
  e.g. `/api/auth-service/health`. The local dev proxy strips `/api/<service>`.
  The router accepts both.

## Open items

- Local Postgres must listen on `0.0.0.0` and accept the Docker range (`start-dev.sh`
  does this with `sudo`).
- `~/.bashrc` line with `AWS_REGION` needs `export`.
