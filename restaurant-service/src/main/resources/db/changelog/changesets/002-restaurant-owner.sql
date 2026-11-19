--liquibase formatted sql

--changeset quickbite:restaurant-002-owner
-- Keycloak user id (token "sub") of the restaurant account that created the restaurant.
-- NULL for restaurants created before authentication existed: only admins can change those.
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS owner_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_restaurants_owner_id ON restaurants(owner_id);
--rollback DROP INDEX IF EXISTS idx_restaurants_owner_id; ALTER TABLE restaurants DROP COLUMN IF EXISTS owner_id;
