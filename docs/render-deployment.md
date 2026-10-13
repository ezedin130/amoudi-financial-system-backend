# Render Docker Deployment Guide — Amoudi Financial Management System Backend

This guide outlines the complete procedure for building, verifying, and deploying the Amoudi Financial Management Spring Boot backend to **Render** as a **Docker Web Service** connected to the **Neon PostgreSQL** database.

---

## 1. Architecture & Prerequisites Overview

* **Runtime:** Java 21 (Eclipse Temurin JRE on Ubuntu Jammy).
* **Framework:** Spring Boot 3.4.3.
* **Packaging:** Executable Fat JAR via multi-stage Docker build.
* **Database:** Neon PostgreSQL (external, SSL-enabled via connection pooler).
* **Schema Evolution:** Managed exclusively by Flyway (`ddl-auto: validate`).
* **Container Security:** Non-root runtime user (`appuser:appgroup`, UID 10001).
* **Frontend:** Deployed to Vercel (Phase 4).

---

## 2. Local Docker Commands

### A. Build the Production Docker Image
Run from the `Backend/` directory:
```bash
docker build -t amoudi-financial-backend:latest .
```

### B. Run the Container Locally (Smoke Test with Test/Prod Environment)
```bash
docker run --rm -it \
  -p 8080:8080 \
  -e PORT=8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="jdbc:postgresql://<neon-host>:5432/<db-name>?sslmode=require" \
  -e DB_USERNAME="<db-user>" \
  -e DB_PASSWORD="<db-password>" \
  -e JWT_SECRET="<your-256-bit-hex-secret>" \
  -e CORS_ALLOWED_ORIGINS="http://localhost:3000" \
  -e STORAGE_PATH="/app/storage/uploads" \
  amoudi-financial-backend:latest
```

---

## 3. Render Web Service Configuration

