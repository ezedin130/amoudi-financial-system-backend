-- =============================================================================
-- Migration: V9__add_financial_indexes_and_constraints.sql
-- Description: Adds composite indexes optimized for financial balance calculations,
--              statement generation, and foreign key query performance.
-- =============================================================================

-- Composite index to accelerate Non-IATA sales transaction aggregation and date-range statements:
CREATE INDEX idx_sales_transactions_non_iata_date
ON sales_transactions (non_iata_id, sale_date);

-- Composite index to accelerate Non-IATA posted payment aggregation and ledger balance calculation:
CREATE INDEX idx_payments_non_iata_status_date
ON payments (non_iata_id, status, payment_date);

-- Index on name_mappings.created_by for complete foreign key indexing:
CREATE INDEX idx_name_mappings_created_by
ON name_mappings (created_by);
