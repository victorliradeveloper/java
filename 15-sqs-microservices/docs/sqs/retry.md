# Retry em sistemas distribuídos

Tentar de novo uma operação que falhou por motivo **transitório** (glitch de rede, pod reiniciando, dependência sobrecarregada). Em microservices, a maioria das falhas é transitória — retry transforma falha em sucesso silencioso.

Referência ao projeto:

- **Retry de consumer (broker):** [`localstack/init-aws.sh:28`](../../localstack/init-aws.sh) (`MAX_RECEIVE_COUNT=3`), aplicado em [`init-aws.sh:59`](../../localstack/init-aws.sh) via `RedrivePolicy`.
- **Retry de publish (outbox):** [`OutboxPublisher.publishOne`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java) com `try/catch` que incrementa `attempts`; entidade em [`OutboxEvent.markFailed`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java); reivindicação atômica em [`OutboxEventRepositoryImpl.claimNext`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/repository/OutboxEventRepositoryImpl.java).
- **Idempotência (pré-requisito):** [`TodoEventAuditListener.onTodoEvent`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoEventAuditListener.java) com `_id = MessageId`; tabela `processed_messages` no notification-service.
- **DLQ (onde retry desiste):** [`TodoEventDlqListener`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventDlqListener.java) e [`TodoEventAuditDlqListener`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoEventAuditDlqListener.java).

---

## Por que existe

Em monolito, chamada de método não falha por motivo transitório. Em microservices, falhas transitórias são **a regra**:

- Pod reiniciado pelo k8s no meio da request.
- Rolling deploy — instância antiga vai morrendo.
- Glitch de rede de 200ms entre dois nodes.
- Serviço alvo retorna 503 por 2s durante um spike.

Todas essas falhas voltam ao normal em segundos. Sem retry, o cliente repassa erro pra cima — usuário vê falha, request perdida. Com retry, o erro vira invisível.

---

## O problema do retry ingênuo

```java
for (int i = 0; i < 5; i++) {
    try { client.call(); break; }
    catch (Exception e) { /* tenta de novo */ }
}
```

```
T+0ms   serviço sobrecarregado (CPU 95%)
T+1ms   client A tenta → falha
T+2ms   client A tenta de novo → falha
T+3ms   client A tenta de novo → falha
        ... outros 100 clients fazendo o mesmo
        serviço explode totalmente
```

Isso é **retry storm** / **thundering herd**: vc transforma problema pequeno em outage. Empresa real já caiu por isso.

---

## A forma certa: exponential backoff + jitter

**Exponential backoff** — cada retry espera o dobro do anterior, dando tempo da dependência respirar:

```
tentativa 1 → falha → espera 100ms
tentativa 2 → falha → espera 200ms
tentativa 3 → falha → espera 400ms
tentativa 4 → falha → espera 800ms
tentativa 5 → falha → desiste (DLQ ou erro pro caller)
```

**Jitter** — adiciona random ao tempo de espera. Sem jitter, 1000 clients que falharam ao mesmo tempo tentam de novo ao mesmo tempo → thundering herd. Com jitter (`waitTime + random(0, waitTime)`), eles se espalham.

---

## Pré-requisito inegociável: idempotência

**Só dá retry em operação idempotente.** Idempotente = chamar N vezes tem o mesmo efeito de chamar 1 vez.

| Operação | Idempotente? | Retry seguro? |
|---|---|---|
| `GET /todos/123` | ✓ | ✓ |
| `PUT /todos/123` (sobrescreve) | ✓ | ✓ |
| `DELETE /todos/123` | ✓ | ✓ |
| `POST /todos` (cria) | ✗ | ✗ — cria duplicado |
| `POST /transfer { amount: 100 }` | ✗ | ✗ — transfere 2x |

Pra POST seguro com retry: **Idempotency Key**. Cliente gera UUID, manda no header. Server guarda key → resultado. Retry com mesmo key retorna mesmo resultado sem executar de novo. É o que Stripe e PayPal usam.

