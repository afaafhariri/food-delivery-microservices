--liquibase formatted sql

-- Ported from golang-migrate 000001. IF NOT EXISTS makes it a no-op on
-- databases that were already migrated by the Go service.

--changeset quickbite:001-create-orders
CREATE TABLE IF NOT EXISTS orders (
    id               UUID PRIMARY KEY,
    customer_id      UUID          NOT NULL,
    restaurant_id    UUID          NOT NULL,
    delivery_address TEXT          NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PLACED',
    total_amount     DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS order_items (
    id             UUID PRIMARY KEY,
    order_id       UUID REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id   UUID          NOT NULL,
    menu_item_name VARCHAR(255)  NOT NULL,
    quantity       INT           NOT NULL,
    unit_price     DECIMAL(10,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
--rollback DROP TABLE IF EXISTS order_items; DROP TABLE IF EXISTS orders;

--changeset quickbite:002-order-query-indexes
-- List queries sort by created_at and batch-load items by order_id.
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
--rollback DROP INDEX IF EXISTS idx_orders_created_at; DROP INDEX IF EXISTS idx_order_items_order_id;
