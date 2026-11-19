--liquibase formatted sql

--changeset quickbite:004-driver-user-id
-- Keycloak user id of the driver account that registered this driver; one driver per account.
-- NULL for drivers created before authentication existed (admin-managed).
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS user_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_drivers_user_id ON drivers(user_id);
--rollback DROP INDEX IF EXISTS uq_drivers_user_id; ALTER TABLE drivers DROP COLUMN IF EXISTS user_id;
