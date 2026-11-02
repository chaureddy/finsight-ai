# Development status

## Current milestone — PayPal Sandbox reporting import (2026-11-02)

### Implemented

- Backend-only OAuth 2.0 Client Credentials integration and PayPal Transaction Search pagination against the fixed Sandbox API host. OAuth tokens are cached in memory until shortly before expiry; the reporting call refreshes after a 401. Connection/read timeouts and two-attempt retries cover transient reporting and token failures.
- Authenticated `GET /api/integrations/paypal/status`, `GET /api/integrations/paypal/review`, and CSRF-protected `POST /api/integrations/paypal/import` APIs. Requests derive ownership from the session; a database unique constraint binds the configured Sandbox source to one FinSight user.
- Flyway V4 adds import-source fields, a Sandbox connection owner, and persisted import/review rows. A full balance-affecting transaction-detail fingerprint is unique per connection because PayPal transaction IDs are not always unique; the original ID remains the transaction external reference.
- Imports automatically create transactions only for successful supported INR records with a signed nonzero amount, no separate fee, valid date, and valid PayPal ID. Refunds/reversals, fees, unsettled records, unsupported currencies/types/precision, and missing-ID records persist in the review queue. No foreign currency conversion or payment action is performed.
- Added a PayPal Sandbox page with configuration/status, date range, import outcome, and review records. Demo Mode shows no PayPal records.

### Files changed in this milestone

- `README.md`, `SECURITY.md`, `DEVELOPMENT_STATUS.md`, `NEXT_STEPS.md`, `.env.example`
- `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/migration/V4__paypal_sandbox_import.sql`
- `backend/src/main/java/ai/finsight/transaction/FinancialTransaction.java`
- New backend package `backend/src/main/java/ai/finsight/paypal/`: `PayPalApiClient.java`, `PayPalClientException.java`, `PayPalConnection.java`, `PayPalConnectionRepository.java`, `PayPalController.java`, `PayPalExceptionAdvice.java`, `PayPalGateway.java`, `PayPalImportPersistenceService.java`, `PayPalImportRecord.java`, `PayPalImportRecordRepository.java`, and `PayPalTransactionData.java`
- `backend/src/test/java/ai/finsight/paypal/PayPalApiClientTest.java`, `backend/src/test/java/ai/finsight/paypal/PayPalIntegrationTest.java`
- `src/App.tsx`, `src/api.ts`, `src/PayPalPage.tsx`, `src/paypal.css`, `src/PayPalPage.test.tsx`

### Verification results

- Backend full suite (final run after RFC3339 date encoding assertions): 20 tests passed, 0 failures/errors. This includes five mocked PayPal API client tests (OAuth acquisition/failure, token refresh after 401, pagination/date encoding, transient server error, timeout retry, fixed Sandbox URL) and one authenticated integration test for CSRF, source isolation, persistence, currency/refund/fee/missing-ID review, duplicate suppression, and cross-user transaction access.
- Final focused API-client rerun: 5 tests passed. One intermediate focused run failed because the test expected decoded RFC3339 query parameters while the outgoing URI correctly percent-encodes colons; the matcher was corrected and the test then passed. No final test failures remain.
- Earlier in implementation, tests caught an H2-incompatible multi-column `ALTER TABLE`, a `CHAR`/`VARCHAR` schema mismatch, a frontend string-matcher issue, and a TypeScript library-target mismatch. Each was corrected; the later full backend/frontend suites passed. These are resolved failures, not known current defects.
- Flyway V1–V4 and Hibernate schema validation passed in H2 PostgreSQL compatibility mode. **V4 has not been run against the actual PostgreSQL 18 server in this milestone.**
- Frontend: 9 Vitest tests passed; `npm run build` passed, including TypeScript typecheck. Vite reports the existing large-bundle advisory (>500 kB).
- No PayPal Developer credentials were present or used. A real Sandbox OAuth/reporting request and actual imported Sandbox records are therefore unverified; status does not become connected until a successful report response is persisted.
- No commit or push was created.

### Remaining setup and limitations

