# Amoudi Financial Management - Enterprise Backend

## 1. Project Purpose & Core Financial Principle

**Amoudi Financial Management** is a financial operations, ticketing reconciliation, and sub-agent accounting platform. It is engineered to ingest daily sales transactions and ticketing records from global distribution systems (Galileo) and settlement systems (BSP), manage Non-IATA sub-agent accounts, track payment settlements, record receipt allocations, and calculate authoritative financial balances.

### Authoritative Financial Invariant
The system adheres to an immutable, derived ledger rule:
$$\text{Outstanding Balance} = \sum(\text{Imported Sales}) - \sum(\text{Posted Payments})$$

- **No Stored Balance Column:** Outstanding balances are never persisted as mutable database fields. They are always derived in real time from transactional records.
- **Pure Monetary Precision:** All monetary amounts use `NUMERIC(18,2)` in PostgreSQL and `java.math.BigDecimal` with `RoundingMode.HALF_UP` in Java. `float` and `double` are strictly prohibited.
- **Payment Status Filter:** Only payments with status `POSTED` are deducted. Payments with status `CANCELLED` are completely excluded from balance deductions.
- **Account-Level Payments:** In V1, payments are applied at the Non-IATA account level (no ticket-level allocation).

---

## 2. Technology Stack

- **Runtime & Language:** Java 21 (LTS)
- **Framework:** Spring Boot 3.4.3
- **Build Tool:** Apache Maven 3.9+
- **Security:** Spring Security 6 (Stateless JWT, HMAC-SHA256 256-bit key, Method Security `@PreAuthorize`)
- **Roles:** Exactly two application roles: `FINANCE` and `GENERAL_MANAGER`
- **User Statuses:** `ACTIVE`, `INACTIVE`, `LOCKED`
- **Database:** PostgreSQL 16+ (PostgreSQL 18.6 verified)
- **Schema Management:** Flyway (Migrations V1 through V10)
- **ORM / Persistence:** Spring Data JPA / Hibernate 6 (`ddl-auto: validate` enforced in all profiles)
- **Excel & Document Processing:** Apache POI 5.2.5 (XSSF Streaming / Sheet2 parsing) & OpenPDF 2.0.3 (Financial reporting)
- **Testing:** JUnit 5, Mockito, AssertJ, Spring MockMvc (224 automated tests passing)

---

## 3. Local Development Guide

### 3.1 Start PostgreSQL
Ensure PostgreSQL is running locally on port 5432:
```powershell
# Using local PostgreSQL service or Docker:
# Database: amoudi_financial
# Port: 5432
# User: postgres
```

### 3.2 Configure Development Environment
Development configuration is located in `src/main/resources/application.yml` and `src/main/resources/application-dev.yml`.
```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "amoudi_financial"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your_postgres_password"
$env:SPRING_PROFILES_ACTIVE = "dev"
```

### 3.3 Run Tests
Execute the complete test suite (224 tests):
```powershell
mvn clean test
```

### 3.4 Start the Backend Server
```powershell
mvn spring-boot:run
```
The application will start on `http://localhost:8080`.
Initial administrative user is bootstrapped in development mode:
- **Username:** `admin`
- **Password:** `Admin@12345`
- **Role:** `GENERAL_MANAGER`

---

## 4. Production Operations Guide

### 4.1 Production Profile (`application-prod.yml`)
In production, run with `-Dspring.profiles.active=prod` or `$env:SPRING_PROFILES_ACTIVE = "prod"`.
The production profile contains **zero hardcoded secrets** and requires environment variables:

| Environment Variable | Required | Description | Example / Default |
| :--- | :--- | :--- | :--- |
| `DB_URL` | **YES** | JDBC connection string | `jdbc:postgresql://db.prod:5432/amoudi_financial` |
| `DB_USERNAME` | **YES** | Database user with DDL/DML permissions | `amoudi_app` |
| `DB_PASSWORD` | **YES** | Database user password | `ComplexSecretPassword!` |
| `JWT_SECRET` | **YES** | 256-bit+ HMAC-SHA256 secret (Base64 or hex) | `8f4b2c1e9a7d3f6e5b4a...` |
| `JWT_EXPIRATION_MS` | NO | JWT token validity in milliseconds | `86400000` (24 hours) |
| `STORAGE_PATH` | NO | Root filesystem directory for uploaded files | `/var/data/amoudi/storage/uploads` |
| `CORS_ALLOWED_ORIGINS` | **YES** | Comma-separated allowed frontend origins | `https://finance.amoudi.com` |
| `SERVER_PORT` | NO | HTTP listener port | `8080` |
| `INITIAL_USER_ENABLED` | NO | Bootstrapping toggle (defaults to `false` in prod) | `false` |
| `INITIAL_USER_PASSWORD`| CONDITIONAL | Required only if `INITIAL_USER_ENABLED=true` | Must not be `Admin@12345` |

