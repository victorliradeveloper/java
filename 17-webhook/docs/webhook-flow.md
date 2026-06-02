# Fluxo do Webhook — função por função

Mapa de quem chama quem, dos dois lados do webhook.

## Visão geral

```
PaymentProcessor.processAsync          (payment-service)
        │
        ▼
OrderServiceClient.notifyPayment       ◄── DISPARA o webhook
        │
        │  POST /webhooks/payment
        │  X-Signature: sha256=<hmac>
        ▼
WebhookSignatureFilter.doFilterInternal (order-service, valida HMAC)
        │
        ▼
PaymentWebhookController.receive        ◄── RECEBE o webhook
        │
        ▼
OrderService.processPaymentWebhook      (idempotência + update da Order)
```

---

## Lado que dispara — `payment-service`

### 1. `PaymentProcessor.processAsync`

`payment-service/src/main/java/com/webhook/payment/service/PaymentProcessor.java:29`

Roda em thread separada (`@Async`). Dorme 5s simulando uma operadora externa,
aprova o pagamento e dispara o webhook.

```java
@Async
public void processAsync(UUID paymentId) {
    Thread.sleep(PROCESSING_DELAY);                                  // 5s
    Payment approved = paymentService.updateStatus(paymentId, APPROVED);
    UUID eventId = UUID.randomUUID();
    orderServiceClient.notifyPayment(eventId, approved.orderId(), approved.status());
}
```

### 2. `OrderServiceClient.notifyPayment`

`payment-service/src/main/java/com/webhook/payment/client/OrderServiceClient.java:33`

Faz o `POST /webhooks/payment`. Tem retry com backoff exponencial e `@Recover`
que loga a falha final.

```java
@Retryable(retryFor = RestClientException.class, maxAttempts = 3,
           backoff = @Backoff(delay = 500, multiplier = 2))
public void notifyPayment(UUID eventId, UUID orderId, PaymentStatus status) {
    restClient.post()
            .uri("/webhooks/payment")
            .body(new PaymentWebhookPayload(eventId, orderId, status.name()))
            .retrieve()
            .toBodilessEntity();
}
```

### 3. `WebhookSigningInterceptor.intercept`

`payment-service/src/main/java/com/webhook/payment/client/WebhookSigningInterceptor.java:28`

Interceptor do `RestClient`. Calcula `HMAC-SHA256(body, secret)` e adiciona o
header `X-Signature: sha256=<hex>` antes da request sair.

---

## Lado que recebe — `order-service`

### 4. `WebhookSignatureFilter.doFilterInternal`

`order-service/src/main/java/com/webhook/order/security/WebhookSignatureFilter.java:43`

Filtro aplicado a todo path `/webhooks/**`. Lê o body, recalcula o HMAC com o
segredo compartilhado e compara com o header `X-Signature` em **constant
time**. Se não bater, devolve `401` e o controller nem chega a rodar.

### 5. `PaymentWebhookController.receive`

`order-service/src/main/java/com/webhook/order/controller/PaymentWebhookController.java:22`

Endpoint `POST /webhooks/payment`. Só desserializa o payload e delega pro
service.

```java
@PostMapping
public ResponseEntity<Void> receive(@RequestBody PaymentWebhookRequest request) {
    orderService.processPaymentWebhook(request.eventId(), request.orderId(), request.status());
    return ResponseEntity.noContent().build();   // 204
}
```

### 6. `OrderService.processPaymentWebhook`

`order-service/src/main/java/com/webhook/order/service/OrderService.java:60`

Onde mora a **idempotência**. Antes de tocar na `Order`, checa se o `eventId`
já foi processado. Se já foi, ignora. Se não, atualiza a `Order` e marca o
evento como processado — tudo na mesma transação.

```java
@Transactional
public void processPaymentWebhook(UUID eventId, UUID orderId, OrderStatus newStatus) {
    if (processedEventRepository.existsById(eventId)) {
        log.info("Event {} already processed, skipping", eventId);
        return;
    }
    Order order = findById(orderId);
    order.changeStatus(newStatus);
    repository.save(order);
    processedEventRepository.save(new ProcessedWebhookEvent(eventId, Instant.now()));
}
```

---

## Resumo de uma linha

| # | Função | Papel |
|---|--------|-------|
| 1 | `PaymentProcessor.processAsync` | Espera 5s, aprova, chama o client |
| 2 | `OrderServiceClient.notifyPayment` | `POST /webhooks/payment` com retry |
| 3 | `WebhookSigningInterceptor.intercept` | Assina o body com HMAC-SHA256 |
| 4 | `WebhookSignatureFilter.doFilterInternal` | Valida a assinatura (rejeita 401) |
| 5 | `PaymentWebhookController.receive` | Endpoint HTTP que recebe |
| 6 | `OrderService.processPaymentWebhook` | Idempotência + update da Order |
