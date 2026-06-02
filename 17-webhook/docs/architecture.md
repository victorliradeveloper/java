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
   │   controller/OrderController                                   │
   │       │                                                        │
   │       ▼                                                        │
   │   service/OrderService  @Transactional                         │
   │       │                                                        │
   │       ├──► repository/OrderRepository  (save Order PENDING)    │
   │       │                                                        │
   │       └──► client/PaymentClient  (RestClient)                  │
   │                  │                                             │
   │                  │ POST /payments  (síncrono)                  │
   │                  ▼                                             │
   └─────────────────┬──────────────────────────────────────────────┘
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
   │       ├──► repository/PaymentRepository  (save PROCESSING)     │
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
   │                  └─► POST /webhooks/payment                    │
   │                       header X-Signature: sha256=...           │
   └────────────────────────────┬───────────────────────────────────┘
                                │
                                ▼
   ┌────────────────────────────────────────────────────────────────┐
   │              ORDER SERVICE  (recebe o webhook)                 │
   │                                                                │
   │   security/WebhookSignatureFilter                              │
   │   (só /webhooks/**, valida HMAC em constant-time)              │
   │       │                                                        │
   │       ▼                                                        │
   │   controller/PaymentWebhookController                          │
   │       │                                                        │
   │       ▼                                                        │
   │   service/OrderService.processPaymentWebhook  @Transactional   │
   │       │   (checa idempotência via eventId)                     │
   │       │                                                        │
   │       ├──► repository/OrderRepository  (update → APPROVED)     │
   │       │                                                        │
   │       └──► repository/ProcessedWebhookEventRepository          │
   │                       (marca eventId como processado)          │
   └────────────────────────────┬───────────────────────────────────┘
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

## Sequência resumida

1. Cliente faz `POST /orders` → `OrderController.create`.
2. `OrderService.create` salva a `Order` como `PENDING` e chama `PaymentClient.createPayment` (síncrono).
3. `PaymentController.create` recebe → `PaymentService.create` salva `Payment` como `PROCESSING` e dispara `PaymentProcessor.processAsync` em outra thread.
4. Payment-service responde `202 Accepted` → order-service marca a `Order` como `PROCESSING` e devolve `201 Created` ao cliente.
5. Em background, `PaymentProcessor` dorme 5s, marca o `Payment` como `APPROVED` e chama `OrderServiceClient.notifyPayment`.
6. A request sai assinada (`X-Signature: sha256=<hmac>`) e bate em `POST /webhooks/payment` do order-service, com retry 3x e backoff exponencial em caso de falha.
7. `WebhookSignatureFilter` valida o HMAC. Se OK, `PaymentWebhookController.receive` chama `OrderService.processPaymentWebhook`, que checa idempotência pelo `eventId`, atualiza a `Order` para `APPROVED` e grava o evento processado — tudo na mesma transação.
