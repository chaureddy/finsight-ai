# FinSight AI — Next Codex Session Handover

## Current state

Project root: `C:\Users\P.Saravanan\Documents\FinSight-AI` (the requested `finsight-ai` path is the same Windows directory; do not modify its parent Git worktree).

### Resume in a fresh Codex session

Open this existing directory. Read `README.md`, `SECURITY.md`, `DEVELOPMENT_STATUS.md`, and this handover before editing. Continue with PostgreSQL 18 verification of V4 and real Sandbox verification of the existing PayPal code. Extend what is here; do not scaffold or recreate the project. Keep all edits inside this directory, preserve V1–V3 and working features, use the user-provided environment for credentials without displaying them, and do not commit or push.

Milestone 4A has a first-party, read-only PayPal Sandbox Transaction Search implementation in the existing Spring Boot + React app. It extends the session-authenticated API, CSRF setup, transaction table, Flyway schema, Axios client, sidebar, and light-theme UI. It does not make payments, issue refunds, or move funds.

No real PayPal credentials were available or used. **Do not call the integration connected or imported against PayPal until a request to the real Sandbox succeeds and records are persisted.** PostgreSQL 18 V1–V3 live verification was part of the earlier baseline. New migration V4 is only verified through H2 PostgreSQL compatibility mode; it still needs the existing safe PostgreSQL verifier run against PostgreSQL 18.

## Implemented API and data flow

- `GET /api/integrations/paypal/status` — authenticated status/configuration only. It reports connected after a successful report response has been persisted.
- `GET /api/integrations/paypal/review` — authenticated, owner-scoped review records.
- `POST /api/integrations/paypal/import` — authenticated and CSRF-protected; accepts `startDate` and `endDate`, at most 31 days, within three years.
- Backend OAuth uses Client Credentials, caches the token in memory until 60 seconds before expiry, and refreshes once after a reporting 401. Requests use the fixed `https://api-m.sandbox.paypal.com` host, request timeouts, and bounded retries. Non-Sandbox hosts are rejected.
- API fetch completes before database persistence. Persistence locks the Sandbox connection row, binds the configured source to one FinSight owner, and writes transaction + import record within a transaction. Database uniqueness uses a balance-affecting report-detail fingerprint because PayPal transaction IDs can repeat. Imported rows keep the PayPal transaction ID as `external_reference`.
- Imported rows are only successful supported event types, INR, valid date and ID, nonzero explicitly signed amount, and no nonzero fee. Unsupported, pending/reversed/refund, fee-bearing, foreign-currency, malformed, or no-ID details are persisted as review records. Existing manual rows are not overwritten.

## Tests already run

- `backend`: `./mvnw.cmd test` — 20 passed, 0 failures/errors. Five PayPal API client tests cover OAuth, token refresh, pagination, upstream retry and timeout behavior. PayPal integration test covers authentication, CSRF, source owner binding, persisted values, review handling, duplicate repeat import, and cross-user transaction access.
- `backend`: focused `./mvnw.cmd '-Dtest=PayPalApiClientTest' test` — 5 passed after fixing a query matcher to expect encoded colons.
- Frontend: `npm test` — 9 passed.
- Frontend: `npm run build` — TypeScript check and Vite production build passed. Vite warns that the generated JS bundle is over 500 kB.
- H2 Flyway V1–V4 and Hibernate validation passed in the full test run.
- These results do not prove PostgreSQL V4 or PayPal Sandbox connectivity.

## Required next actions

1. Run the PayPal mock tests and full suite again after any change:

   ```powershell
   Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai\backend'
   .\mvnw.cmd test
   Set-Location -LiteralPath 'C:\Users\P.Saravanan\Documents\finsight-ai'
   npm test
   npm run build
   ```

