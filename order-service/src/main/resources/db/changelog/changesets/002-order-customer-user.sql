--liquibase formatted sql

--changeset quickbite:003-order-customer-user
-- Keycloak user id of the customer account that placed the order (from customer-service's
-- ResolveCustomer). Customers can only see and cancel orders with their own id here. NULL for
-- orders placed before authentication existed, or for admin-managed customer profiles.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_user_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_orders_customer_user_id ON orders(customer_user_id);
--rollback DROP INDEX IF EXISTS idx_orders_customer_user_id; ALTER TABLE orders DROP COLUMN IF EXISTS customer_user_id;
