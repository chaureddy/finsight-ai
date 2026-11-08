# FinSight AI — Comprehensive Implementation Plan

## Executive Summary
FinSight AI is a premium AI-powered personal financial intelligence platform built for the PayPal AI Hackathon 2026. This document establishes the blueprint for taking the existing React + TypeScript frontend, Spring Boot 3 backend, and PostgreSQL database to a secure, complete, production-ready release.

---

## 1. Baseline Architecture & Inventory

### 1.1 Existing Components
- **Frontend**: React 18, Vite, TypeScript, Lucide React, Recharts, Axios with CSRF handling.
- **Backend**: Java 25 (`--release 21`), Spring Boot 3.5.16, Spring Security, Spring Data JPA, Hibernate, Flyway.
- **Database**: PostgreSQL 18.6 schema with Flyway migrations `V1`–`V4`.
- **Existing Capabilities**:
  - User authentication (registration, login, current user, CSRF bootstrap & rotation).
  - Financial transactions CRUD with owner isolation.
  - Cash flow aggregation (monthly income, expense, savings rate).
  - Financial management: Assets, Liabilities, Investments, Insurance policies with overview calculations.
  - Financial intelligence engine: Budgets (with overlap detection), Financial Goals (with projected completion), Recurring Transactions (with schedule advancement and occurrence recording), Forecast (3-month cash projection), Financial Health score (0–100 weighted assessment), Spending Intelligence (trends, elevated categories, budget overruns).
  - PayPal Sandbox Integration: Backend OAuth client credentials, Transaction Search pagination, owner-isolated connection locking, transaction-detail fingerprinting, review queue for unverified or unsupported records.

### 1.2 Identified Gaps & Deficiencies
1. **Connected Accounts (`/accounts`)**: Currently a placeholder module page. Needs actual account management UI and backend support: PayPal status, connect/disconnect, and CSV import fallback.
2. **PayPal OAuth & Disconnection**: Disconnection endpoint missing; token encryption at rest required; review item resolution action missing.
3. **Transaction Import & Export Fallback**: CSV import endpoint missing; transaction CSV export button currently disabled with "coming soon".
4. **AI Financial Assistant (`/assistant`)**: Currently a placeholder module page. Needs a full interactive chat interface grounded in actual user data with intelligent deterministic fallback when external AI keys are unset.
5. **Automated Recurring Payment Detection**: Engine currently requires manual definition; needs automated pattern detector from transaction history.
6. **Settings Page (`/settings`)**: Currently a placeholder module page. Needs profile management, security settings, preferences, and data management.
7. **Production & Security Hardening**: Security audit documentation, token encryption at rest, rate limiting, and deployment readiness guide.

---

## 2. Implementation Milestones

### Milestone 1: Database & Backend Foundations (Phase 2 & 3)
- **Flyway Migration `V5__accounts_and_csv_import.sql`**:
  - Update `financial_transaction` constraint to permit `CSV_IMPORT`, `MANUAL`, and `PAYPAL` sources.
  - Create `user_account_connection` table for tracking connected providers (PayPal, manual banks, uploaded statements) and encrypted tokens at rest.
  - Add review resolution support for PayPal and imported records.
- **Security & Encryption Service**:
  - `EncryptionService`: AES-256-GCM encryption and decryption with key derivation for sensitive credentials and OAuth tokens.
- **Connected Accounts & PayPal Enhancements**:
  - PayPal disconnect endpoint (`POST /api/integrations/paypal/disconnect`).
  - PayPal review item action endpoint (`POST /api/integrations/paypal/review/{id}/action` to approve into transaction or dismiss).
  - Account connection management controller (`/api/accounts`).
- **CSV Transaction Import & Export**:
  - `CsvTransactionService`: Robust parsing for standard CSV formats (date, description, amount, direction/type, category, currency), deduplication by content fingerprint.
  - `POST /api/transactions/import-csv`: Multi-part or text upload with duplicate skipping.
  - `GET /api/transactions/export`: RFC 4180-compliant CSV download of owner transactions.

### Milestone 2: Financial Intelligence & AI Assistant (Phase 4)
- **Advanced Financial Analysis Service**:
  - Recurring transaction pattern detection (scans history for recurring amounts, intervals, and merchants).
  - Refund and fee analysis (identifies reversals, refunds, and provider fees).
  - Personalized savings recommendations (rule-based intelligence engine analyzing high-expense categories, subscription creep, emergency fund gaps, and high-interest debt).
- **AI Financial Assistant Service (`/api/assistant`)**:
  - `POST /api/assistant/chat`: Processes user queries grounded strictly in authenticated user data.
  - Deterministic fallback engine: Handles queries regarding net worth, monthly spending, top categories, budget overruns, recurring commitments, health score, and personalized tips without hallucination.
  - Optional AI provider integration: If `AI_PROVIDER_API_KEY` is present, formats a privacy-safe sanitized system prompt grounded in actual numbers.

### Milestone 3: Frontend Completion (Phase 5)
- **Connected Accounts Page (`/accounts`)**:
  - Real account status cards (PayPal Sandbox, CSV Statement Import, Cash & Bank Accounts).
  - PayPal connect/disconnect actions.
  - Interactive CSV file upload with preview, duplicate reporting, and column mapping.
- **AI Financial Assistant Page (`/assistant`)**:
  - Fintech chat interface with message history, typing states, and suggested question pills.
  - Clear data grounding badges ("Grounded in your actual transactions").
  - Quick action buttons to view relevant pages (e.g. "View Budgets", "Check Recurring").
- **Transactions Page Upgrades**:
  - Functional CSV Export button.
  - Import CSV modal with instant feedback.
  - Filter by source (All, Manual, PayPal, CSV).
- **Settings Page (`/settings`)**:
  - User profile details and display name updater.
  - Currency preferences.
  - Security overview and session management.
- **Dashboard Refinements**:
  - Direct insights cards linked to AI recommendations and health score.

### Milestone 4: Testing & Verification (Phase 6)
- Backend tests:
  - Encryption and token handling tests.
  - CSV parsing, deduplication, and import tests.
  - CSV export tests.
  - PayPal disconnect and review resolution tests.
  - AI Assistant query and recommendation tests.
  - User isolation verification for all new endpoints.
- Frontend tests:
  - Account connection component tests.
  - AI Assistant chat interaction tests.
  - CSV import and export tests.
  - Settings page tests.

### Milestone 5: Security Audit & Production Deployment (Phases 7, 8, 9)
- Comprehensive security review documented in `SECURITY_AUDIT.md`.
- Production deployment configuration and documentation in `DEPLOYMENT_GUIDE.md`.
- Hackathon project presentation package in `README.md`.
- Update `DEVELOPMENT_STATUS.md`.

---

## 3. Strict Operating Guidelines
- Preserve all existing functionality, tests, and endpoints.
- Maintain owner data isolation on 100% of queries.
- Zero credential or secret leakage.
- Deterministic fallbacks ensure full functionality even with zero external third-party API keys configured.

