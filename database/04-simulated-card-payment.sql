BEGIN;
ALTER TABLE payment.products_sold ADD COLUMN IF NOT EXISTS payment_method VARCHAR(80);
ALTER TABLE payment.products_sold ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;
ALTER TABLE payment.products_sold ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(80);
COMMIT;
