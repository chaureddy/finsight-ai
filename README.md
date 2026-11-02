# FinSight AI

**See Your Money. Understand Your Future.**

FinSight AI is a personal finance workspace extended from its existing Stage 1 foundation. Dashboard figures in unauthenticated preview mode are expressly labelled illustrative demo data. Authenticated transactions and financial records are loaded from the Spring API and persisted in PostgreSQL.

## Run locally

Requirements: Node.js 20+, Java 21 or newer, and either Docker Desktop with Compose or a local PostgreSQL 18 server. Maven does not need a system install; the checked-in Maven Wrapper uses Maven 3.9.16 and the commands below keep its distribution and dependency cache inside the project. This workspace was verified with Node 24.18.1 and Java 25 on Windows 11.

### PowerShell development setup

Run these commands from a PowerShell terminal. The example password is for local development only; replace it before using the app with real data.

```powershell
Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai'
Copy-Item .env.example .env
```

Start PostgreSQL (requires Docker Desktop installed and running):

```powershell
docker compose up -d --wait postgres
```

In a second PowerShell terminal, launch the backend. Maven Wrapper runs in `backend`; environment variables are set in this shell because Spring Boot does not load `.env` files by default.

```powershell
Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai\backend'
$env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5432/finsight'
$env:DATABASE_USERNAME = 'finsight'
# Example alternate host/port: $env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5433/finsight'
$env:DATABASE_PASSWORD = 'development-only'
$env:COOKIE_SECURE = 'false'
$env:CORS_ORIGIN = 'http://localhost:5173'
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.maven-cache'
.\mvnw.cmd spring-boot:run
```

In a third terminal, run the frontend. Vite proxies `/api` to the backend, so browser requests and the HttpOnly session cookie remain same-origin in development.

```powershell
Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai'
npm ci
npm run dev
```

Open `http://localhost:5173`. Flyway applies migrations at backend startup; Hibernate validates the resulting schema. For a native PostgreSQL installation, start PostgreSQL separately and use the same backend environment-variable commands. No Docker/Maven software is installed or system settings modified by this project.

### PayPal Sandbox transaction import

This read-only integration calls PayPal OAuth 2.0 Client Credentials and the Transaction Search API from the backend. It does not process payments, issue refunds, move funds, or link individual users' personal PayPal accounts. Use only a PayPal Developer Sandbox REST app. In the backend PowerShell terminal, set the server-only variables before starting Spring Boot:

```powershell
$env:PAYPAL_CLIENT_ID = '<Sandbox app client ID>'
$env:PAYPAL_CLIENT_SECRET = '<Sandbox app secret>'
$env:PAYPAL_BASE_URL = 'https://api-m.sandbox.paypal.com'
.\mvnw.cmd spring-boot:run
```

Do not prefix either credential with `VITE_`, place them in frontend code, or commit them. The app rejects non-Sandbox API hosts. The PayPal Developer app must be granted the `https://uri.paypal.com/services/reporting/search/read` scope. The Transaction Search API requires PayPal Partner Network authorization to search on behalf of third parties; without that authorization it can read only the account available to the configured app. This deployment permanently binds the configured Sandbox source to its first FinSight user. A successful token alone does not mean it is connected: a successful report response is required.

After signing in, open **PayPal Sandbox**, choose a date window of at most 31 days, and select **Verify & import**. The page reports the persisted import, duplicate, and review counts. Eligible records are successful, INR, supported payment events whose amount includes an explicit sign and has no separate fee. Refunds/reversals, fees, unsettled payments, other currencies, unsupported event types, malformed records, and records without transaction IDs stay in the review list; they do not affect cash-flow analytics. No exchange rates are invented. PayPal states that transactions can take up to three hours to appear and its report history is limited to three years. The API's transaction ID is not always unique, so idempotency uses a fingerprint of the complete balance-affecting transaction detail while retaining the PayPal ID as the transaction external reference.