### Step 1: Create a New Web Service on Render
1. Log in to your [Render Dashboard](https://dashboard.render.com/).
2. Click **New +** &rarr; **Web Service**.
3. Connect your Git repository containing the Amoudi Financial Management project.

### Step 2: Configure Service Parameters
* **Name:** `amoudi-financial-backend` (or your preferred service name).
* **Region:** `Ohio (US East)` (Select the same region as your Neon database: `us-east-2`).
* **Branch:** `main` (or your deployment branch).
* **Root Directory:** `Backend` (Crucial: Render will build from the `Backend` subfolder).
* **Environment:** `Docker`.
* **Dockerfile Path:** `Dockerfile` (Relative to `Backend` root).
* **Docker Build Context:** `.` (Relative to `Backend` root).
* **Plan:** `Starter` (or higher, recommended for persistent storage and 512MB+ RAM).

---

## 4. Required Render Environment Variables

Configure the following environment variables in the Render Dashboard under **Environment**:

| Environment Variable | Required | Example / Description |
| :--- | :---: | :--- |
| `SPRING_PROFILES_ACTIVE` | **Yes** | `prod` — Activates production profile with Flyway validation. |
| `PORT` | **Yes** | `10000` (Render provides this automatically; Spring Boot binds dynamically). |
| `DB_URL` | **Yes** | `jdbc:postgresql://<neon-host>:5432/<db-name>?sslmode=require` |
| `DB_USERNAME` | **Yes** | Neon database user (e.g. `neondb_owner`). |
| `DB_PASSWORD` | **Yes** | Neon database password (kept secret). |
| `JWT_SECRET` | **Yes** | Minimum 256-bit (32 bytes) hex-encoded secret key for HMAC-SHA256 signing. |
| `JWT_EXPIRATION_MS` | No | `86400000` (Default: 24 hours in milliseconds). |
| `CORS_ALLOWED_ORIGINS` | **Yes** | Comma-separated allowed frontend origins (e.g. `https://amoudi-financial.vercel.app,http://localhost:3000`). |
| `STORAGE_PATH` | No | `/app/storage/uploads` (Default container upload directory). |
| `INITIAL_USER_ENABLED`| No | `false` (Default: Keeps automatic admin seeding disabled; accounts are already provisioned). |

> [!WARNING]
> Never place plaintext credentials in source files, Dockerfiles, or committed repository configs. All secrets must be configured via Render's secure Environment Variables.

---

## 5. Health Check & Validation

### Health Check Endpoint
* **Path:** `/api/v1/health`
* **HTTP Method:** `GET`
* Configure Render's **Health Check Path** setting to `/api/v1/health`.
* **Expected Response (`200 OK`):**
  ```json
  {
    "status": "UP",
    "application": "Amoudi Financial Management",
    "database": "CONNECTED",
    "environment": "[prod]"
  }
  ```

### Authentication Test
After deployment, verify that an existing administrator can authenticate:
```bash
curl -X POST https://<your-render-url>.onrender.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "<your-admin-password>"}'
```
Expected response:
```json
{
  "success": true,
  "message": "Authentication successful",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": 1,
      "username": "admin",
      "fullName": "System Administrator",
      "role": "GENERAL_MANAGER",
      "status": "ACTIVE"
    }
  }
}
```

---

## 6. Understanding Startup & Flyway Logs

During container startup, inspect Render logs to verify normal operation:

1. **HikariCP Pool:**
   ```
   INFO ... com.zaxxer.hikari.HikariDataSource - AmoudiProdHikariCP - Start completed.
   ```
2. **Flyway Schema Validation:**
   ```
   INFO ... o.f.core.internal.command.DbValidate - Successfully validated 11 migrations
   INFO ... o.f.core.internal.command.DbMigrate - Current version of schema "public": 11
   INFO ... o.f.core.internal.command.DbMigrate - Schema "public" is up to date. No migration necessary.
   ```
3. **Hibernate Schema Validation:**
   ```
   INFO ... o.h.jpa.internal.util.LogHelper - HHH000204: Processing PersistenceUnitInfo
   INFO ... o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory
   ```
4. **Tomcat Port Binding:**
   ```
   INFO ... o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port <PORT> (http) with context path '/'
   ```

---

## 7. Receipt & File Upload Storage Considerations

* **Current Architecture:** Uploaded receipts are written to the local filesystem using [`LocalStorageService`](file:///c:/D%20One/Projects/Amoudi%20Financial%20System/Backend/src/main/java/com/amoudi/financial/service/storage/LocalStorageService.java) under `${STORAGE_PATH}`.
* **Ephemeral Container Limitation:** By default, standard Render containers have ephemeral storage. Files created inside the container will not persist across service redeployments or container restarts.
* **Recommended Production Solutions:**
  1. **Render Persistent Disk (Immediate Option):**
     * Add a persistent disk in the Render service settings (e.g. 1GB–10GB mounted at `/app/storage/uploads`).
     * Set `STORAGE_PATH=/app/storage/uploads`.
     * Receipts will persist across all container rebuilds and deploys.
  2. **Cloud Object Storage (Future Enterprise Option):**
     * Transition receipt storage to AWS S3, Cloudflare R2, or MinIO via an S3-compatible adapter.

---

## 8. Troubleshooting Common Issues

| Issue | Likely Cause | Resolution |
| :--- | :--- | :--- |
| **Port Binding Timeout** | Spring Boot did not bind to `$PORT` or bound only to `localhost`. | Ensure `server.port` is `${PORT:${SERVER_PORT:8080}}` and `server.address` is `0.0.0.0` (configured). |
| **Database Connection Refused** | Incorrect Neon hostname, port, or SSL parameter. | Verify `DB_URL` contains `?sslmode=require` and points to the pooler endpoint. |
| **Flyway Checksum Mismatch** | Migration files were modified after being applied to Neon. | Never edit V1–V11 migrations; create V12 if schema changes are required. |
| **OutOfMemoryError (OOM Killed)** | Container exceeded Render instance memory limit. | JVM flags `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` are active in Dockerfile. Upgrade Render instance if processing large Excel files. |
| **CORS Blocked on Frontend** | Origin mismatch with Vercel frontend URL. | Add the exact Vercel URL (e.g. `https://amoudi.vercel.app`) to `CORS_ALLOWED_ORIGINS` in Render. |