### 4.2 Initial Administrative User Provisioning in Production
Production strictly forbids the default insecure development password `Admin@12345`.
If bootstrapping the first administrator via environment variables:
```bash
export INITIAL_USER_ENABLED="true"
export INITIAL_USER_USERNAME="prod_admin"
export INITIAL_USER_PASSWORD="VaultGeneratedComplexPassword123!"
export INITIAL_USER_FULL_NAME="Production Administrator"
export INITIAL_USER_ROLE="GENERAL_MANAGER"
```
Once bootstrapped, set `INITIAL_USER_ENABLED="false"`.

### 4.3 Database Migration Behavior
- Flyway migrations `V1` through `V10` execute automatically upon application startup.
- Hibernate runs with `ddl-auto: validate`. If the database schema differs in any way from the JPA entity model, the application will **fail fast** at startup rather than corrupting data.
- **Clean Disabled:** Production profile strictly disables Flyway clean (`clean-disabled: true`).

### 4.4 File Storage Setup & Reliability
- File uploads are organized by category and date under `${STORAGE_PATH}`:
  - `${STORAGE_PATH}/imports/YYYYMMDD/<uuid>_<filename>.xlsx`
  - `${STORAGE_PATH}/receipts/YYYYMMDD/<uuid>_<filename>.pdf`
