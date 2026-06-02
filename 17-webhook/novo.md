Excelente projeto para aprender:

- Comunicação síncrona (REST)
- Comunicação assíncrona (Webhook)
- Microsserviços
- Docker
- Spring Boot
- Evolução para mensageria no futuro

## Arquitetura

```text
┌─────────────────┐
│  Order Service  │
└────────┬────────┘
         │
         │ POST /payments
         ▼
┌─────────────────┐
│ Payment Service │
└────────┬────────┘
         │
         │ POST /webhooks/payment
         ▼
┌─────────────────┐
│  Order Service  │
└─────────────────┘
```

---

# Fase 1 — Estrutura do projeto

Crie dois microsserviços separados:

```text
microservices-webhook-study/
│
├── order-service/
│
├── payment-service/
│
└── docker-compose.yml
```

Cada serviço terá:

```text
src/main/java
src/test/java
Dockerfile
```

---

# Fase 2 — Order Service

Responsabilidade:

- Criar pedidos
- Consultar pedidos
- Receber webhooks

## Entidade

```java
public record Order(
    UUID id,
    String product,
    BigDecimal amount,
    OrderStatus status
) {}
```

## Status

```java
public enum OrderStatus {
    PENDING,
    PROCESSING,
    APPROVED,
    REJECTED
}
```

> `PROCESSING` representa o estado entre o ack do Payment Service e a
> chegada do webhook. Sem ele, o pedido fica `PENDING` por ~5s mentindo
> sobre o estado real.

## Endpoints

### Criar pedido

```http
POST /orders
```

Request:

```json
{
  "product": "Notebook",
  "amount": 5000
}
```

Response:

```json
{
  "id": "...",
  "status": "PENDING"
}
```

---

### Buscar pedido

```http
GET /orders/{id}
```

---

### Receber webhook

```http
POST /webhooks/payment
```

Esse endpoint atualizará o pedido.

---

# Fase 3 — Payment Service

Responsabilidade:

- Receber solicitação de pagamento
- Simular processamento
- Enviar webhook

## Endpoint

```http
POST /payments
```

Request:

```json
{
  "orderId": "...",
  "amount": 5000
}
```

Response:

```json
{
  "status": "PROCESSING"
}
```

---

# Fase 4 — Comunicação síncrona

Quando o pedido for criado:

```text
POST /orders
        │
        ▼
Order Service
        │
        │ REST
        ▼
Payment Service
```

Use:

```java
RestClient
```

(Spring moderno)

Exemplo:

```java
restClient.post()
    .uri("/payments")
    .body(request)
    .retrieve();
```

---

# Fase 5 — Simular processamento assíncrono

Dentro do Payment Service:

```java
@Async
public void processPayment(...)
```

ou

```java
CompletableFuture.runAsync(...)
```

Fluxo:

```text
Recebe pagamento
↓
Retorna PROCESSING
↓
Espera 5 segundos
↓
Aprova pagamento
↓
Envia webhook
```

> ⚠️ `@Async` / `CompletableFuture` é ótimo pra aprender o fluxo, mas é
> frágil: se o Payment Service reiniciar durante os 5s de espera, o
> webhook nunca é enviado. O padrão real é uma tabela `outbox` + worker
> lendo eventos pendentes. Fica como evolução opcional após a fase 11.

---

# Fase 6 — Implementar Webhook

Após o processamento:

```java
restClient.post()
    .uri("http://order-service:8080/webhooks/payment")
    .body(webhookRequest)
    .retrieve();
```

Payload:

```json
{
  "orderId": "...",
  "status": "APPROVED"
}
```

---

# Fase 7 — Persistência

Adicionar:

### Order Service

```text
PostgreSQL
```

Tabela:

```sql
orders
```

---

### Payment Service

```text
PostgreSQL
```

Tabela:

```sql
payments
```

---

### Idempotência do webhook (antecipada)

Criar já nesta fase, no Order Service:

```sql
processed_webhook_events (
  event_id UUID PRIMARY KEY,
  received_at TIMESTAMP
)
```

E incluir `eventId` (UUID) no payload do webhook desde a fase 6.

> Idempotência aparece originalmente na fase 11, mas sem ela qualquer
> retry quebra o pedido (estado oscila a cada reentrega). É mais didático
> introduzir junto da persistência e provar o comportamento logo.

---

# Fase 8 — Docker

### Dockerfile

Para cada serviço:

```dockerfile
FROM eclipse-temurin:25-jdk

COPY target/app.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]
```

---

# Fase 9 — Docker Compose

```yaml
services:

  order-service:

  payment-service:

  order-db:

  payment-db:
```

Fluxo:

```text
docker compose up
```

Subirá:

- Order Service
- Payment Service
- PostgreSQL Orders
- PostgreSQL Payments

---

# Fase 10 — Testes

### Unitários

- OrderService
- PaymentService

### Integração

Testar:

```text
Criar pedido
↓
Chamar Payment Service
↓
Receber webhook
↓
Atualizar pedido
```

Utilize:

```java
@SpringBootTest
```

e

```java
Testcontainers
```

> Pra testar o callback de verdade, duas abordagens:
>
> - **WireMock** no teste do Payment Service: simula o endpoint
>   `/webhooks/payment` do Order e verifica que foi chamado com o payload
>   correto. Bom pra teste isolado por serviço.
> - **Testcontainers subindo os dois serviços** + Postgres: testa o
>   fluxo ponta a ponta de verdade. Mais lento, mas é o que prova que o
>   sistema funciona junto.
>
> Recomendado: WireMock por serviço + um único teste E2E com ambos.

---

# Fase 11 — Melhorias reais

## Assinatura

Header:

```http
X-Signature
```

Validar HMAC.

---

## Retry

Se o webhook falhar:

```text
Tentativa 1
Tentativa 2
Tentativa 3
```

Utilize:

```java
Spring Retry
```

---

## Idempotência

Salvar:

```text
eventId
```

Ignorar eventos repetidos.

---

# Resultado final

Ao concluir, você terá praticado:

- Java 25
- Spring Boot
- REST APIs
- RestClient
- Webhooks
- Comunicação síncrona
- Comunicação assíncrona
- Docker
- Docker Compose
- PostgreSQL
- Testcontainers
- Retry
- Idempotência
- Arquitetura de microsserviços