- Set `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, and `PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com` in the backend process only. Grant the app `https://uri.paypal.com/services/reporting/search/read` and confirm Transaction Search works for its account.
- PayPal documents that third-party account searches require PayPal Partner Network authorization. This implementation is a first-party configured Sandbox app-account integration, not individual user-consented PayPal linking; one FinSight user owns the configured source per database. No live API, disconnect/reassignment, webhook, payment, refund, or transfer support is implemented.
- The reporting endpoint may lag by three hours and exposes at most three years of history. Imports are limited to 31-day date ranges. Non-INR, fee-bearing, pending, reversed/refund, unsupported, or ambiguous data remains review-only; a record without a transaction ID is persisted under an opaque detail fingerprint for review.
- **Unverified mapping assumption:** auto-import currently interprets the sign of PayPal `transaction_amount.value` as the account cash-flow direction. That assumption has not been checked against a real Sandbox response. Confirm incoming and outgoing cases against PayPal documentation/provider responses before relying on imported totals; otherwise route the rows to review.
- Import idempotency is covered sequentially and enforced with a connection row lock plus database-unique detail fingerprint. A concurrent database race test is still outstanding.
- PostgreSQL 18 V4 verification and browser-to-real-PostgreSQL-to-PayPal Sandbox verification remain outstanding. Historical milestone notes below record earlier states and test counts.

## Repository audit

The designated project contained the FinSight foundation described above. It does not have its own `.git` directory, but `git rev-parse` confirms it is a subdirectory of `C:/Users/P.Saravanan`'s parent Git worktree (`Documents/finsight-ai/`). That parent contains unrelated user files; it was not initialized, staged, committed, or modified outside this project.

## Completed in this foundation

- React + TypeScript + Vite shell with responsive light-theme navigation and dashboard.
- Clearly labelled sample dashboard data, cash-flow chart, transaction list/search, add-transaction modal, and empty states for other sections.
- Spring Boot 3 API source targeting Java 21, Spring Security session authentication, BCrypt registration, Flyway PostgreSQL schema, and owner-scoped transaction list/create/delete endpoints.
- Docker Compose PostgreSQL service, environment example, initial README and security limitations.
- Vitest coverage for demo labeling, browser-preview transactions, authenticated data, register/login/create, edit/delete, and logout; dependency advisory remediation (zero npm audit findings).
- Deterministic current-month cash-flow aggregation API with owner-scoped database sums and JUnit unit coverage authored.
- Official Maven Wrapper 3.3.4 scripts configured for Maven 3.9.16 under `backend/`; Maven distribution and dependency caches are directed into ignored project-local directories. No system Maven installation was performed.
- React Axios client with configurable `VITE_API_URL`, credentialed requests, CSRF bootstrap and stale-token retry, registration/login/current-user/logout flows, authenticated dashboard data, and transaction list/create/update/delete.
- Owner-filtered monthly dashboard cash flow, visible unauthenticated demo mode, session-expiry return to sign-in, and loading/error/empty states.
- Backend update endpoint and Spring integration coverage for Flyway migration, CSRF bootstrap, registration/login/current user, create/list/update/delete, cross-user isolation, cash-flow summary, and logout.

## In progress / remaining

- PostgreSQL 18 V1–V3 were verified in the previous milestone. V4 and the PayPal browser-to-PostgreSQL flow remain outstanding in the current milestone.
- Add broader browser-level integration checks for intelligence pages and more concurrent database behavior tests.
- Add transaction category management and additional historical chart ranges.
- Implement the planned AI provider abstraction and specialized agents, followed by tested safe fallbacks.
- PayPal order/capture/webhooks are out of scope for this read-only reporting integration and should not be added as part of Milestone 4A.
- Add password reset, profile management, rate limiting, audit logging, OpenAPI docs, and production deployment hardening.
- Expand page-level React Testing Library coverage beyond the current budget create flow.
- No project-local commits: Git belongs to the parent profile worktree, and user instructions prohibit modifying or committing outside the FinSight directory.

## Milestone 2 — Financial management (2026-11-02)

### Implemented

- Flyway V2 creates normalized `financial_asset`, `financial_liability`, `financial_investment`, and `insurance_policy` tables with UUID primary keys, owner foreign keys/cascade, category checks, monetary precision/range checks, and owner indexes.
- Authenticated CRUD endpoints: `/api/assets`, `/api/liabilities`, `/api/investments`, `/api/insurance`. Reads filter on the authenticated owner; update and delete SQL include both record ID and authenticated owner ID. Foreign-owned resources return 404; `userId` is never accepted from clients.
- Financial inputs use Bean Validation and BigDecimal. Invalid financial requests return only invalid field names; rejected field values are not echoed by the application handler.
- `/api/financial-overview` computes total assets, investment current value, liabilities, net worth, monthly EMI, insurance coverage, current-month income/expenses/savings, and savings rate using BigDecimal and two-decimal HALF_UP rounding.
- Accounting convention: assets exclude investment holdings; investments are counted separately in net worth, so users should not duplicate investment holdings as assets. Values are manually entered; no market data or returns are fabricated.
- Frontend now has authenticated Assets, Liabilities, Investments, Insurance, and Financial Overview pages with form validation, loading/error/empty states, edit/delete actions, delete confirmation, and real API data. Demo mode does not access these records. The home dashboard's net-worth card uses the overview API.
- Added backend integration coverage for all four modules' create/list/update/delete, user isolation, bad amounts/categories, migration execution, and overview arithmetic. Added a frontend API-backed asset create flow test.

