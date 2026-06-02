```
                          ┌─────────────────────────┐
                          │      CLIENTE HTTP       │
                          └────────────┬────────────┘
                                       │
                          POST /orders │  GET /orders/{id}
                                       ▼
   ┌────────────────────────────────────────────────────────────────┐
   │                   ORDER SERVICE  (porta 8081)                  │
   │                                                                │
   │   controller/  →  PaymentWebhookController  ←──┐               │
   │       │                                        │               │
   │       │                  security/ ─ WebhookSignatureFilter    │
   │       ▼                  (só /webhooks/**, valida HMAC)        │
   │   service/OrderService  @Transactional                         │
   │       │                                                        │
   │       ├──► repository/  OrderRepository                        │
   │       │                 ProcessedWebhookEventRepository        │
   │       │                                                        │
   │       └──► client/PaymentClient  (RestClient)                  │
   └────────────┬────────────────────────────▲──────────────────────┘
                │                            │
                │ POST /payments             │ POST /webhooks/payment
                │ (síncrono)                 │ (assinado + retry)
                ▼                            │
   ┌────────────────────────────────────────────────────────────────┐
   │                  PAYMENT SERVICE  (porta 8082)                 │
   │                                                                │
   │   controller/PaymentController                                 │
   │       │                                                        │
   │       ▼                                                        │
   │   service/PaymentService  @Transactional                       │
   │       │                                                        │
   │       ├──► repository/PaymentRepository                        │
   │       │                                                        │
   │       └──► dispara PaymentProcessor  @Async (task-1)           │
   │                  │                                             │
   │                  │ sleep 5s → updateStatus(APPROVED)           │
   │                  ▼                                             │
   │            client/OrderServiceClient                           │
   │              @Retryable(3x, backoff exp)                       │
   │              @Recover (log final)                              │
   │                  │                                             │
   │                  │ assinado por WebhookSigningInterceptor      │
   │                  └─► sai com header X-Signature: sha256=...    │
   └────────────┬───────────────────────────────────────────────────┘
                │
                ▼
   ┌─────────────────────────┐         ┌─────────────────────────┐
   │  orderdb  (Postgres 16) │         │ paymentdb (Postgres 16) │
   │  - orders               │         │ - payments              │
   │  - processed_webhook_   │         │ - flyway_schema_history │
   │    events               │         └─────────────────────────┘
   │  - flyway_schema_history│
   └─────────────────────────┘
```
