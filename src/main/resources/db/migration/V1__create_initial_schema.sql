-- =============================================================================
-- Migration: V1__create_initial_schema.sql
-- Description: Foundation schema establishing system verification table.
-- Purpose:
--   1. Validates PostgreSQL connectivity.
--   2. Verifies Flyway migration execution pipeline.
--   3. Validates JPA/Hibernate schema verification (ddl-auto=validate).
-- =============================================================================

CREATE TABLE system_status (
    id UUID PRIMARY KEY,
    status_name VARCHAR(100) NOT NULL,
    status_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_system_status_name UNIQUE (status_name)
);

-- Insert initial baseline verification record
INSERT INTO system_status (id, status_name, status_value, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'DATABASE_INITIALIZED',
    'SUCCESS',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
