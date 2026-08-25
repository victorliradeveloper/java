-- Baseline schema do todo-service. Mantido em sincronia com as entidades JPA.
-- Em DBs novos (volume vazio), o Flyway executa esta migration.
-- Em DBs existentes, baseline-on-migrate=true + baseline-version=1 marca esta
-- versao como ja aplicada, sem re-executar (ver application.yml).
--
-- Sem tabela outbox: comunicacao com audit/notification eh sincrona via HTTP,
-- nao mais via outbox + RabbitMQ.

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
