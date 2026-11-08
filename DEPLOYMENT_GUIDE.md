# FinSight AI — Production Deployment Guide

This guide details the deployment options, environment configuration, database management, and operational procedures for **FinSight AI**.

---

## 1. System Architecture

FinSight AI employs a 3-tier production architecture designed for high availability, containerization, and data isolation:

```
                    ┌──────────────────────────────┐
                    │      Client Web Browser      │
                    └──────────────┬───────────────┘
                                   │ HTTPS / Port 80, 443
                                   ▼
                    ┌──────────────────────────────┐
                    │     Frontend (Nginx 1.27)    │
                    │  React 19 + TypeScript SPA   │
                    └──────────────┬───────────────┘
                                   │ Reverse Proxy: /api/*
                                   ▼
                    ┌──────────────────────────────┐
                    │  Backend (Spring Boot 3.5)   │
                    │      Java 21/25 Alpine       │
                    └──────────────┬───────────────┘
                                   │ JDBC Connection Pool (Hikari)
                                   ▼
                    ┌──────────────────────────────┐
                    │    Database (PostgreSQL 18)  │
                    │  Flyway Schema Migrations    │
                    └──────────────────────────────┘
```

---

## 2. Prerequisites

### Containerized Deployment
- **Docker Engine**: version 24.0 or newer
- **Docker Compose**: version 2.20 or newer

### Native Host Deployment
- **Java**: OpenJDK 21 or Java 25 LTS
- **Node.js**: version 20 LTS, 22 LTS, or 24
- **Database**: PostgreSQL 18.x running locally or remotely
- **Maven**: Bundled Maven Wrapper (`./mvnw` or `.\mvnw.cmd`)

---

## 3. Option A — Full Container Deployment (Recommended)

Run the entire platform including PostgreSQL 18, the Spring Boot API, and the React/Nginx frontend with a single command.

### Step 1: Prepare Environment File
Copy the example environment configuration:
```bash
cp .env.example .env
```
Edit `.env` to configure your credentials:
```ini
POSTGRES_PORT=5432
DATABASE_PASSWORD=your_secure_postgres_password
APP_ENCRYPTION_KEY=your-32-byte-secret-encryption-key-here
BACKEND_PORT=8081
FRONTEND_PORT=5173

# Optional: PayPal Sandbox Credentials
PAYPAL_CLIENT_ID=your_paypal_sandbox_client_id
PAYPAL_CLIENT_SECRET=your_paypal_sandbox_client_secret
PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
```

### Step 2: Build and Launch Containers
```bash
docker compose up -d --build
```

### Step 3: Verify Status & Health
```bash
docker compose ps
```
The health checks will report `healthy` for `postgres`, followed by `backend`, and finally `frontend`:
- **Web Application**: http://localhost:5173/
- **Backend API**: http://localhost:8081/api
- **Health Check**: http://localhost:8081/actuator/health

---

## 4. Option B — Hybrid Local Development Setup

In local development, you can use the active local PostgreSQL 18 Windows service while running the backend and frontend directly on the host.

### Step 1: Ensure Local PostgreSQL 18 is Running
Verify connection to local PostgreSQL 18.6:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-postgres.ps1
```
Or verify directly with `pg_isready`:
```bash
pg_isready -h 127.0.0.1 -p 5432 -U finsight -d finsight
```

### Step 2: Run Backend Service
```powershell
cd backend
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.maven-cache'
.\mvnw.cmd spring-boot:run
```
The backend starts on `http://localhost:8081`. Flyway migrations (`V1` through `V5`) execute automatically on startup.

### Step 3: Run Frontend Dev Server
In a separate terminal:
```bash
npm install
npm run dev
```
The frontend dev server starts on `http://localhost:5173`. Requests to `/api/*` are automatically proxied to `http://localhost:8081/api/*`.

---

## 5. Configuration Reference

| Variable | Description | Default | Production Requirement |
| :--- | :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | JDBC connection string | `jdbc:postgresql://127.0.0.1:5432/finsight` | **Required** |
| `SPRING_DATASOURCE_USERNAME` | Database username | `finsight` | **Required** |
| `SPRING_DATASOURCE_PASSWORD` | Database password | — | **Required** (Secret) |
| `APP_ENCRYPTION_KEY` | AES-256 master key for token encryption | `finsight-master-encryption-key-for-credentials-32b` | **Required** (Min 32 characters) |
| `COOKIE_SECURE` | Set `Secure` attribute on session cookies | `false` | Set `true` when behind HTTPS |
| `CORS_ORIGIN` | Allowed web origin for API | `http://localhost:5173` | Set to your public domain |
| `PAYPAL_CLIENT_ID` | PayPal developer client ID | — | Optional (Sandbox reporting) |
| `PAYPAL_CLIENT_SECRET` | PayPal developer secret | — | Optional (Sandbox reporting) |
| `PAYPAL_BASE_URL` | PayPal API base address | `https://api-m.sandbox.paypal.com` | Use Sandbox endpoint for testing |
| `AI_PROVIDER_API_KEY` | External LLM API key | — | Optional (Deterministic engine used if unset) |

---

## 6. Database Migrations & Integrity

FinSight AI utilizes sequential Flyway migrations. Migrations run automatically when the backend boots:

| Migration | File | Description |
| :--- | :--- | :--- |
| **V1** | `V1__identity_and_transactions.sql` | Users, sessions, core transactions schema |
| **V2** | `V2__financial_management.sql` | Assets, liabilities, budgets, and recurring bills |
| **V3** | `V3__financial_intelligence.sql` | Financial intelligence and health score tables |
| **V4** | `V4__paypal_sandbox_import.sql` | PayPal connection tracking and review items |
| **V5** | `V5__accounts_and_csv_import.sql` | CSV account credentials, review resolution, source checks |

> [!IMPORTANT]
> Never modify past migration files (`V1`–`V5`). When introducing new database entities or constraints, always add a new sequential migration (`V6__...sql`).

---

## 7. Backup and Disaster Recovery

### Creating a Database Backup
To take a full, consistent snapshot of the PostgreSQL database:
```bash
# Docker environment:
docker exec -t finsight-postgres pg_dump -U finsight -d finsight -F c -b -v -f /tmp/finsight_backup.dump
docker cp finsight-postgres:/tmp/finsight_backup.dump ./finsight_backup.dump

# Native host environment:
pg_dump -h 127.0.0.1 -p 5432 -U finsight -F c -b -v -f finsight_backup.dump finsight
```

### Restoring from Backup
```bash
# Docker environment:
docker cp ./finsight_backup.dump finsight-postgres:/tmp/finsight_backup.dump
docker exec -t finsight-postgres pg_restore -U finsight -d finsight -v --clean /tmp/finsight_backup.dump

# Native host environment:
pg_restore -h 127.0.0.1 -p 5432 -U finsight -d finsight -v --clean finsight_backup.dump
```

---

## 8. Production Verification & Testing

Before opening traffic to users, execute the full test verification suite:

```bash
# Backend test suite (33 tests)
cd backend
.\mvnw.cmd test
cd ..

# Frontend test suite (21 tests)
npm test -- --run

# Frontend production compilation
npm run build
```

Expected result: **54 passed tests, 0 failures, 0 errors**.