The backend endpoints are `GET /api/integrations/paypal/status`, `GET /api/integrations/paypal/review`, and CSRF-protected `POST /api/integrations/paypal/import` with `{ "startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD" }`. All endpoints require a FinSight session. Imported rows are standard owner-scoped transactions with `sourceProvider=PAYPAL`, the PayPal external reference, and `currencyCode=INR`. See PayPal's [OAuth authentication guide](https://developer.paypal.com/api/rest/authentication/), [Transaction Search API specification](https://github.com/paypal/paypal-rest-api-specifications/blob/main/openapi/reporting_transactions_v1.json), and [transaction event-code reference](https://developer.paypal.com/reports/reference/t-codes/) for current scopes, data fields, and event classifications.

### PostgreSQL 18 verification on Windows

The project targets PostgreSQL 18: `docker-compose.yml` pins `postgres:18-alpine`, and the normal Spring profile connects through `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. There is no runtime H2 fallback. H2 is test-scoped and used by the automated integration suite only. Flyway runs at normal application startup; Hibernate validates the resulting schema. Dependency alignment is Spring Boot 3.5.16, Flyway 13.12.0 (both `flyway-core` and `flyway-database-postgresql`), and pgJDBC 42.7.14. Boot moved from 3.4.1 to 3.5.16 because testing Flyway 13 with Boot 3.4.1 failed during context initialization: its auto-configuration called a Flyway builder method removed in Flyway 11. Flyway moved from 10.20.1 (release notes document support through PostgreSQL 17) to 13.12.0 (the current supported-version list includes PostgreSQL 18). pgJDBC moved from 42.7.4 (CI tested PostgreSQL 15, 16, and 17 beta) to maintained version 42.7.14; pgJDBC added PostgreSQL 18 to its CI matrix in 42.7.13. References: [Flyway release notes](https://documentation.red-gate.com/fd/release-notes-for-flyway-engine-179732572.html), [Flyway supported database versions](https://documentation.red-gate.com/fd/supported-database-versions-143754067.html), [Spring Boot 3.5 API](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/flyway/FlywayProperties.html), and [pgJDBC changelog](https://github.com/pgjdbc/pgjdbc/blob/master/CHANGELOG.md).

Compose publishes host port 5432 by default; set `POSTGRES_PORT` to change the host-side port and set `DATABASE_URL` to the same port. PostgreSQL 18 uses a separate `finsight-data-pg18` volume. Any older `finsight-data` volume is left untouched; Compose does not upgrade or attach an older major-version data directory.

Use the PostgreSQL 18 service and make sure its installation's `bin` directory contains `psql.exe` and `pg_isready.exe`. The verifier checks the service and queries the actual server version before making database changes. It creates the configured database role and database only when absent; it will not change an existing role password or database owner. It asks for admin and application passwords with hidden input if the corresponding environment variables are not set, and does not print client stderr that could echo SQL containing a password.

Use PowerShell from the project directory:

```powershell
Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai'
Get-Service -Name 'postgresql*'
```

The defaults are `jdbc:postgresql://127.0.0.1:5432/finsight`, role `finsight`, and database `finsight`. IPv4 loopback avoids Windows `localhost` resolving to `::1` when the credentials are valid for the IPv4 connection. The JDBC URL carries the configurable server host and port. If PostgreSQL listens on port 5433, for example:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5432/finsight'
$env:DATABASE_USERNAME = 'finsight'
# For a non-default native port, set this to e.g.:
# $env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5433/finsight'
# Compose can publish a different host port with POSTGRES_PORT (default 5432).
# $env:POSTGRES_PORT = '5433'
# Optional: the verifier prompts securely if DATABASE_PASSWORD is not set.
# $env:DATABASE_PASSWORD = 'your-local-role-password'
# Optional if the PostgreSQL administrator role is not named postgres:
# $env:PG_ADMIN_USERNAME = 'postgres'
# $env:PG_ADMIN_PASSWORD = 'your-local-admin-password'
```

Run the real PostgreSQL/API verification. It requires PostgreSQL 18 administrator credentials and application-role credentials (prompted securely when unset). It builds and tests the backend, starts the packaged application against PostgreSQL, checks Flyway V1–V3, then exercises transaction, asset, liability, investment, insurance, budget, goal, and recurring transaction CRUD; recurring pause/resume and idempotent occurrence recording; financial overview arithmetic; forecast, financial health, and spending endpoints; cross-user isolation; and logout invalidation. It creates only uniquely named temporary test accounts and removes them (including their owned records) afterward. It never resets existing credentials or deletes pre-existing user records. Use `-PostgresBin` if PostgreSQL's `bin` directory is not on PATH or in the standard install location:

```powershell
Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai'
.\scripts\verify-postgres.ps1
# Example with an explicit PostgreSQL 18 client tools directory:
# .\scripts\verify-postgres.ps1 -PostgresBin "$env:ProgramFiles\PostgreSQL\18\bin"
# If port 8080 is occupied, choose another verification API port; the script identifies the listener and never stops it:
# .\scripts\verify-postgres.ps1 -ApiBaseUrl 'http://127.0.0.1:8081'
```

You can validate the default IPv4 endpoint and `DATABASE_URL` host/port overrides without contacting PostgreSQL:

```powershell
Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai'
.\scripts\test-postgres-url.ps1
```

For the actual browser-to-API-to-PostgreSQL flow, keep the backend running with `-KeepBackend`, then use another PowerShell terminal for Vite:

```powershell
# Terminal 1: run verifier and keep its PostgreSQL-backed backend alive
Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai'
.\scripts\verify-postgres.ps1 -KeepBackend

