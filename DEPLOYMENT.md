# Deployment preparation

## Suggested architecture

Use a static hosting service for the Vite build, a small always-on container web service for Spring Boot, and managed PostgreSQL 18. Render is one compatible option: static sites can be free, while free web services sleep and are not suitable for dependable financial-data use. Render's free PostgreSQL databases expire after 30 days, so use a persistent managed database plan for any lasting data. Check current [Render pricing](https://render.com/pricing) before creating paid resources; no account or cloud resources have been created.

Configure the static host to build with `npm ci && npm run build` and publish `dist`. Add a same-origin rewrite for `/api/*` to the backend service's `/api/*` URL so the browser keeps session cookies on the frontend origin. The backend image is built from `Dockerfile.backend` at the repository root. Set its health-check path to `/api/health`.

## Backend secrets and settings

Set these in the hosting provider's secret/environment settings, never in Git or `VITE_*` variables:

- `DATABASE_URL`: provider's PostgreSQL JDBC URL (use its TLS-required URL/parameters).
- `DATABASE_USERNAME` and `DATABASE_PASSWORD`: dedicated app role credentials.
- `PORT`: supplied by the hosting platform.
- `COOKIE_SECURE=true`.
- `CORS_ORIGIN`: exact deployed frontend origin.
- `PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com`.
- `PAYPAL_CLIENT_ID` and `PAYPAL_CLIENT_SECRET`: leave unset until Sandbox use is approved and verified.
- `PAYPAL_SIGNED_AMOUNT_DIRECTION_VERIFIED=false` until incoming and outgoing provider records establish the sign mapping.

Flyway applies migrations at startup and Hibernate uses `ddl-auto=validate`. Before production, take a managed database backup, apply and validate migrations in staging, then deploy the backend and confirm `/api/health`, login, CSRF, and owner-scoped API flows over HTTPS. Do not use the demo figures as financial records.

## Database and rollback

Keep managed PostgreSQL backups/snapshots enabled and verify a restore procedure before storing real records. Keep database access private to the backend where the provider supports private networking; otherwise require TLS and restrict inbound access. Never reset the existing local database as part of deployment.

Rollback the application by redeploying the previous backend/frontend builds. Flyway migrations have no automatic down migration: keep changes backward-compatible across the rollback window and restore a database backup only when a reviewed recovery plan requires it. Preserve provider migration history.

## Not deployment-ready yet

- Rotate the exposed local PostgreSQL role password and authenticate to the existing database; run Flyway V4 and the API verifier against PostgreSQL 18.
- Complete production review for login throttling, security headers/CSP, backups/restore, monitoring, and incident response.
- Validate real Sandbox authorization and transaction sign semantics before enabling PayPal auto-import. No PayPal credentials or real Sandbox request were available during this sprint.
- Review current provider pricing and data-retention terms before creating services. No billing, domain purchase, or cloud resource has been initiated.
