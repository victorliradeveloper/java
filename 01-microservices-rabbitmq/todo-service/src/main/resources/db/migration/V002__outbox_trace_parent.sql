-- Trace context (W3C traceparent) capturado no momento em que o evento entra no
-- outbox. O publisher roda num @Scheduled (thread propria, sem o trace da
-- requisicao HTTP original), entao sem persistir aqui o trace se perderia e a
-- publicacao + o consumer apareceriam como um trace novo, desconexo da request.
-- Nullable: eventos enfileirados fora de um contexto de trace ficam null.
-- traceparent W3C tem 55 chars ("00-<32hex>-<16hex>-01"); 64 da folga.
ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(64);