- **Path Traversal Guards:** All storage operations normalize paths and enforce `targetPath.startsWith(rootLocation)`. Any attempt to traverse directories (`../../`, `\..\`) triggers `SecurityException` and returns HTTP 400.
- **Orphan Cleanup:** When a payment's receipt is replaced, the previous receipt file is deleted from disk to prevent storage leaks.
- **Size Limits:** Excel imports are bounded to 25 MB; receipts are bounded to 10 MB.

### 4.5 Production Backup Strategy
A production backup must protect both PostgreSQL metadata and physical filesystem documents:

1. **PostgreSQL Database Backup:**
   ```bash
   pg_dump -U postgres -Fc -d amoudi_financial -f /backups/amoudi_financial_$(date +%Y%m%d_%H%M%S).dump
   ```
   Contains: `users`, `non_iatas`, `import_batches`, `sales_transactions`, `payments`, `name_mappings`, `audit_logs`, and Flyway schema history.

2. **File Storage Backup:**
   ```bash
   tar -czvf /backups/amoudi_storage_$(date +%Y%m%d_%H%M%S).tar.gz /var/data/amoudi/storage/uploads/
   ```
   Contains all raw Galileo/BSP Excel source files and payment receipt bank slips.

### 4.6 Disaster Recovery Procedure
1. Restore PostgreSQL database: `pg_restore -U postgres -d amoudi_financial -c /backups/latest.dump`
2. Restore file storage: `tar -xzvf /backups/latest.tar.gz -C /`
3. Verify Flyway migrations: Run `flyway info` or check `flyway_schema_history`.
4. Start backend service: Verify logs show successful database connection and schema validation.
5. Verify health endpoint: `GET /api/v1/health` returns `status: UP` and `database: CONNECTED`.
6. Run financial reconciliation check: Verify that `Financial Summary == Statement Closing == Aging Total Outstanding`.

---

## 5. Security Checklist

Before deploying to production, verify each of the following controls:

- [x] **Production JWT Secret:** Set via `JWT_SECRET` environment variable (minimum 256 bits).
- [x] **Production Database Password:** Set via `DB_PASSWORD` (no default postgres password).
- [x] **No Default Admin Password:** `INITIAL_USER_PASSWORD` changed or `INITIAL_USER_ENABLED=false`.
- [x] **Strict CORS Policy:** `CORS_ALLOWED_ORIGINS` restricted to verified corporate frontend domains (no `*`).
- [x] **HTTP Security Headers:**
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Content-Security-Policy: default-src 'self'`
  - `Referrer-Policy: no-referrer`
- [x] **Role Authorization Enforced:**
  - `FINANCE`: Imports sales, creates name mappings, cancels payments, views all financial reports.
  - `GENERAL_MANAGER`: Creates payments, uploads receipts, views all financial reports.
- [x] **Path Traversal Protection:** Directory traversal characters (`..`, `/`, `\`) rejected in all file storage services.
- [x] **Receipt Replacement Cleanup:** Superseded receipt files deleted immediately upon replacement.
- [x] **Password Protection:** BCrypt adaptive hashing with work factor 10; passwords and hashes never returned in API responses or logs.
- [x] **Append-Only Audit Logs:** `audit_logs` table has no update or delete endpoints; all logins, entity mutations, payments, imports, uploads, and downloads are logged.
- [x] **Error Response Sanitization:** Global error responses do not leak stack traces, SQL syntax, or server filesystem paths.
- [x] **Request Tracing:** `X-Correlation-ID` and `X-Request-ID` propagated across all HTTP requests, responses, and MDC logs.
- [x] **HTTPS Deployment:** Application must run behind a TLS reverse proxy (Nginx / ALB / Cloudflare).

---

## 6. REST API Endpoint Index

All endpoints are prefixed with `/api/v1/`.

### 6.1 Authentication (`/auth`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Public | Authenticates username/password; returns JWT access token and user profile. |
| `GET` | `/api/v1/auth/me` | Authenticated | Returns profile of currently authenticated user. |

### 6.2 Health & Diagnostics (`/health`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/health` | Public | Returns system readiness, PostgreSQL database connectivity, and active profile. |

### 6.3 Non-IATA Directory (`/non-iatas`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/non-iatas` | `FINANCE`, `GENERAL_MANAGER` | Creates a new Non-IATA sub-agent account. |
| `GET` | `/api/v1/non-iatas` | `FINANCE`, `GENERAL_MANAGER` | Lists paginated Non-IATAs with search and status filtering. |
| `GET` | `/api/v1/non-iatas/{id}` | `FINANCE`, `GENERAL_MANAGER` | Retrieves Non-IATA account details. |
| `PUT` | `/api/v1/non-iatas/{id}` | `FINANCE`, `GENERAL_MANAGER` | Updates Non-IATA profile details. |
| `PATCH`| `/api/v1/non-iatas/{id}/status`| `FINANCE`, `GENERAL_MANAGER` | Updates account status (`ACTIVE`, `INACTIVE`). |

### 6.4 Sales Transactions & Summaries (`/sales`, `/financial-summaries`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/sales` | `FINANCE`, `GENERAL_MANAGER` | Lists paginated sales transactions with dynamic filtering and sorting. |
| `GET` | `/api/v1/sales/{id}` | `FINANCE`, `GENERAL_MANAGER` | Retrieves sales transaction details. |
| `GET` | `/api/v1/financial-summaries`| `FINANCE`, `GENERAL_MANAGER` | Lists derived financial summaries for all Non-IATAs (`totalSales`, `totalPayments`, `outstandingBalance`). |
| `GET` | `/api/v1/financial-summaries/{id}`| `FINANCE`, `GENERAL_MANAGER` | Retrieves derived financial summary for a specific Non-IATA. |

### 6.5 Galileo/BSP Excel Import & Name Mapping (`/imports`, `/name-mappings`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/imports` | `FINANCE` only | Uploads and ingests Galileo/BSP daily sales report Excel file (`Sheet2`, `G.TOT`). |
| `GET` | `/api/v1/imports` | `FINANCE`, `GENERAL_MANAGER` | Lists paginated import batch history. |
| `GET` | `/api/v1/imports/{id}` | `FINANCE`, `GENERAL_MANAGER` | Retrieves import batch statistics. |
| `GET` | `/api/v1/imports/{id}/errors`| `FINANCE`, `GENERAL_MANAGER` | Lists row-level parsing/mapping rejections. |
| `POST` | `/api/v1/name-mappings` | `FINANCE` only | Maps raw Excel source agency name to Non-IATA account. |
| `GET` | `/api/v1/name-mappings` | `FINANCE`, `GENERAL_MANAGER` | Lists all registered name mappings. |

### 6.6 Payments & Receipts (`/payments`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/payments` | `GENERAL_MANAGER` only | Records a new payment transaction with status `POSTED`. |
| `POST` | `/api/v1/payments/{id}/receipt` | `GENERAL_MANAGER` only | Attaches/replaces a receipt file (PDF, JPG, PNG); deletes superseded file. |
| `GET` | `/api/v1/payments/{id}/receipt` | `FINANCE`, `GENERAL_MANAGER` | Downloads attached receipt document. |
| `GET` | `/api/v1/payments` | `FINANCE`, `GENERAL_MANAGER` | Lists paginated payments with dynamic date, receipt, and status filters. |
| `GET` | `/api/v1/payments/{id}` | `FINANCE`, `GENERAL_MANAGER` | Retrieves single payment record by ID. |
| `POST` | `/api/v1/payments/{id}/cancel` | `FINANCE` only | Cancels `POSTED` payment transaction; locked with pessimistic concurrency guard. |

### 6.7 Statements & Aging Reports (`/non-iatas/{id}/statement`, `/aging`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/non-iatas/{id}/statement` | `FINANCE`, `GENERAL_MANAGER` | Generates chronological account statement with running balance. |
| `GET` | `/api/v1/non-iatas/{id}/statement/export`| `FINANCE`, `GENERAL_MANAGER` | Exports account statement as Excel (`.xlsx`) or PDF (`.pdf`). |
| `GET` | `/api/v1/aging` | `FINANCE`, `GENERAL_MANAGER` | Generates FIFO aging breakdown across 4 aging buckets (`0-30`, `31-60`, `61-90`, `90+` days). |
| `GET` | `/api/v1/aging/export` | `FINANCE`, `GENERAL_MANAGER` | Exports company aging report as Excel (`.xlsx`) or PDF (`.pdf`). |