2. Execute Flyway V4 on the actual PostgreSQL 18 development database using the existing `scripts/verify-postgres.ps1` flow and proper credentials. Preserve its hidden prompts, cleanup, CSRF, and owner-isolation checks. Do not reset passwords or alter existing data. Do not say the full verifier passed unless its exact assertions pass.

3. Ask the user to configure PayPal Developer Sandbox app credentials only in the backend environment. Never ask them to paste credentials into chat, frontend variables, source files, or documentation. Set:

   ```powershell
   $env:PAYPAL_CLIENT_ID = '<Sandbox app client ID>'
   $env:PAYPAL_CLIENT_SECRET = '<Sandbox app secret>'
   $env:PAYPAL_BASE_URL = 'https://api-m.sandbox.paypal.com'
   ```

   PayPal must grant `https://uri.paypal.com/services/reporting/search/read`. A valid token without Transaction Search authorization is not a connected account; a reporting 403 should remain a safe, explicit permission error.

4. Test in the Developer Sandbox using a date range with known test data. Confirm OAuth success, report authorization, pagination, actual API response shape, and persistence. Never fabricate provider payloads or report success based only on mocked tests.

5. **Confirm amount-direction semantics from real Sandbox responses before relying on imported items in cash-flow analytics.** The current mapper uses the sign of `transaction_amount.value` (positive = income, negative = expense). PayPal’s T-code reference describes CR/DR direction, but the Transaction Search `transaction_info` schema does not expose a separate debit/credit field. If the real response does not contractually encode account direction in the amount sign, revise mapping to review-only until direction can be determined safely. Add regression tests for verified in/out examples.

6. Confirm idempotency using repeated real report responses, including a transaction whose detail is returned more than once and repeated transaction IDs. The current fingerprint is based on the complete `transaction_info` JSON, which may include fields that can change over time. Check that an unchanged imported detail remains deduplicated on a later query; if not, define a stable per-detail identity from PayPal-supported immutable fields and test both duplicate and distinct rows with the same transaction ID.

7. Preserve the source-account limitation: Transaction Search is an app-account reporting API. PayPal requires Partner Network authorization for third-party accounts. The current app does not implement individual PayPal OAuth consent. It enforces one configured Sandbox source owner per FinSight database and currently has no user-facing disconnect/reassignment flow. Do not weaken that isolation to let another user claim the same source.

8. Review transaction deletion behavior: deleting an imported financial transaction cascades its import ledger record. A later import may recreate that transaction. Verify this UX is acceptable; do not change it without preserving dedupe and owner isolation.

## Known limits/blockers

- No real PayPal API call or import has succeeded in this workspace; credentials and scope remain user setup.
- PostgreSQL 18 V4 has not been applied to the actual server.
- Third-party personal-account linking is not supported without PayPal partner authorization and a separate user consent/token design.
- The current first-owner source claim has no self-service release. Do not suggest a database deletion as a routine fix.
- Only INR transactions without separate fees are auto-imported. Unsupported records remain review-only, and no currency conversion is performed.
- Reporting history is limited to three years and executed transactions may take up to three hours to appear. App imports are capped at 31 days per request and 1,000 report rows per search due bounded pagination.
- No webhook, payment, capture, refund, transfer, production PayPal host, or live credentials are included.
- Automated backend tests emit a Mockito dynamic-agent warning. Frontend production build emits the existing bundle-size warning.

## Official PayPal references

- [OAuth 2.0 authentication](https://developer.paypal.com/api/rest/authentication/)
- [Transaction Search OpenAPI specification](https://github.com/paypal/paypal-rest-api-specifications/blob/main/openapi/reporting_transactions_v1.json)
- [Transaction event-code reference](https://developer.paypal.com/reports/reference/t-codes/)
- [PayPal security guidelines](https://developer.paypal.com/security-guidelines)

## Stop condition

Do not begin Milestone 4B or another product module until PostgreSQL V4, real Sandbox authorization, amount-direction semantics, and repeated-import behavior have been verified and recorded. Do not commit or push automatically.