No projeto, dedupe acontece via `_id = MessageId` ([`TodoAuditLog.java`](../../audit-service/src/main/java/com/microservices/audit/infrastructure/entity/TodoAuditLog.java)) e via collection `processed_messages` no notification — mesmo princípio.

---

## Onde já existe no projeto

### 1. Retry de consumer SQS (broker faz, sem código)

[`init-aws.sh:28`](../../localstack/init-aws.sh) define `MAX_RECEIVE_COUNT=3` e [`init-aws.sh:59`](../../localstack/init-aws.sh) aplica via `RedrivePolicy` em cada fila principal:

```bash
"RedrivePolicy": "{\"deadLetterTargetArn\":\"${dlq_arn}\",\"maxReceiveCount\":\"${MAX_RECEIVE_COUNT}\"}"
```

A função [`configure_dlq`](../../localstack/init-aws.sh) é chamada uma vez por fila — `todo-created-queue`, `todo-updated-queue`, `todo-deleted-queue`, `todo-audit-queue`.

```
consumer falha → msg volta pra fila (após VisibilityTimeout)
consumer falha → msg volta pra fila
consumer falha → SQS desiste, move pra DLQ
```

O **VisibilityTimeout funciona como backoff** entre tentativas. Pra backoff custom, o listener chama `Visibility.changeTo(N)` antes de lançar — ver [`docs/sqs/visibility-timeout.md`](./visibility-timeout.md).

### 2. DLQ — onde a msg para depois dos 3 retries

Cada fila principal tem listener de DLQ que loga em `WARN`:

- Notification: [`TodoEventDlqListener`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventDlqListener.java) — 3 listeners (`onCreatedDlq`, `onUpdatedDlq`, `onDeletedDlq`).
- Audit: [`TodoEventAuditDlqListener.onAuditDlq`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoEventAuditDlqListener.java).

Ambos recebem `String` (não `TodoEvent`) — payload malformado é a causa #1 de chegar na DLQ, desserializar de novo daria o mesmo erro em loop.

### 3. Retry de publish do outbox

[`OutboxPublisher.publishOne`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java) publica no SNS dentro de `try/catch`; em falha chama `event.markFailed(...)`, que incrementa `attempts` e grava `last_error` ([`OutboxEvent.markFailed`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java)):

```java
public void markFailed(String reason) {
    this.attempts++;
    this.lastError = reason;
    this.processingNode = null;
    this.leaseExpiresAt = null;
}
```

O `@Scheduled` em [`OutboxPublisher.publishPending`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java) roda a cada `poll-interval-ms: 2000` ([`application.yml:41`](../../todo-service/src/main/resources/application.yml)) e tenta de novo via [`OutboxEventRepositoryImpl.claimNext`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/repository/OutboxEventRepositoryImpl.java) — `findAndModify` atômico que ignora docs com `lease_expires_at` válido (`lease-duration-ms: 30000` em [`application.yml:47`](../../todo-service/src/main/resources/application.yml)).

Crash do publisher → lease expira em 30s → próximo claim retoma. Sem backoff por enquanto: retry imediato a cada poll (registrado em [`OutboxEvent.markFailed:67`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java) como dívida). Ver [`docs/sqs/outbox.md`](./outbox.md).

### 4. Idempotência dos consumers (que viabiliza o retry)

Sem isso, todo retry duplicaria. Implementações no projeto:

- **Audit:** [`TodoEventAuditListener.onTodoEvent`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoEventAuditListener.java) usa `_id = messageId.toString()`. Insert duplicado lança `DuplicateKeyException`, tratada como "já processado". Mais barato e correto que tabela auxiliar.
- **Notification:** collection `processed_messages` indexada por `messageId`. Listener grava antes de enviar email; se já existe, ignora.

### 5. **Não existe ainda**: retry em chamada HTTP entre serviços

Quando `notification-service` chamar SMTP ou um webhook externo, vai precisar de retry HTTP. Padrão em Java: **Resilience4j**.

---

## Resilience4j — retry de chamada HTTP

Dependência:

```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

Anotação no método:

```java
@Retry(name = "emailService", fallbackMethod = "fallback")
public void sendEmail(String to, String body) {
    emailClient.send(to, body);  // pode dar 503
}

