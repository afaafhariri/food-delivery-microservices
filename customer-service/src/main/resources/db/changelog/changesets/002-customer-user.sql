--liquibase formatted sql

--changeset quickbite:customer-002-user-id
-- Keycloak user id (token "sub") of the account this customer profile belongs to; one profile per
-- account. NULL for profiles created before authentication existed (admin-managed).
ALTER TABLE customers ADD COLUMN IF NOT EXISTS user_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_user_id ON customers(user_id);
--rollback DROP INDEX IF EXISTS uq_customers_user_id; ALTER TABLE customers DROP COLUMN IF EXISTS user_id;
