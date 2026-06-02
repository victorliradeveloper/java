CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL
);
