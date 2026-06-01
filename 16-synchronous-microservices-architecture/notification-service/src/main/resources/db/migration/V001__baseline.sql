-- Baseline schema do notification-service.
-- Em DBs novos (volume vazio), o Flyway executa esta migration.
-- Em DBs existentes, baseline-on-migrate=true + baseline-version=1 marca esta
-- versao como ja aplicada, sem re-executar (ver application.yml).

CREATE TABLE processed_events (
    event_id     VARCHAR(128) PRIMARY KEY,
    processed_at TIMESTAMP    NOT NULL
);

-- Acelera o job de cleanup que faz DELETE WHERE processed_at < cutoff.
CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
