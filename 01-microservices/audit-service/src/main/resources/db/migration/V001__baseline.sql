-- Baseline schema do audit-service.
-- Em DBs novos (volume vazio), o Flyway executa esta migration.
-- Em DBs existentes, baseline-on-migrate=true + baseline-version=1 marca esta
-- versao como ja aplicada, sem re-executar (ver application.yml).

CREATE TABLE todo_audit_log (
    message_id   VARCHAR(128) PRIMARY KEY,
    aggregate_id VARCHAR(64)  NOT NULL,
    title        VARCHAR(512),
    event_type   VARCHAR(32)  NOT NULL,
    occurred_at  TIMESTAMP    NOT NULL,
    recorded_at  TIMESTAMP    NOT NULL
);

-- Acelera consulta "historico de um Todo" (filtro por aggregate_id + ordem temporal).
CREATE INDEX idx_audit_aggregate ON todo_audit_log (aggregate_id, occurred_at);

-- Acelera consulta por tipo de evento + janela de tempo.
CREATE INDEX idx_audit_event_type_occurred ON todo_audit_log (event_type, occurred_at);