### Verification results

- Backend `mvnw -B test`: **10 passed, 0 failures/errors**. Flyway V1 and V2 ran under H2 2.2 PostgreSQL compatibility mode. This does not prove the migration against PostgreSQL 18.
- Frontend `npm test -- --reporter=dot`: **6 passed**.
- `npm run build` (includes TypeScript project typecheck): passed. Vite reports the existing >500 kB bundle warning (current JS chunk ~687 kB); chart test runs emit Recharts zero-size-container warnings in jsdom.
- Live PostgreSQL 18 migration and API verification was not run in this milestone; use `scripts/verify-postgres.ps1` with valid local admin/application credentials after reviewing that verifier against the new financial migration/tests.

### Remaining

- Account aggregation, AI assistant, investments market integrations, PayPal, account recovery, rate limiting, and production deployment hardening remain unfinished.
- No live market valuations, bank connections, or investment return projections are provided. Duplicate names are permitted because multiple assets/policies may legitimately share a display name; no provider policy number is collected for reliable deduplication.

## Milestone 3 — Financial intelligence engine (2026-11-02)

### Implemented

- Flyway V3 adds `financial_budget`, `financial_goal`, `recurring_transaction`, and `recurring_occurrence` with UUID ownership FKs, validation checks, query indexes, overlap/period uniqueness, and a unique recurrence/date idempotency constraint. Deleting a posted transaction preserves its occurrence key (`SET NULL`) so it cannot be posted again.
- Budget CRUD at `/api/budgets`; spending uses only actual expense transactions for the current calendar month, with remaining/percent/alert/exceeded status. Date ranges must cover whole calendar months; same-owner/category overlaps return 409.
- Goal CRUD at `/api/goals`; progress, remaining amount, required monthly savings, estimated completion date, and ON_TRACK/AT_RISK/COMPLETED status are deterministic. The projection assumes the average net savings of the previous three complete months continues without interest; no positive baseline yields no projected date.
- Recurring definitions at `/api/recurring-transactions`, upcoming schedule listing, pause/resume, and edit/delete. No scheduled transaction is posted automatically and no payment is initiated. The explicit record-occurrence action creates an actual transaction only for the due date, atomically advances the schedule, and prevents repeated occurrence creation using both a precheck and the unique DB key.
- `/api/forecast` returns three month estimates from the previous three complete actual months and active recurring schedules. Historical averages and monthly recurrence equivalents are combined as a maximum floor rather than summed to avoid adding the same scheduled expense twice. Closing cash starts from entered cash/bank asset values. Estimates include assumptions and confidence.
- `/api/financial-health` calculates an explainable 0–100 normalized indicator from five weighted components: savings rate (30), emergency fund coverage (25), EMI/income (20), budget adherence (15), and goal progress (10). Missing data reduces confidence and causes normalization over only available weights. It is explicitly not a credit score.
- `/api/intelligence/spending` reports actual expense categories, six-month actual income/expense/savings trends, comparison observations, budget alerts, and monthlyized recurring expense concentration. An elevated observation requires at least 50% and ₹1,000 above the user's own prior-three-complete-month category average; it is not a fraud claim.
- Added authenticated frontend pages for budgets, financial goals, recurring payments, forecast, financial health, and spending intelligence. Record forms use real APIs, loading/empty/error states, confirm deletion, and preserve demo separation. Recurrence pages label scheduled definitions separately from actual transactions.
- Added backend integration tests for CRUD, authorization, duplicate budgets/occurrences, validation, expense-only budgets, goal completion, forecast shape/confidence, spending categories, and endpoints; added forecast/health empty-data unit tests.

### Verification results

