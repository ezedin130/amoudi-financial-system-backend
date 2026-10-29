-- =============================================================================
-- Migration: V13__add_galileo_transaction_types_and_refund_support.sql
-- Description: Adds support for SALE, REFUND, and EXCHANGE transaction types,
--              links refunds and exchanges to original sales transactions,
--              updates amount constraints to allow negative amounts for REFUNDs,
--              and adds transaction type metrics to import batches.
-- =============================================================================

-- 1. Add transaction_type column to sales_transactions with default 'SALE'
ALTER TABLE sales_transactions 
    ADD COLUMN transaction_type VARCHAR(20) NOT NULL DEFAULT 'SALE';

-- 2. Add original_transaction_id foreign key for linking refunds/exchanges to original ticket
ALTER TABLE sales_transactions 
    ADD COLUMN original_transaction_id BIGINT NULL;

ALTER TABLE sales_transactions 
    ADD CONSTRAINT fk_sales_transactions_original 
    FOREIGN KEY (original_transaction_id) REFERENCES sales_transactions (id) 
    ON DELETE SET NULL;

CREATE INDEX idx_sales_transactions_type ON sales_transactions (transaction_type);
CREATE INDEX idx_sales_transactions_orig_id ON sales_transactions (original_transaction_id);

-- 3. Update check constraints on amount and grand_total to allow negative amounts for REFUNDs
ALTER TABLE sales_transactions DROP CONSTRAINT IF EXISTS chk_sales_transactions_amount;
ALTER TABLE sales_transactions DROP CONSTRAINT IF EXISTS chk_sales_transactions_grand_total;

ALTER TABLE sales_transactions ADD CONSTRAINT chk_sales_transactions_amount 
    CHECK ((transaction_type = 'REFUND' AND amount < 0) OR (transaction_type != 'REFUND' AND amount > 0));

ALTER TABLE sales_transactions ADD CONSTRAINT chk_sales_transactions_grand_total 
    CHECK ((transaction_type = 'REFUND' AND grand_total < 0) OR (transaction_type != 'REFUND' AND grand_total > 0));

-- 4. Add breakdown metric counts to import_batches
ALTER TABLE import_batches ADD COLUMN sales_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE import_batches ADD COLUMN refunds_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE import_batches ADD COLUMN exchanges_count INTEGER NOT NULL DEFAULT 0;

-- Backfill historical import_batches so sales_count = successful_rows
UPDATE import_batches 
SET sales_count = successful_rows 
WHERE sales_count = 0 AND successful_rows > 0;
