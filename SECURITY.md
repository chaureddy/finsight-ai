# Security

## Implemented foundations

- Passwords are hashed using Spring Security BCrypt with work factor 12.
- Authentication is session based; session cookies are HttpOnly, `SameSite=Lax`, and can be marked Secure with `COOKIE_SECURE=true`.
- Spring Security CSRF defaults remain enabled; the API exposes a CSRF bootstrap endpoint. CORS allows one configured origin, credentialed requests, and the CSRF request header.
- Transactions carry an owner UUID and all list, create, update, and delete operations derive the owner from the authenticated principal. Record mutation queries include both record and owner identifiers; foreign-owned updates and deletes return 404.
- Assets, liabilities, investments, and insurance policies use separate PostgreSQL tables with `user_id` foreign keys. Their authenticated list queries are owner-filtered; update/delete statements bind both UUID and owner UUID. User-supplied ownership fields are not accepted. Financial record access uses parameterized `NamedParameterJdbcTemplate` statements; dynamic table identifiers are private and selected only from fixed internal constants.
- Financial record DTOs validate categories, text lengths, dates, rates, and nonnegative BigDecimal amounts. Database `CHECK` constraints provide a second validation boundary. Financial validation errors return field names only and do not echo submitted values.
- Financial overview calculations execute on the server with `BigDecimal`; values are rounded to two decimal places using HALF_UP. Investments are separate from non-investment assets to prevent planned double-counting.
- Transaction persistence uses JPA parameters and UUID identifiers. Monetary values use `BigDecimal`; database migrations enforce ownership and amount constraints.
- Request DTOs have Bean Validation constraints, and credentials are environment configured.
- Milestone 3 budget, goal, and recurring-transaction repositories scope reads and mutations to the authenticated principal's owner UUID. Analytics services likewise pass the principal owner ID to every financial query; clients cannot submit an owner ID.
- Budget, goal, and recurring requests validate required fields and monetary amounts. The financial validation advice returns generic field-level errors without echoing rejected amounts or descriptions.
- Recurring definitions do not initiate payments or create actual transactions automatically. An explicit authenticated action records a due occurrence. A database unique key on recurrence ID and due date prevents duplicate posting, including after a linked transaction is removed.
- Forecast, health, and spending intelligence are deterministic backend services. They operate on owner-scoped records and return estimates/observations with assumptions; they do not invoke an AI model or label observations as fraud.
- PayPal uses a backend-only OAuth client credentials flow for the fixed Sandbox host. Access tokens are held in memory with an expiry margin, not persisted or logged. API requests have connection/read timeouts and bounded retries; failures return generic messages without PayPal response bodies.
- PayPal import, status, and review APIs require the authenticated session; import POST also requires CSRF. Owner UUIDs are derived from the session principal. A database-enforced connection binds one Sandbox reporting source to one FinSight user. A unique per-source detail fingerprint prevents duplicate imports, including concurrent requests. Review queries are scoped through the connection owner.
- The integration requests balance-affecting transaction records only and does not request payer profile fields. Only completed, supported INR transactions with signed amounts and no separate fee enter cash-flow records. Other records stay in a review table and do not silently become income or expenses.

## Known gaps before real user data