- Backend `mvnw -B test`: **14 passed, 0 failures/errors**. Flyway V1–V3 executed using H2 2.2 PostgreSQL compatibility mode. Integration coverage includes unauthenticated access denial for each intelligence API.
- Frontend Vitest: **6 passed** before the new intelligence navigation/pages were added; rerun after final frontend coverage update is pending.
- Frontend production build/typecheck: passed after the intelligence pages/routes; bundle is ~709 kB and retains the existing Vite >500 kB chunk warning.
- **No live PostgreSQL 18 migration or API verification was performed for V3.** H2 migration success is not PostgreSQL 18 proof.

### Known limitations

- Forecast inputs are limited to three prior complete months; missing months count as zero. The recurrence floor avoids obvious double-counting but cannot reliably distinguish manually entered transactions that correspond to recurrence definitions.
- Recurrence occurrences are recorded by explicit user confirmation and date; this is not a payment or transfer service. Monthly/yearly date advancement follows Java `LocalDate` month arithmetic.
- Financial health depends on data completeness and is an informational indicator, not credit scoring or personalized professional advice. Budgets/goals may lack sufficient history.
- PostgreSQL 18 verification, broader frontend page-level CRUD coverage, transaction category management, and production review remain outstanding. Automated payment execution, AI, PayPal, bank connections, and rate limits remain unfinished.

### Final milestone verification update

- Backend `backend\\mvnw.cmd -B test`: **14 passed, 0 failures/errors**. Flyway V1–V3 executed on H2 2.2 in PostgreSQL compatibility mode.
- Frontend `npm test -- --reporter=dot`: **7 passed** (including authenticated budget creation through the API client).
- `npm run build`: passed; this runs `tsc -b` before Vite. The production JavaScript bundle is 708.79 kB and Vite reports its existing >500 kB chunk warning.
- No actual PostgreSQL 18 server verification was performed for V3. H2 migration execution does not establish PostgreSQL 18 compatibility.
- No product acceptance claim is made for live PostgreSQL or browser-to-PostgreSQL integration in this milestone.

## PostgreSQL 18 integration verification follow-up (2026-11-02)

- Extended `scripts/verify-postgres.ps1` to require successful Flyway V1, V2, and V3 history entries and exercise PostgreSQL-backed transaction, asset, liability, investment, insurance, budget, goal, and recurring flows. It also checks recurring pause/resume, explicit occurrence idempotency, projections/intelligence endpoints, exact monetary totals, owner isolation, logout, and temporary-user cleanup.
- The verifier keeps per-user session cookies and post-login CSRF bootstrap behavior, suppresses credential-bearing database diagnostics, and removes only its GUID-addressed `verify-...@example.invalid` users. Financial records cascade from those temporary users. It does not modify existing users or reset credentials.
- The verifier reports the owning process for a busy API port and never terminates it. Use `-ApiBaseUrl 'http://127.0.0.1:8081'` to run the verification API on another port. Vite proxy target is configurable with `VITE_API_PROXY_TARGET` for the browser flow.
- PowerShell parse: passed. PostgreSQL URL helper test: passed. `pg_isready -h 127.0.0.1 -p 5432`: accepting connections. Installed client: `psql (PostgreSQL) 18.6`. Windows service `postgresql-x64-18` is running. Port 8080 had no listener when checked.
- Backend `mvnw -B test`: **14 passed, 0 failures/errors**. Flyway V1–V3 ran only on H2 2.2 PostgreSQL compatibility mode. Frontend Vitest: **7 passed**. `npm run build` (including typecheck): passed with the existing 708.79 kB chunk warning.
- **Real PostgreSQL authentication and migration/API verification remain unverified.** The verifier was started and reached its secure PostgreSQL administrator password prompt; no admin/application credentials were configured in the process, so it was cancelled before authentication and before any role/database or schema changes. `pg_isready` and the local psql client version do not prove the server version or authenticated database access.
- Remaining manual step: run `scripts/verify-postgres.ps1 -PostgresBin "$env:ProgramFiles\PostgreSQL\18\bin"` in an interactive PowerShell terminal and provide the valid administrator and application-role passwords at the hidden prompts (or configure the existing environment overrides). Share the redacted pass/fail summary for any follow-up; do not paste passwords, cookies, or tokens.

## PostgreSQL verifier transaction-count fix (2026-11-02)