# Terminal 2: start the real frontend (Vite proxies /api to localhost:8080)
Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai'
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8080'
npm run dev
```

When using a custom verification port such as 8081, set `VITE_API_PROXY_TARGET` to `http://127.0.0.1:8081` in the Vite terminal before `npm run dev`.

Open `http://localhost:5173`, register a fresh user, sign in, and add/edit/delete a transaction. These browser requests use the real Spring API and PostgreSQL; the verifier does not mock API calls. The dashboard demo mode remains separate and labelled. Stop the retained backend using the PID printed by the verifier: `Stop-Process -Id <PID>`.

If PostgreSQL is unavailable, the included H2-backed integration suite remains a fast application test, but it does not satisfy real-database verification. In PowerShell:

```powershell
Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai\backend'
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.maven-cache'
.\mvnw.cmd test
```

That suite verifies application wiring but does not provide a PostgreSQL server and cannot satisfy the real database acceptance check. Install PostgreSQL yourself through the normal Windows installer when you are ready to perform that check. No software was installed or services changed here.

Troubleshooting:

- **No PostgreSQL 18 service found / service stopped:** start the existing PostgreSQL 18 service from Windows Services. The verifier does not start Windows services.
- **`psql.exe` or `pg_isready.exe` not found:** add the PostgreSQL 18 `bin` directory to this PowerShell session's PATH, or pass it with `-PostgresBin`.
- **Connection refused / timeout:** confirm the service is running, `DATABASE_URL` host and port are correct, and PostgreSQL listens on that address. Check `pg_hba.conf` if local authentication rejects the connection.
- **Password authentication failed for `postgres`:** first confirm the target service/port and `PG_ADMIN_USERNAME`; a remembered password can be correct for another PostgreSQL instance. Check credentials without changing the database using this interactive prompt (replace the port if needed):

  ```powershell
  & "$env:ProgramFiles\PostgreSQL\18\bin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -d postgres -W
  ```

  `-W` prompts rather than accepting the password in the command. The verifier similarly uses hidden input unless `PG_ADMIN_PASSWORD` is set, passes it only through the process environment to `psql`, and restores that environment when it exits. It never resets role passwords or edits `pg_hba.conf`. If unsure, try pgAdmin or another known superuser. An authenticated superuser can use psql's interactive `\password postgres` command to set a new password without placing it in SQL history. If no administrator can connect, back up the service's `pg_hba.conf` and follow the [PostgreSQL authentication guide](https://www.postgresql.org/docs/18/client-authentication.html) and [pg_hba.conf reference](https://www.postgresql.org/docs/18/auth-pg-hba-conf.html). Any temporary rule must be localhost-only, short-lived, restored immediately after changing the password, and followed by an authenticated test. Never use a broad `trust` rule.
- **Database owner mismatch:** the verifier stops rather than changing ownership. Review the existing database with your PostgreSQL administrator.
- **Wrong server version:** this project requires major version 18 exactly; the verifier rejects any other major version.
- **Backend port already in use:** stop the existing development API before running the verifier; it refuses to replace an API already listening on the target port. Vite's development proxy expects the backend on port 8080.
- **Flyway/schema startup error:** inspect the PostgreSQL connection details and backend logs under `backend\\target` created by the verifier; do not switch runtime to H2 to bypass the migration.

For a direct cross-origin API, set `VITE_API_URL` to the API base URL and configure `CORS_ORIGIN` to the exact frontend origin; use HTTPS and `COOKIE_SECURE=true` in deployed environments. The current `SameSite=Lax` cookie setup is intended for same-site app/API deployment.

## Current API

### Financial management API

All endpoints below require an authenticated session and the standard `X-CSRF-TOKEN` header for POST, PUT, and DELETE. Records are scoped to the session user; request bodies never accept a `userId`. Foreign-owned IDs return 404.

| Module | Endpoints |
|---|---|
| Assets | `GET/POST /api/assets`; `PUT/DELETE /api/assets/{id}` |
| Liabilities | `GET/POST /api/liabilities`; `PUT/DELETE /api/liabilities/{id}` |
| Investments | `GET/POST /api/investments`; `PUT/DELETE /api/investments/{id}` |
| Insurance | `GET/POST /api/insurance`; `PUT/DELETE /api/insurance/{id}` |
| Financial overview | `GET /api/financial-overview` |

### Milestone 3 — financial intelligence

| Capability | API |
|---|---|
| Budgets | `GET/POST /api/budgets`; `PUT/DELETE /api/budgets/{id}` |
| Goals | `GET/POST /api/goals`; `PUT/DELETE /api/goals/{id}` |
| Recurring definitions | `GET/POST /api/recurring-transactions`; `PUT/DELETE /api/recurring-transactions/{id}` |
| Upcoming schedules | `GET /api/recurring-transactions/upcoming` (next 30 days) |
| Pause/resume | `PATCH /api/recurring-transactions/{id}/active` |
| Explicitly record due occurrence | `POST /api/recurring-transactions/{id}/occurrences` with `occurrenceDate` |
| Three-month forecast | `GET /api/forecast` |
| Financial health | `GET /api/financial-health` |
| Spending analysis | `GET /api/intelligence/spending` |

All endpoints require an authenticated session. Mutations require the existing CSRF header. Forecasts and scores are deterministic Java calculations over owner-scoped records. Scheduled definitions do not trigger payments or automatically create actual transactions; recording an occurrence is an explicit user action and `(recurring_transaction_id, occurrence_date)` is unique in the database.

#### Calculation and status rules

- Budgets cover complete calendar-month ranges. Current-month `amountSpent` sums only `EXPENSE` transactions in the budget category. `alertTriggered` becomes true at or above the configured percentage; `exceeded` is true only when spending is greater than the limit. Overlapping periods for the same owner/category are rejected.
- Goal progress is `currentAmount / targetAmount`, capped at 100%; remaining is `max(target-current, 0)`. Required monthly savings is remaining divided by the count of calendar months to target (minimum one). Projected completion assumes the average monthly net savings across the last three complete months continues unchanged, with no interest; if it is zero or negative, no completion date is projected and the goal is `AT_RISK`. A goal is `COMPLETED` when current progress meets or exceeds target. Projections are estimates, never promises.
- Forecast baseline is the average of the last three complete calendar months; missing months count as zero. Active recurring amounts are normalized as daily × 30, weekly × 52/12, monthly × 1, yearly × 1/12. Since user-entered recurrence definitions may already be represented in actual history, the recurring estimate is used as a floor (`max(historical average, recurring monthly equivalent)`) rather than added to history. Expected closing cash starts with entered Cash and Bank Account asset values and adds projected monthly savings. No transfers, market returns, or unknown transactions are assumed. Output includes assumptions and a confidence label.
- Financial health uses five visible weighted components: savings rate (30 points; linear to 20%), emergency fund (25; cash/bank value divided by average monthly expenses, full score at 6 months), EMI-to-income (20; full at 20% or less, linear to zero at 40%), budget adherence (15; full within limits, linear to zero at 20% overspend), and average goal progress (10; linear to 100%). Missing components are omitted and the total is normalized over available weights; confidence reports available weight. This is not a credit score.
- Spending observations compare the current category spend to the user's own average across the previous three complete months. An observation is marked elevated only if it is at least 50% higher and at least ₹1,000 more. It is not a fraud finding. Current-month values are partial-month actuals; historical month comparisons are labeled accordingly.

Frontend routes: `/budgets`, `/goals`, `/recurring-payments`, `/forecast`, `/financial-health`, and `/spending-intelligence`. Each page uses the authenticated APIs, supports loading/empty/error states, and separates scheduled values from actual transactions.

Money fields use decimal values persisted as PostgreSQL `NUMERIC(19,2)`. Investment values are manually entered; there are no live market quotes or modeled returns. Accounting convention: assets are non-investment holdings, investment holdings are recorded separately, and net worth is assets + investment current values − outstanding liabilities.

- `POST /api/auth/register` — create an account (minimum 12 character password).
- `POST /api/auth/login` — session login using form fields `email` and `password`.
- `GET /api/auth/me` — current user profile.
- `POST /api/auth/logout` — terminate the session.
- `PUT /api/transactions/{id}` — update an owned transaction.
- `GET /api/transactions` — list only the signed-in user's transactions.
- `POST /api/transactions` — add an owned transaction with a positive decimal amount.
- `DELETE /api/transactions/{id}` — delete an owned transaction.
- `GET /api/analytics/cash-flow` — current calendar month's income, expenses, net cash flow, and calculated savings rate for the signed-in user.
- `GET /api/auth/csrf` — CSRF token for same-origin/session requests.
- `GET /api/health` — simple process health response (not a database readiness check).

Validation errors and authentication errors are returned by Spring. Do not expose database or provider secrets to frontend environment variables. API docs and OpenAPI generation remain to be implemented.

## Product scope and data handling

The UI includes login and registration, a separate labelled demo mode, authenticated transaction CRUD, assets, liabilities, investments, insurance, financial overview, budgets, goals, recurring schedules, cash-flow forecast, financial health, spending intelligence, logout, session expiry handling, and loading/error/empty states. Financial pages use authenticated backend records. Demo mode does not call or modify these APIs. No live bank or market data is connected. AI assistance and PayPal are not implemented; their environment keys are placeholders only.

## Deployment

For a real deployment, use managed PostgreSQL with TLS, set `COOKIE_SECURE=true`, set a strict HTTPS `CORS_ORIGIN`, provision secrets through the deployment platform, and terminate TLS at a trusted proxy. Add backups, migrations review, observability, rate limiting, account recovery, and an incident response process before handling real financial data. See [SECURITY.md](SECURITY.md) and [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md).