public void fallback(String to, String body, Exception ex) {
    log.error("Email falhou após retries, gravando pra reprocessar", ex);
    failedEmailRepository.save(...);
}
```

Config no `application.yml`:

```yaml
resilience4j.retry:
  instances:
    emailService:
      max-attempts: 3
      wait-duration: 100ms
      exponential-backoff-multiplier: 2
      retry-exceptions:
        - java.io.IOException
        - org.springframework.web.client.HttpServerErrorException
      ignore-exceptions:
        - org.springframework.web.client.HttpClientErrorException  # 4xx — não retry
```

Resilience4j faz exponential backoff automático e respeita o whitelist de exceptions.

---

## Quando dar retry — tabela de decisão

| Erro | Retry? | Por quê |
|---|---|---|
| `503 Service Unavailable` | ✓ | Transitório, vai voltar |
| `504 Gateway Timeout` | ✓ | Provavelmente transitório |
| `429 Too Many Requests` | ✓ | Respeitar `Retry-After` header |
| `IOException`, `ConnectException` | ✓ | Glitch de rede |
| `SocketTimeoutException` | ✓ | Resposta demorou; serviço pode ter respondido |
| `400 Bad Request` | ✗ | Request errada; retry falha igual |
| `401 Unauthorized` | ✗ | Token errado; retry não conserta |
| `403 Forbidden` | ✗ | Permissão; retry não muda |
| `404 Not Found` | ✗ | Não existe; retry não cria |
| `409 Conflict` | ⚠️ | Geralmente bug de concorrência — ação manual |

**Regra**: retry só em erro **transitório** (5xx, network) **E** em operação **idempotente**.

---

## Cuidados

- **Sem idempotência, retry vira bug** — duplica cobrança, duplica email, duplica registro. Idempotência **vem primeiro**, retry depois.
- **Sempre defina `max-attempts`** — retry infinito esconde bug permanente e segura recursos.
- **Não retry em 4xx** — exceto `408 Request Timeout` e `429 Too Many Requests`. 4xx geralmente é problema do cliente.
- **Combine com timeout** — sem timeout, retry espera infinito por resposta que nunca vem.
- **Combine com circuit breaker** — depois de N retries em sequência, "abre" o circuito e para de tentar por um tempo. Resilience4j tem `@CircuitBreaker` no mesmo pacote.
- **Cuidado com retry encadeado** — A chama B chama C. Cada um com 3 retries → 27 chamadas em C pra 1 request em A. Costuma-se desabilitar retry em camadas intermediárias.
- **Observabilidade** — métrica de "tentativas por chamada" é sinal precoce de problema. Resilience4j expõe via Actuator/Micrometer.

---

## Idempotency Key — pra POST seguro com retry

```
POST /payments
Idempotency-Key: 7f3a-8c12-...
Content-Type: application/json

{ "amount": 100, "currency": "BRL" }
```

Server:

```java
@PostMapping("/payments")
public Payment create(@RequestHeader("Idempotency-Key") String key,
                      @RequestBody PaymentRequest req) {
    Optional<Payment> existing = idempotencyKeys.findResult(key);
    if (existing.isPresent()) return existing.get();    // retry: retorna resultado anterior

    Payment p = paymentService.charge(req);
    idempotencyKeys.save(key, p);                       // grava key + resultado
    return p;
}
```

Próxima chamada com mesmo `Idempotency-Key` retorna o `Payment` anterior **sem cobrar de novo**.

---

## Referências

- [`docs/sqs/dlq.md`](./dlq.md) — onde a mensagem para depois de N retries falharem.
- [`docs/sqs/visibility-timeout.md`](./visibility-timeout.md) — backoff entre tentativas no SQS.
- [`docs/sqs/outbox.md`](./outbox.md) — retry de publish (coluna `attempts`).
- [`.spec/01-issues/closed/idempotency.md`](../../.spec/01-issues/closed/idempotency.md) — design de idempotência no projeto.
- [Resilience4j — Retry](https://resilience4j.readme.io/docs/retry).
- [AWS Architecture Blog — Exponential Backoff and Jitter](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/).
