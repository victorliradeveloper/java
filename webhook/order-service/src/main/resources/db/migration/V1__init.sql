CREATE TABLE orders (
    id UUID PRIMARY KEY,
    product VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE processed_webhook_events (
    event_id UUID PRIMARY KEY,
    received_at TIMESTAMP NOT NULL
);
