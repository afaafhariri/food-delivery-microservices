--liquibase formatted sql

--changeset quickbite:restaurant-003-orders
-- The restaurant's own view of the orders placed with it, built from order.placed and
-- order.cancelled. order-service owns orders; this copy lets the restaurant list incoming orders
-- and accept or reject them without calling order-service.
CREATE TABLE IF NOT EXISTS restaurant_orders (
    order_id         UUID PRIMARY KEY,
    restaurant_id    UUID          NOT NULL,
    customer_id      UUID,
    delivery_address TEXT,
    total_amount     DECIMAL(10,2),
    items            TEXT          NOT NULL DEFAULT '[]',  -- JSON array from order.placed
    status           VARCHAR(20)   NOT NULL,               -- PLACED, ACCEPTED, REJECTED, CANCELLED
    reason           TEXT,                                 -- rejection or cancellation reason
    placed_at        TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_restaurant ON restaurant_orders (restaurant_id, placed_at DESC);
--rollback DROP TABLE IF EXISTS restaurant_orders;