- Root cause: `GET /api/transactions` returns a direct JSON array from `List<FinancialTransaction>`; it is not paginated and has no response wrapper. In Windows PowerShell, `@($response.Content | ConvertFrom-Json).Count` can count the pipeline result and report `1` for a multi-item array. A local two-row reproduction confirmed the old expression reports `1`; assigning `ConvertFrom-Json -InputObject` to a variable preserves an array with count `2`.
- Updated the verifier to validate the JSON array shape, assign and count it explicitly, emit only the returned count and UUID transaction IDs, and require Alice's two generated IDs to be present. Bob's array is checked to contain neither Alice ID and to be empty for the new verification account. Existing Bob update/delete 404 checks, CSRF, session cookie, authorization, and uniquely generated account cleanup remain enabled.
- Backend `mvnw -B test`: **14 passed, 0 failures/errors**. Frontend Vitest: **7 passed**. `npm run build` including TypeScript typecheck: passed with the existing bundle-size warning. PowerShell parser: passed. A local JSON-array reproduction passed (2 entries count as 2; empty array count is 0).
- This runtime has no configured `PG_ADMIN_PASSWORD` or `DATABASE_PASSWORD`; no credentials were entered. Real PostgreSQL verification was therefore not rerun after the verifier fix. The user's report from the previous run confirms PostgreSQL 18 connectivity, app credentials, V1–V3 migrations, Spring startup, authentication/CSRF, and both transaction creates/list HTTP status; the list assertion failure itself is a verifier counting defect and does not establish any remaining CRUD checks.
- Remaining: rerun `scripts/verify-postgres.ps1 -PostgresBin "$env:ProgramFiles\PostgreSQL\18\bin"` interactively with valid credentials. Do not claim complete PostgreSQL transaction or financial CRUD verification until its full run passes, including Bob isolation and cleanup.

## PostgreSQL asset-isolation assertion investigation (2026-11-02)

- The failed line was the Bob asset-list check using `@($response.Content | ConvertFrom-Json).Count -ne 0`. The asset endpoint returns a plain JSON array. In this Windows PowerShell runtime the expression reports count `1` for both `[]` and multi-item arrays, so it falsely reports that Bob received an asset even when the response is empty. This was reproduced locally; it is a verifier assertion defect, not evidence that the API returned Alice's record.
- Source trace: `GET /api/assets` resolves the Spring authenticated principal with `auth.getName()`, looks up that user UUID, then issues `SELECT * FROM financial_asset WHERE user_id=:user`. `POST /api/assets` sets `user_id` from that same authenticated principal; `AssetInput` contains no owner field. Unknown `userId` JSON properties are rejected by the configured Jackson strict-deserialization setting. Updates and deletes include both asset UUID and authenticated owner UUID in their predicates.
- The verifier now checks Alice and Bob `/api/auth/me` against their respective generated accounts; records only response HTTP status, count, and UUID asset IDs; explicitly checks whether Bob's response contains Alice's created ID; and counts all financial-list JSON arrays after assigning parsed JSON. It retains Bob's 404 update/delete assertions. Existing Spring integration coverage already verifies Bob gets an empty asset list and cannot update/delete Alice's asset; this run adds a test that a client-supplied `userId` is rejected.
- Ownership review found the same principal-derived owner filtering for liabilities, investments, and insurance in `FinancialController`; budgets use owner-scoped repository queries; goals and recurring records use `(id, authenticated user UUID)` repository lookups; analytics use owner-scoped transaction queries. No backend data-isolation defect was found in the inspected paths.
- Backend `mvnw -B test`: **14 passed, 0 failures/errors**, including the owner-injection regression assertion. Frontend Vitest: **7 passed**. TypeScript check and production build: passed, with the existing Vite chunk-size warning. PowerShell parse passed; the verifier array-count helper returned 0, 1, and 2 for empty, single-item, and two-item arrays.
- A new real PostgreSQL verification was not run: this process has neither PostgreSQL admin nor app-role credentials configured. The user-reported prior PostgreSQL migration and API checks are not a rerun of the fixed Bob asset assertion. Remaining step: rerun the verifier interactively with valid credentials and confirm its safe status/count/UUID diagnostics show Bob's array does not contain Alice's asset ID.

## PostgreSQL recurring occurrence assertion investigation (2026-11-02)

