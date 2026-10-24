BEGIN;
ALTER TABLE payment.products_sold ADD COLUMN IF NOT EXISTS order_status VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';
UPDATE payment.products_sold SET order_status = 'CANCELADO'
WHERE id_state_sold IN (SELECT id_state_sold FROM payment.state_sold WHERE UPPER(name_state) = 'CANCELADO')
  AND order_status = 'PENDIENTE';
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='products_sold_order_status_check' AND conrelid='payment.products_sold'::regclass) THEN
  ALTER TABLE payment.products_sold ADD CONSTRAINT products_sold_order_status_check
  CHECK (order_status IN ('PENDIENTE','PREPARANDO','EN_CAMINO','ENTREGADO','CANCELADO'));
 END IF;
END $$;
COMMIT;
