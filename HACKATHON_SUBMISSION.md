# FinSight AI — PayPal AI Hackathon 2026 Submission Document

**Project Name**: FinSight AI  
**Repository**: [https://github.com/chaureddy/finsight-ai](https://github.com/chaureddy/finsight-ai)  
**Track**: AI Agents & Fintech Copilots / Consumer & Merchant Financial Intelligence  
**License**: MIT Open Source  

---

## 1. Problem Statement

Modern consumers, freelancers, and small merchants conduct transactions across diverse payment gateways, digital wallets, and bank accounts. In particular, PayPal is central to global digital commerce and creator payouts, but managing these flows remains fragmented:

1. **Information Silos**: Transaction records remain locked in PayPal portals or scattered across monthly PDF/CSV statements.
2. **AI Hallucinations in Financial Apps**: Generic LLM assistants frequently invent financial figures, miscalculate cash balances, or extrapolate trends from non-existent transactions.
3. **Security Vulnerabilities in Aggregators**: Many aggregators request plain-text credentials or store unencrypted financial keys at rest, leaving users susceptible to credential harvesting.
4. **Lack of Actionable Advice**: Users receive raw line-item lists rather than actionable insights on recurring subscriptions, processing fees, cash burn rates, and financial health scores.

---

## 2. Solution: FinSight AI

**FinSight AI** is a privacy-first, enterprise-grade autonomous financial intelligence platform engineered with a React 19 frontend, a Spring Boot 3.5.16 / Java 25 backend, and a PostgreSQL 18 transactional database.

FinSight AI connects directly to **PayPal Sandbox APIs** (OAuth 2.0, Transaction Search Reporting, and v2 Checkout Orders) to ingest, organize, and analyze authentic payment activities. It provides a conversational **Grounded AI Financial Assistant** that delivers mathematical insights with **0% hallucination**, deriving all answers directly from the user's verified PostgreSQL ledger.

---

## 3. Main Features

- **Consolidated Financial Overview**: Real-time net worth calculation, asset and liability tracking, liquid cash analysis, and balance sheet reconciliation.
- **PayPal Sandbox Hub**:
  - Live connection verification to `api-m.sandbox.paypal.com`.
  - Transaction Search API integration (`/v1/reporting/transactions`) with deduplicating import.
  - Full PayPal v2 Orders checkout integration (`POST /v2/checkout/orders` with `intent: CAPTURE`) and payment capture persistence.
  - Interactive Sandbox Checkout simulator for developer and judge testing.
- **Statement Reconciliation**: RFC 4180 compliant CSV statement importer with SHA-256 fingerprinting to prevent duplicate record insertion.
- **Deep Financial Intelligence**:
  - Algorithmic recurring payment and subscription detection.
  - Merchant-level spend aggregation and variance detection.
  - Cash flow forecasting for up to 3 months with statistical confidence scoring.
  - 5-factor weighted Financial Health Score (Savings Rate, Emergency Fund, Debt-to-Income, Budget Adherence, Goal Progress).
- **Accounts Hub (`/accounts`)**: Unified dashboard for managing connected PayPal Sandbox credentials, CSV bank accounts, and manual investment portfolios.
- **Security & Privacy Center (`/profile`, `/settings`)**: Session management, cryptographic key verification badge, profile customization, and one-click data export.

---

## 4. AI Integration: Grounded Copilot

FinSight AI's conversational assistant (`/assistant`) solves the fundamental flaw of LLMs in finance: **factual reliability**.

### Architecture
1. **Mathematical Grounding**: Every prompt (*"Analyze my PayPal spending"*, *"What are my largest expenses?"*, *"Identify recurring subscriptions"*) executes deterministic query algorithms against the user's isolated PostgreSQL records.
2. **Strict Multi-Tenant Isolation**: Tenant IDs are strictly bound to the authenticated server session (`JSESSIONID`). The AI engine has zero visibility across tenant boundaries.
3. **Interactive Suggestion Chips**: Responses include clickable action chips (*"Show Cloud Expenses"*, *"View Recurring Subscriptions"*, *"Review PayPal Fees"*) that guide users to deep analytical breakdowns.
4. **Zero-Hallucination Fallback**: When external LLM API keys are unset, the built-in deterministic intelligence engine provides continuous, mathematical analysis without interruption or privacy risk.

---

## 5. PayPal Integration Highlights

| PayPal API Component | Endpoint / Technology | Implementation Details |
|:---|:---|:---|
| **OAuth 2.0 Client Credentials** | `POST /v1/oauth2/token` | In-memory token caching with proactive refresh before expiry |
| **Transaction Search Reporting** | `GET /v1/reporting/transactions` | Automated ingestion with `balance_affecting_records_only=Y`, pagination, and deduplication |
| **Orders v2 API** | `POST /v2/checkout/orders` | Order creation with `intent: CAPTURE`, currency mapping, and reference tracking |
| **Orders Capture API** | `POST /v2/checkout/orders/{id}/capture` | Settlement processing and automated ledger ingestion as `PAYPAL` provider transactions |
| **Host Boundary Guard** | Fixed host assertion | Throws `IllegalArgumentException` if any URL outside `api-m.sandbox.paypal.com` is targeted |

---

## 6. Technology Stack

- **Frontend**: React 19, TypeScript, Vite 6, Lucide React, Recharts
- **Backend**: Java 25 LTS, Spring Boot 3.5.16, Spring Security 6, Spring Data JPA / Hibernate
- **Database**: PostgreSQL 18.6 with Flyway V1–V5 automated schema migrations
- **Cryptography**: AES-256-GCM authenticated cipher with 96-bit random IVs, BCrypt (12 rounds)
- **Containerization**: Multi-stage Dockerfiles (`Dockerfile.backend`, `Dockerfile.frontend`), Docker Compose, Nginx Alpine

---

## 7. Security and Privacy Posture

- **Zero Plain-Text Credentials**: Sensitive tokens are encrypted at rest with AES-256-GCM using authenticated Galois/Counter Mode.
- **Double-Submit CSRF Protection**: Per-session `X-CSRF-TOKEN` rotation with strict enforcement on all mutating endpoints.
- **HttpOnly Cookies**: Session tokens (`JSESSIONID`) are protected against client-side script access with `SameSite=Lax` and `HttpOnly` flags.
- **IDOR Protection**: All resource access queries enforce `WHERE owner_id = :authenticatedUserId` derived strictly from server-side security context.
- **Clean Repository**: `.gitignore` strictly protects `.env`, `.env.*`, `target/`, `node_modules/`, and scratch logs. Zero credentials committed to git history.

---

## 8. Innovation and Impact

1. **Bridging Gateway APIs to Autonomous AI**: Transforms raw payment processing webhooks and search APIs into real-time personal finance intelligence.
2. **Defensive Financial Engineering**: Eliminates AI hallucination in consumer finance by verifying every claim against cryptographic ledger records.
3. **Complete Open-Source SaaS Blueprint**: Demonstrates a complete production-grade fintech architecture (from Flyway migrations to multi-stage Docker builds and automated Vitest/JUnit suites).

---

## 9. Known Limitations

1. **Interactive Buyer Approval**: Executing final settlement via `POST /v2/checkout/orders/{id}/capture` requires an interactive PayPal Sandbox personal buyer to approve the transaction in the PayPal modal. In headless test runs, order creation is 100% verified, while capture settlement requires human buyer sign-in.
2. **Sandbox Environment Scope**: Exclusively configured for `api-m.sandbox.paypal.com` to prevent accidental production charges during evaluation.
3. **Multi-Currency Conversion**: Transactions are tracked in their original settlement currency (USD, INR, EUR); real-time foreign exchange conversion is not applied.