- The failing verifier line was `@($response.Content | ConvertFrom-Json | Where-Object id -eq $occurrenceTransactionId).Count -ne 1`. A local PowerShell reproduction with a matching transaction UUID returned zero for this pipeline predicate; parsing the response into an array variable and using `Where-Object { [string]$_.id -eq $occurrenceTransactionId }` returned one. This is a verifier-side filtering/counting defect. It does not prove the PostgreSQL row exists; live DB state must still be confirmed on the next run.
- Source trace: controller returns `OccurrenceResult.transactionId` from `RecurringTransactionService.recordOccurrence`. The service is class-level `@Transactional`; it resolves the recurrence by both recurrence ID and authenticated owner, saves `FinancialTransaction` (UUID created by its constructor), saves `RecurringOccurrence` with the same transaction UUID and owner, then advances the recurrence before the service transaction commits. Repositories share the Spring JPA transaction manager. The migration makes `recurring_occurrence.transaction_id` a foreign key to `financial_transaction.id` and makes `(recurring_transaction_id, occurrence_date)` unique. No backend persistence defect was found in the inspected flow.
- Verifier now validates the returned transaction UUID, checks Alice's authenticated transaction array using parsed-array assignment and an explicit scriptblock predicate, and logs only HTTP status, row counts, and UUIDs. It then queries PostgreSQL using the same parsed DB endpoint/application role to require one committed occurrence row linked to the returned transaction UUID and owned by the temporary Alice account. The duplicate occurrence request must still return HTTP 409.
- Added integration assertions that the 201 response contains the transaction UUID; the associated transaction and occurrence are persisted with matching IDs, Alice ownership, description/date/direction/amount; Bob cannot record Alice's occurrence or list its transaction; and a duplicate request creates no second transaction.
- Backend `mvnw -B test`: **14 passed, 0 failures/errors** (including the occurrence persistence/link/duplicate/owner checks). Frontend Vitest: **7 passed**. TypeScript and production build: passed with existing Vite bundle-size warning. PowerShell parse and exact predicate reproduction passed: legacy matching count 0, corrected matching count 1.
- No PostgreSQL credentials are configured in this process, so I did not rerun the live verifier or independently query PostgreSQL after the user's earlier HTTP 201. The application-versus-verifier diagnosis is strongly evidenced as a verifier predicate bug; actual PostgreSQL occurrence commit/link and duplicate behavior remain to be confirmed by rerunning the enhanced verifier interactively.

## PostgreSQL transaction deletion assertion investigation (2026-11-02)

- The assertion failed after the first Alice expense DELETE. The fixture has three actual transactions by then: the test income, the test expense, and the transaction created by the explicitly recorded recurring occurrence. Deleting the recurring definition does not delete that already-recorded financial transaction. After deleting the expense, the expected list therefore contains two IDs (income and occurrence), not one. The verifier's expected count was stale after adding occurrence coverage.
- The transaction endpoint returns HTTP 204 for a successful delete and an empty body; it uses `findByIdAndUserId(id, authenticatedOwner)` and `transactions.delete`. The subsequent list is owner-filtered. No backend change was indicated by the sequence or source inspection.
- Updated the verifier to check exact remaining UUIDs after each delete, assert each deleted UUID is absent, retain the expected occurrence transaction, and query PostgreSQL for zero rows matching the two explicitly deleted transaction IDs and Alice's generated account. This adds an independent PostgreSQL persistence check instead of weakening deletion verification.
- Backend tests: **14 passed**. Frontend tests: **7 passed**. TypeScript/production build: **passed** with the known Vite chunk-size warning. PowerShell parse and a three-ID deletion fixture passed (3 before, 2 after expense deletion, 1 after income deletion).
- PostgreSQL deletion was **not independently confirmed** in this run: admin and app database credential variables are unset, and no credentials were entered. The next interactive verifier run will perform the direct row-absence query; do not claim live PostgreSQL deletion passed before that succeeds.

## Verification results

- Repository inventory: read the frontend, backend, Flyway migration, Docker Compose, and current documentation before making integration changes.
- Earlier environment inspection: Windows 11, Node v24.18.1, npm 11.16.0, Java 25, and Git available; `mvn` and Docker CLI/Compose absent; no PostgreSQL service found at that time.
- Backend compile/test: from `backend`, set `$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.maven-cache'` then run `.\mvnw.cmd -B test`; passed on Java 25 with `--release 21`; **8 tests passed, 0 failed**.
- Flyway migration: executed successfully by the Spring integration tests on H2 2.2 in PostgreSQL compatibility mode. This is not a real PostgreSQL server verification.
- Backend Spring MVC integration: passed registration, form login, current user, CSRF endpoint/header, session logout invalidation, transaction CRUD, cash-flow aggregation, and cross-user list/update/delete isolation.
- Frontend tests: `npm test -- --reporter=dot` passed, **5 tests passed**.
- Frontend typecheck: `npx tsc -b --pretty false` passed.
- Frontend production build: `npm run build` passed; Vite reports a 675.63 kB minified JavaScript chunk warning.
- Dependency audit: `npm audit --audit-level=moderate` reports 0 vulnerabilities.
- Maven Wrapper run was verified with `MAVEN_USER_HOME` inside `backend/.maven-cache` and Maven dependencies inside `backend/.m2/repository`; no Maven installation or PATH change was made.
- PostgreSQL connectivity, Docker startup, real PostgreSQL migration, deployed cookie behavior, and full live browser/API session were not testable at that time because this machine had no Docker or PostgreSQL service.

