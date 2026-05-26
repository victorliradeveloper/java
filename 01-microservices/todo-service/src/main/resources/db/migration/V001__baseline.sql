-- Baseline schema do todo-service. Mantido em sincronia com as entidades JPA.
-- Em DBs novos (volume vazio), o Flyway executa esta migration.
-- Em DBs existentes, baseline-on-migrate=true + baseline-version=1 marca esta
-- versao como ja aplicada, sem re-executar (ver application.yml).

CREATE TABLE todos (
    id          VARCHAR(255) PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    completed   BOOLEAN      NOT NULL,
    created_at  TIMESTAMP    NOT NULL
);

CREATE TABLE idempotency_keys (
    key             VARCHAR(255) PRIMARY KEY,
    request_hash    VARCHAR(64)  NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    created_at      TIMESTAMP    NOT NULL,
    expires_at      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);

CREATE TABLE outbox_events (
    id               VARCHAR(36)  PRIMARY KEY,
    aggregate_id     VARCHAR(64)  NOT NULL,
    aggregate_type   VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(64)  NOT NULL,
    exchange         VARCHAR(128) NOT NULL,
    routing_key      VARCHAR(128) NOT NULL,
    payload          TEXT         NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    published_at     TIMESTAMP,
    attempts         INTEGER      NOT NULL,
    last_error       TEXT,
    processing_node  VARCHAR(128),
    lease_expires_at TIMESTAMP,
    next_attempt_at  TIMESTAMP
);

-- Acelera o claim do publisher: filtra pendentes (published_at IS NULL)
-- e ordena por created_at FIFO.
CREATE INDEX idx_outbox_pending ON outbox_events (published_at, created_at);
