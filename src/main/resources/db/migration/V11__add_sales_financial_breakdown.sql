-- =============================================================================
-- Migration: V11__add_sales_financial_breakdown.sql
-- Description: Adds Galileo/BSP sales breakdown columns (base_fare, tax, total_sale,
--              service_charge, grand_total) to sales_transactions table while
--              preserving the existing authoritative amount column and all historical data.
-- =============================================================================

ALTER TABLE sales_transactions ADD COLUMN base_fare NUMERIC(18,2);
ALTER TABLE sales_transactions ADD COLUMN tax NUMERIC(18,2);
ALTER TABLE sales_transactions ADD COLUMN total_sale NUMERIC(18,2);
ALTER TABLE sales_transactions ADD COLUMN service_charge NUMERIC(18,2);
ALTER TABLE sales_transactions ADD COLUMN grand_total NUMERIC(18,2);

-- Backfill grand_total from the existing authoritative amount column for all historical records:
UPDATE sales_transactions
SET grand_total = amount
WHERE grand_total IS NULL;

-- Enforce NOT NULL and positive check on grand_total (authoritative accounting amount):
ALTER TABLE sales_transactions ALTER COLUMN grand_total SET NOT NULL;
ALTER TABLE sales_transactions ADD CONSTRAINT chk_sales_transactions_grand_total CHECK (grand_total > 0);

-- Index on grand_total for performant accounting balance queries and aggregations:
CREATE INDEX idx_sales_transactions_grand_total ON sales_transactions (grand_total);
