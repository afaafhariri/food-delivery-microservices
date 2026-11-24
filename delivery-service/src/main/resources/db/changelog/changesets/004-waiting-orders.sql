--liquibase formatted sql

--changeset quickbite:005-waiting-orders
-- Placed orders that found every driver busy. When a driver becomes free (a delivery is
-- delivered or cancelled, or a driver is set available) the oldest waiting order gets them; a
-- periodic sweep catches anything those triggers missed. Rows leave when the order is
-- assigned or cancelled.
CREATE TABLE IF NOT EXISTS waiting_orders (
    order_id      UUID PRIMARY KEY,
    waiting_since TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_waiting_orders_since ON waiting_orders(waiting_since);
--rollback DROP TABLE IF EXISTS waiting_orders;
