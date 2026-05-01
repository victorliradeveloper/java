CREATE TABLE orders (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID           NOT NULL REFERENCES users(id),
    description VARCHAR(500)   NOT NULL,
    amount      NUMERIC(10, 2) NOT NULL,
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