- Account reset, email verification, profile editing, login throttling/rate limiting, lockout policy, audit trail, and security event monitoring are not implemented.
- There is no AI integration or tool execution. When introduced, the deterministic service must scope every query to the authenticated owner; model text must never authorize tools or supply balances. Prompt injection defenses, output validation, provider privacy review, and safe fallback behavior remain required.
- PayPal is a read-only Sandbox reporting integration, not an account-consent flow for each FinSight user. PayPal requires Partner Network access to query third-party accounts and the `reporting/search/read` permission. A successful report call is required before status is marked connected. Live PayPal, per-user PayPal OAuth consent, encrypted partner credentials, and production deployment review remain unsupported. The first FinSight user to successfully import claims the configured Sandbox source; there is no self-service disconnect/reassignment operation.
- PayPal event reporting may contain repeated transaction IDs for distinct report details. Import deduplication fingerprints balance-affecting detail and keeps the original PayPal ID as an external reference. Records without IDs are review-only. Imported transactions may be edited/deleted through existing transaction APIs; deleting one cascades its import record, so a later import can add it again.
- CSRF/session flows and financial CRUD/ownership/analytics APIs are covered by Spring MVC integration tests on H2; Flyway V1–V3 execute in H2 PostgreSQL compatibility mode. Production browser cookie behavior and V3 against actual PostgreSQL 18 still need verification.
- Error handling and production headers need a hardened review; rate limiting and API abuse controls are absent.
- The demo dashboard contains fixed illustrative figures, not private user data. Do not treat them as advice or real balances.
- Backend tests cover the PostgreSQL migration syntax in H2 compatibility mode. The newest financial migration has not yet been executed against the user's live PostgreSQL 18 instance. This document is not a security certification.
- Milestone 3 test run: 14 backend tests passed, including unauthenticated denial, owner isolation, validation, idempotency, and deterministic calculation cases; 7 frontend tests passed. Frontend production build/typecheck passed. These tests do not establish PostgreSQL 18 behavior.

## Threats and mitigations

| Threat | Current mitigation | Remaining work |
|---|---|---|
| Cross-user financial record access | Owner-scoped transaction, asset, liability, investment, and policy operations; integration tests verify foreign-owned IDs are inaccessible | Continue isolation tests for future entities |
| Cross-user intelligence data access | Owner-scoped budget, goal, recurring, forecast, health, and spending queries; foreign-owned module access returns not found | Verify V3 on PostgreSQL 18 and keep owner-isolation tests for additions |
| Duplicate scheduled transaction posting | Explicit occurrence action plus unique `(recurring_transaction_id, occurrence_date)` database constraint | Concurrent PostgreSQL integration race test remains outstanding |
| Misleading projections or scoring | Returned assumptions, limited history confidence, explicit estimate language, documented formulas; score is not a credit score | Validate usability with more edge-case and product review |
| Financial data disclosure through request errors | Financial validation handler returns invalid field names without rejected values | Verify safe errors for any new financial endpoints |
| Password disclosure | BCrypt password hashing; API omits hashes | Reset workflow, breach controls, rate limits |
| CSRF/session theft | CSRF enabled, CSRF token bootstrap, credentialed Axios, HttpOnly SameSite cookie, Spring logout invalidates session | HTTPS-only production, CSP and headers |
| Injection | JPA repositories, validation, no string-built SQL | Review all new query paths |
| Secret leakage | PayPal credentials are backend environment variables; OAuth tokens are memory-only and errors omit provider bodies; frontend has no PayPal secrets | Add a production secret manager, rotation procedure, and deployment review |
| False PayPal account linking or money movement | UI labels app-account Sandbox reporting only; app credentials are not represented as each user's PayPal consent; no payment/refund/transfer endpoints exist | PayPal partner enrollment and a separate per-user account-link design are needed for third-party access |

## PayPal milestone verification note (2026-11-02)

Earlier statements in this document about PostgreSQL V1–V3 verification are historical. The prior milestone did verify V1–V3 against PostgreSQL 18. This PayPal milestone adds V4, which currently passed only under H2 PostgreSQL compatibility mode; V4 has not yet been run against the live PostgreSQL 18 server. Automated PayPal tests use mocked provider responses and do not establish Sandbox credential, permission, or data availability.

## Sprint update (2026-11-03)

- The default database password is now required rather than falling back to a known sample value. Rotate the exposed `finsight` role password before the next authenticated application run; do not put it in source or chat.
- PayPal automatic imports are gated off by default until real incoming/outgoing Sandbox records verify the amount-sign mapping. Test fixtures and mocked provider responses are not sufficient evidence to enable the gate.
- The PostgreSQL service was reachable at `127.0.0.1:5432`, but the project could not authenticate or verify existing role/database permissions because no admin/app credentials were available. V4 against PostgreSQL 18 is still unverified.
- Login throttling, production response headers/CSP, monitoring, backup/restore validation, and an incident/credential rotation procedure remain deployment blockers.
- `npm audit --audit-level=moderate` reported zero vulnerabilities on 2026-11-03. This is a point-in-time frontend dependency check, not a complete security review.