## Known defects / limitations

- Demo dashboard figures remain illustrative and are isolated from registered account data.
- Assets/liabilities/net worth, investments, insurance, goals, AI, and PayPal remain unfinished.
- PostgreSQL migration execution and direct Docker startup still need verification on a host with Docker/PostgreSQL. H2 compatibility is useful coverage, not proof of PostgreSQL behavior.
- Rate limiting, password recovery, multi-month chart aggregation, and production deployment review remain outstanding before use with sensitive real-world records.
- The complete acceptance milestone remains blocked on a real PostgreSQL run; do not infer that H2 compatibility tests prove the PostgreSQL Docker service works.

## Previous PostgreSQL 16 preparation (historical; superseded 2026-11-02)

- The earlier preparation target was PostgreSQL 16 because Compose then pinned `postgres:16-alpine`. Its test runs used H2; no PostgreSQL 16 database validation was claimed.
- The current machine later reported a running `postgresql-x64-18` service. The old verifier rejected it before prompting for credentials; this prompted the compatibility upgrade below.
- Its previous PowerShell, frontend, and H2 test results are historical. Current target and latest test results are recorded in the PostgreSQL 18 section below.

## PostgreSQL 18 compatibility upgrade (2026-11-02)

### PostgreSQL verifier transaction 403 follow-up

- Root cause: the verifier correctly retained each `WebRequestSession` cookie jar, used that session's CSRF bootstrap token, sent the expected JSON body and `X-CSRF-TOKEN` header. Spring Security clears/rotates the CSRF token after successful form login. The verifier kept using the pre-login token for authenticated POST/PUT/DELETE requests, so transaction creation was rejected with HTTP 403. The frontend Axios client already handles 403 by fetching a fresh CSRF token and retrying once.
- The verifier now fetches a new CSRF token for Alice and Bob immediately after login, using each existing session cookie jar. CSRF and authorization protections remain enabled.
- Added an integration regression test: the old token is rejected after login, then the newly bootstrapped token creates an authenticated transaction successfully.
- Backend tests after the change: 9 passed, 0 failures/errors. This test suite uses H2; it does not substitute for live PostgreSQL verification.
- Verifier PowerShell parsing passed. The real PostgreSQL verifier was not rerun because this process has no `PG_ADMIN_PASSWORD` or `DATABASE_PASSWORD` credentials configured; no prompt was answered or credential guessed. The user's prior PostgreSQL run establishes connection/migration progress, but this turn does not establish live transaction CRUD success.
- Existing verifier cleanup removes only its uniquely generated `verify-...@example.invalid` user rows; the transaction rows cascade from those disposable users. Existing accounts/transactions are not targeted. Cleanup runs in `finally` if user creation has begun.

### IPv4 authentication discrepancy follow-up

- The Spring datasource default, `.env.example`, and verifier fallback now use `jdbc:postgresql://127.0.0.1:5432/finsight`. This avoids Windows `localhost` resolving to IPv6 `::1` when the administrator can authenticate over IPv4 only. Explicit `DATABASE_URL` overrides remain authoritative, including custom host, port, and IPv6 values.
- Added `scripts/PostgresUrl.ps1` to centralize URL validation and endpoint extraction. The verifier passes that same extracted host/port to `pg_isready` and every `psql` call, and passes the unchanged URL to Spring Boot.
- Password handling remains hidden prompt → in-memory SecureString conversion → process-scoped `PGPASSWORD`; passwords are not added to client arguments or output. No auth settings, credentials, service, or database were changed.
- Added `scripts/test-postgres-url.ps1`, a network-free check for the IPv4 default plus custom DNS host/port and IPv6 overrides.
- `scripts/test-postgres-url.ps1`: passed. It verifies IPv4 default and custom DNS/port plus IPv6/port URL parsing without contacting PostgreSQL.
- PowerShell parser accepted `PostgresUrl.ps1`, `test-postgres-url.ps1`, and `verify-postgres.ps1`.
- Backend `mvnw -B test`: passed, 8 tests, 0 failures/errors. Flyway ran against H2 in PostgreSQL compatibility mode; it is not real PostgreSQL verification.
- Frontend `npm test -- --reporter=dot`: passed, 5 tests; `npx tsc -b --pretty false`: passed; `npm run build`: passed with the existing 675.63 kB chunk-size warning. The chart tests continue to emit zero-size chart-container warnings.
- This follow-up did not run authenticated setup against PostgreSQL. The user-confirmed successful IPv4 `psql` login was not repeated here; the acceptance verifier/API lifecycle remains pending.

