```
                          ┌─────────────────────────┐
                          │      CLIENTE HTTP       │
                          └────────────┬────────────┘
                                       │
                          POST /payments
                                       │
                                       ▼
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
                │ POST /webhooks/payment (assinado + retry)
                ▼
   ┌────────────────────────────────────────────────────────────────┐
   │                   ORDER SERVICE  (porta 8081)                  │
   │                                                                │
   │   security/ ─ WebhookSignatureFilter                           │
   │   (só /webhooks/**, valida HMAC)                               │
   │       │                                                        │
   │       ▼                                                        │
   │   controller/PaymentWebhookController                          │
   │       │                                                        │
   │       ▼                                                        │
   │   service/OrderService  @Transactional                         │
   │       │                                                        │
   │       └──► repository/  OrderRepository                        │
   │                         ProcessedWebhookEventRepository        │
   └────────────┬───────────────────────────────────────────────────┘
                │
                ▼
   ┌─────────────────────────┐         ┌─────────────────────────┐
   │ paymentdb (Postgres 16) │         │  orderdb  (Postgres 16) │
   │ - payments              │         │  - orders               │
   │ - flyway_schema_history │         │  - processed_webhook_   │
   └─────────────────────────┘         │    events               │
                                       │  - flyway_schema_history│
                                       └─────────────────────────┘
```
