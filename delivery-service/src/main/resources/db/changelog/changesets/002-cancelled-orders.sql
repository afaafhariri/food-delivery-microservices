--liquibase formatted sql

--changeset quickbite:003-cancelled-orders
-- Orders announced on order.cancelled. It can arrive before order.placed (different
-- topics), so assignment checks this table and never books a driver for a cancelled order.
CREATE TABLE IF NOT EXISTS cancelled_orders (
    order_id     UUID PRIMARY KEY,
    cancelled_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE IF EXISTS cancelled_orders;
