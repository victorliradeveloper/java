# Diagrama ponta a ponta — arquitetura sincrona

Visao da topologia + fluxo de um `POST /todos` atravessando todos os componentes.
Complementa `docs/arquitetura-sincrona.md` com o "mapa" dos servicos.

```
                         ┌─────────────────────────┐
                         │      CLIENTE HTTP       │
                         └────────────┬────────────┘
                                      │
                          POST /todos │  (header opcional: Idempotency-Key)
                          GET  /todos │
                          PUT  /todos │
                          DEL  /todos │
                                      ▼
   ┌──────────────────────────────────────────────────────────────────┐
   │              API GATEWAY  (host 8090 → container 8080)           │
   │                                                                  │
   │   Spring Cloud Gateway                                           │
   │       │  predicate: Path=/todos/**                               │
   │       │  filter:    RequestRateLimiter ──► Redis (token bucket)  │
   │       │  uri:       lb://todo-service ◄──┐                       │
   │       ▼                                  │ discovery + LB        │
   └──────────────────────────────────────────┼───────────────────────┘
                                              │
                                ┌─────────────┴──────────────┐
                                │   EUREKA SERVER  (8761)    │
                                │   service registry         │
                                │  ▲     ▲      ▲      ▲     │
                                └──┼─────┼──────┼──────┼─────┘
                          register│     │ resolve     │
                                  │     │             │
   ┌──────────────────────────────┴─────┴─────────────┴────────────────┐
   │                  TODO SERVICE   (porta 8081)                      │
   │                                                                   │
   │   controller/ TodoController                                      │
   │       │                                                           │
   │       ▼                                                           │
   │   idempotency/ IdempotencyService                                 │
   │       │   ① INSERT atômico em idempotency_keys (claim)            │
   │       │      ├─ key nova   → segue p/ operação                    │
   │       │      └─ key existe → replay do response cacheado          │
   │       ▼                     ou 409 (hash mismatch / in-progress)  │
   │   service/ TodoService     ◄─ orquestra: persiste + notifica      │
   │       │                                                           │
   │       │ (1) ┌──────────────────────────────────────────┐          │
   │       ├────►│ TodoPersistenceService  @Transactional   │          │
   │       │     │   INSERT/UPDATE/DELETE em todos          │          │
   │       │     │   ── COMMIT ── (libera conn pool)        │          │
   │       │     └──────────────────────────────────────────┘          │
   │       │                                                           │
   │       │  (chamadas HTTP fora da transação ─ pool livre)           │
   │       │                                                           │
   │       │ (2) ┌──────────────────────────────────────────┐          │
   │       └────►│ DownstreamNotifier                       │          │
   │             │   eventId = UUID  (chave de idempotência)│          │
   │             │                                          │          │
   │             │   ┌──────────────────┐ ┌───────────────┐ │          │
   │             │   │ notifyAudit      │ │ notifyNotif.  │ │          │
   │             │   │  @CircuitBreaker │ │ @CircuitBrk.  │ │          │
   │             │   │  @Retry (3x      │ │ @Retry        │ │          │
   │             │   │   200/400/800ms) │ │               │ │          │
   │             │   │  fallback: LOGA  │ │ fallback: LOG │ │          │
   │             │   │   e segue (201)  │ │  e segue      │ │          │
   │             │   └────────┬─────────┘ └───────┬───────┘ │          │
   │             └────────────┼───────────────────┼─────────┘          │
   │                          │                   │                    │
   │   client/  AuditClient   │   NotificationClient                   │
   │   (Feign)  lb://audit    │   (Feign) lb://notification            │
   └──────────────────────────┼───────────────────┼────────────────────┘
                              │                   │
              POST /audit-logs│  POST /notifications/todo-events
                              ▼                   ▼
   ┌──────────────────────────────┐   ┌──────────────────────────────────┐
   │   AUDIT SERVICE (8083)       │   │   NOTIFICATION SERVICE (8082)    │
   │                              │   │                                  │
   │   AuditController            │   │   NotificationController         │
   │       │  POST /audit-logs    │   │       │  POST /notifications/... │
   │       ▼                      │   │       ▼                          │
   │   AuditService               │   │   NotificationService            │
   │     insertIfAbsent(eventId)  │   │     1) existsById(eventId)?      │
   │     ON CONFLICT DO NOTHING   │   │        sim → DEDUPE (descarta)   │
   │     ── dedupe via PK ──      │   │     2) emailService.send(...)    │
   │       │                      │   │     3) tryInsert(eventId)        │
   │       ▼                      │   │        ── dedupe APÓS envio ──   │
   │   todo_audit_log             │   │       │            │             │
   │   (auditdb)                  │   │       ▼            ▼             │
   │   sempre devolve 202         │   │  processed_events  SMTP (Gmail)  │
   │                              │   │  (notificationdb)   ▲            │
   │                              │   │   sempre 202 (ou 500 se SMTP cai)│
   └──────────────┬───────────────┘   └──────────────┬───────────────────┘
                  │                                  │
                  └──────────┐          ┌────────────┘
                             ▼          ▼
                       ┌──────────────────────┐
                       │   POSTGRES  (5432)   │
                       │   tododb             │
                       │   auditdb            │
                       │   notificationdb     │
                       └──────────────────────┘

   ════════════ INVARIANTES IMPORTANTES ════════════
   • Persistência commita ANTES das chamadas HTTP → não segura conn pool durante request remoto
   • DownstreamNotifier tem CB+Retry POR DOWNSTREAM (nomes distintos) → blast radius isolado
   • Fallback "loga e segue" = request principal retorna 201 mesmo com downstream caído (evento PERDIDO)
   • eventId (UUID) é gerado no todo-service e funciona como chave de dedupe nos 2 downstreams
   • Idempotency-Key (header) protege o POST /todos contra retry do cliente; eventId protege o fan-out
   • Sem RabbitMQ / outbox / DLQ — durabilidade trocada por simplicidade operacional
```

## Como ler o fluxo de um `POST /todos`

1. Cliente → `API Gateway:8090` (rate limit no Redis) → resolve `lb://todo-service` no Eureka → encaminha pra `todo-service:8081`.
2. `TodoController` chama `IdempotencyService.executeIdempotent(...)` — se o header `Idempotency-Key` veio, tenta um INSERT atômico em `idempotency_keys`. Key duplicada com mesmo payload = retorna a response cacheada; payload diferente = 409.
3. Claim aceito → `TodoService.create()` chama `TodoPersistenceService` que abre `@Transactional`, salva no `tododb` e **commita**. Conn pool é liberada antes de qualquer chamada remota (`docs/arquitetura-sincrona.md:87-102`).
4. Após o commit, `DownstreamNotifier` faz duas chamadas Feign **sequenciais** com o mesmo `eventId`:
   - `notifyAudit` (CB `audit-service`, retry 3x com backoff 200/400/800ms, fallback loga).
   - `notifyNotification` (CB `notification-service`, mesmo padrão).
5. `audit-service` insere com `ON CONFLICT DO NOTHING` em `todo_audit_log` (eventId é PK). `notification-service` checa `processed_events`, envia email via SMTP, depois marca como processado (dedupa **depois** do envio pra preferir duplicar a perder — `NotificationService.java:11-22`).
6. Qualquer falha downstream cai no fallback → loga e segue → cliente vê **201 Created**. Trade-off explícito: o evento foi perdido (`DownstreamNotifier.java:22-26`).