### Changes

- PostgreSQL 18 is now the explicit development target. Docker Compose uses `postgres:18-alpine`, a separate `finsight-data-pg18` named volume, and configurable host port `POSTGRES_PORT` (default 5432). The earlier volume is left untouched; no in-place major upgrade is attempted.
- Application database URL remains environment-configurable, with no H2 runtime fallback. H2 remains test-scoped, Flyway stays enabled, and Hibernate uses `ddl-auto: validate`.
- Resolved dependencies verified by Maven: Spring Boot 3.5.16, Flyway core 13.12.0, Flyway PostgreSQL module 13.12.0, and pgJDBC 42.7.14. Boot 3.4.1 failed to initialize with Flyway 13 because its auto-configuration calls a Flyway API removed in Flyway 11; Boot 3.5.16 is the compatibility fix. Flyway 13 is selected because current Redgate compatibility docs list PostgreSQL 18; Flyway 10.20.1 only documented PostgreSQL 17. pgJDBC 42.7.14 is maintained and follows 42.7.13 adding PostgreSQL 18 to CI. See the official references in README.
- Verifier requires exactly PostgreSQL major 18 from both the version-tagged Windows service preflight and authenticated `SHOW server_version_num`; it parses the host and port from `DATABASE_URL` and accepts `-PostgresBin` for client tools. It now classifies authentication and connectivity failures while suppressing raw `psql` output, because generated SQL can contain the app password.
- README documents PostgreSQL 18 startup, custom ports, the exact dependency rationale, and manual authentication recovery. The verifier does not reset role passwords or change `pg_hba.conf`.

### Verification results

- Backend `mvnw -B test`: passed, 8 tests, 0 failures/errors. Flyway V1 executed in the existing H2 PostgreSQL-compatibility integration environment; this is not a PostgreSQL 18 migration test.
- Backend `mvnw -B package`: passed and produced the executable Spring Boot JAR.
- Maven dependency tree confirmed exact resolved versions: `flyway-core:13.12.0`, `flyway-database-postgresql:13.12.0`, and `postgresql:42.7.14`.
- Frontend: 5 tests passed; TypeScript typecheck passed; production build passed with the existing 675.63 kB chunk-size warning.
- PowerShell parser accepted the updated verifier. PostgreSQL 18 service is running and `pg_isready -h localhost -p 5432` reports accepting connections. This is a readiness check, not authentication or a database query.
- The verifier has not been run through authenticated DB setup or migrations. No `PG_ADMIN_*` or `DATABASE_*` credential variables are set in this process, and direct `postgres` password authentication was previously reported failing. We did not guess/reset passwords, change `pg_hba.conf`, or modify the database.

### Remaining manual verification

- Confirm the correct PostgreSQL 18 service and port, then authenticate as `postgres` or another superuser using `psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -W` (the password prompt is hidden). If a different admin role is used, set `PG_ADMIN_USERNAME`.
- If the password is uncertain, first try pgAdmin or another known superuser and verify the target instance. An authenticated superuser can change the role password with psql's interactive password command; no password should be put in a command argument or SQL history. If all administrator access is lost, use PostgreSQL's documented recovery procedure, back up the service-specific `pg_hba.conf`, use a temporary localhost-only rule only if necessary, restore the original file immediately, and verify authenticated access. No such recovery action was performed.
- With valid admin/app credentials, run `scripts/verify-postgres.ps1`; it will create only missing development role/database objects and then verify actual server version, Flyway migration, CRUD, cash flow, user isolation, and logout. Run with `-KeepBackend` for browser verification.
- Real PostgreSQL 18 migration, API lifecycle, and browser-to-PostgreSQL flow remain unverified; the milestone is not declared complete.
