CREATE TABLE products
(
    sku      VARCHAR(32) PRIMARY KEY,
    name     VARCHAR(120)   NOT NULL,
    price    NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    currency CHAR(3)        NOT NULL,
    active   BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE orders
(
    id                  UUID PRIMARY KEY,
    customer_id         VARCHAR(64)    NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    currency            CHAR(3)        NOT NULL,
    subtotal            NUMERIC(12, 2) NOT NULL,
    discount            NUMERIC(12, 2) NOT NULL,
    total               NUMERIC(12, 2) NOT NULL,
    payment_reference   VARCHAR(64),
    cancellation_reason VARCHAR(200),
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    version             BIGINT         NOT NULL
);

CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at DESC);

CREATE TABLE order_items
(
    id         BIGSERIAL PRIMARY KEY,
    order_id   UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sku        VARCHAR(32)    NOT NULL,
    name       VARCHAR(120)   NOT NULL,
    quantity   INT            NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
