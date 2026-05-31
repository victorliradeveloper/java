# Retry em sistemas distribuídos

Tentar de novo uma operação que falhou por motivo **transitório** (glitch de rede, pod reiniciando, dependência sobrecarregada). Em microservices, a maioria das falhas é transitória — retry transforma falha em sucesso silencioso.

Referência ao projeto:

- **Retry de consumer (broker):** [`localstack/init-aws.sh:28`](../../localstack/init-aws.sh) (`MAX_RECEIVE_COUNT=3`), aplicado em [`init-aws.sh:59`](../../localstack/init-aws.sh) via `RedrivePolicy`.
- **Retry de publish (outbox):** [`OutboxPublisher.publishOne`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java) com `try/catch` + backoff exponencial agendado em [`OutboxEvent.markFailed`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java) (campo `next_attempt_at`); reivindicação atômica em [`OutboxEventRepositoryImpl.claimNext`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/repository/OutboxEventRepositoryImpl.java).
- **Retry de SMTP (notification):** [`EmailService.send`](../../notification-service/src/main/java/com/microservices/notification/service/EmailService.java) com `@Retry(name = "smtp")` + `@CircuitBreaker(name = "smtp")` via Resilience4j; config em [`notification-service/application.yml`](../../notification-service/src/main/resources/application.yml).
- **Idempotência (pré-requisito):** [`IdempotencyService.executeIdempotent`](../../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyService.java) (header `Idempotency-Key` no POST); [`TodoEventAuditListener.onTodoEvent`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoEventAuditListener.java) com `_id = MessageId`; collection `processed_messages` no notification-service.
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

## Quando entra em ação — os 3 pontos do fluxo

Retry não é uma coisa só: o projeto tem **3 camadas independentes**, cada uma cobrindo uma falha diferente.

```
POST /todos
   │
   ▼
[todo-service]
  salva Todo + OutboxEvent (mesma tx)
   │
   ▼
OutboxPublisher.publishPending  ◄── (1) RETRY DE PUBLISH
  publica no SNS                     se SNS falha → markFailed →
   │                                 next_attempt_at = agora + backoff
   ▼                                 (2s, 4s, 8s... cap 60s, com jitter)
  SNS → SQS (fanout)
   │
   ▼
[notification / audit]
  @SqsListener recebe              ◄── (2) RETRY DE CONSUMER (broker)
   │                                   se listener lança exception →
   ▼                                   msg volta à fila após VisibilityTimeout
  EmailService.send  ◄── (3) RETRY DE SMTP    até MAX_RECEIVE_COUNT=3 → DLQ
    @Retry(smtp) tenta 3x
    (200ms → 400ms → 800ms)
```

**Onde cada um age:**

1. **Publish (outbox → SNS)** — falha de rede/SNS. `OutboxPublisher` agenda nova tentativa via `next_attempt_at` na própria linha do Mongo. Backoff exponencial **por evento**.

2. **Consumer (SQS → listener)** — qualquer exception não capturada no `@SqsListener`. O **broker SQS** reentrega após `VisibilityTimeout`. Depois de 3 tentativas, vai pra **DLQ**.

3. **SMTP (notification → servidor de email)** — falha do provedor de email. `@Retry` do Resilience4j tenta 3x **dentro da mesma execução do listener**, antes de deixar a exception subir para o nível 2.

**A ordem importa**: o SMTP-retry (3) tenta primeiro localmente; se esgotar, propaga e vira retry-de-consumer (2); se esgotar de novo, vai pra DLQ. Já o (1) é independente — protege a publicação, não o consumo.

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

### 3. Retry de publish do outbox (com backoff exponencial)

Três peças cooperam:

**(a) `OutboxEvent.markFailed`** — entidade só registra a falha e aplica o `LocalDateTime` calculado pela política, sem conhecer aritmética de backoff:

```java
public void markFailed(String reason, IntFunction<LocalDateTime> nextAttemptResolver) {
    this.attempts++;
    this.lastError = reason;
    this.processingNode = null;
    this.leaseExpiresAt = null;
    this.nextAttemptAt = nextAttemptResolver.apply(this.attempts);
}
```

**(b) `BackoffPolicy`** — interface separada, default é `ExponentialJitterBackoffPolicy`:

```java
public interface BackoffPolicy {
    LocalDateTime nextAttemptAt(int attempts);
}

public class ExponentialJitterBackoffPolicy implements BackoffPolicy {
    @Override
    public LocalDateTime nextAttemptAt(int attempts) {
        int safeExponent = Math.clamp(attempts - 1L, 0, MAX_EXPONENT);
        long exponential = initialMs * (1L << safeExponent);
        long capped = Math.min(exponential, maxMs);
        return LocalDateTime.now().plus(Duration.ofMillis(applyJitter(capped)));
    }
}
```

Bean exposto em [`OutboxConfig`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxConfig.java); trocar política = trocar bean (`@Primary` num teste, perfil dedicado etc), sem mexer em entidade ou publisher.

**(c) `OutboxPublisher.publishOne`** — passa a política como method reference:

```java
event.markFailed(truncate(e.toString()), backoffPolicy::nextAttemptAt);
```

Sequência típica com defaults de [`OutboxProperties.Backoff`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxProperties.java) (`initial-ms: 2000`, `max-ms: 60000`):

```
attempts=1 → ~2s   (jitter 1.5s–2.5s)
attempts=2 → ~4s   (3s–5s)
attempts=3 → ~8s   (6s–10s)
attempts=4 → ~16s  (12s–20s)
attempts=5 → ~32s  (24s–40s)
attempts=6+ → ~60s (cap, 45s–75s)
```

O `@Scheduled` em [`OutboxPublisher.publishPending`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java) roda a cada `poll-interval-ms: 2000` e chama [`OutboxEventRepositoryImpl.claimNext`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/repository/OutboxEventRepositoryImpl.java) — `findAndModify` atômico que filtra:

```
publishedAt == null
AND (lease_expires_at == null OR lease_expires_at < now)
AND (next_attempt_at  == null OR next_attempt_at  <= now)   ← respeita backoff
ORDER BY created_at ASC
```

O índice composto `(published_at, next_attempt_at, created_at)` é criado pela [`V005_OutboxNextAttemptIndex`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V005_OutboxNextAttemptIndex.java) (Mongock).

**Crash do publisher** → lease expira em 30s → próximo claim retoma. **Backoff por evento** evita martelar SNS quando ele está degradado e dispersa retentativas no tempo. **Jitter** previne thundering herd quando dezenas de eventos falharam simultaneamente (ex.: SNS outage). Ver [`docs/sqs/outbox.md`](./outbox.md).

### 4. Idempotência dos consumers (que viabiliza o retry)

Sem isso, todo retry duplicaria. Implementações no projeto:

- **Audit:** [`TodoEventAuditListener.onTodoEvent`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoEventAuditListener.java) usa `_id = messageId.toString()`. Insert duplicado lança `DuplicateKeyException`, tratada como "já processado". Mais barato e correto que tabela auxiliar.
- **Notification:** collection `processed_messages` indexada por `messageId`. Listener grava antes de enviar email; se já existe, ignora.

### 5. Retry de SMTP no `EmailService` (com Circuit Breaker)

Quando o `notification-service` chama o SMTP, a chamada está protegida por **`@Retry` + `@CircuitBreaker`** do Resilience4j — ambos vinculados à mesma instância chamada `smtp` ([`EmailService.java:76-78`](../../notification-service/src/main/java/com/microservices/notification/service/EmailService.java)):

```java
@CircuitBreaker(name = "smtp")
@Retry(name = "smtp")
public void send(TodoEvent event) {
    // ...envia o email; pode lançar EmailDeliveryException / MailException / MessagingException
}
```

Config real em [`notification-service/application.yml`](../../notification-service/src/main/resources/application.yml):

```yaml
resilience4j.retry:
  instances:
    smtp:
      max-attempts: 3
      wait-duration: 200ms
      enable-exponential-backoff: true
      exponential-backoff-multiplier: 2     # 200ms → 400ms → 800ms entre tentativas
      retry-exceptions:
        - com.microservices.notification.exception.EmailDeliveryException
        - org.springframework.mail.MailException
        - jakarta.mail.MessagingException
```

**Não há `fallbackMethod`** — é design explícito. Se as 3 tentativas falharem, a exceção propaga pro `@SqsListener`, que **não acka** a mensagem. SQS reentrega; depois de `maxReceiveCount=3` no broker, a mensagem cai na DLQ. Combinar fallback que "absorve" o erro localmente quebraria essa cadeia — DLQ ficaria sempre vazia e mensagens podres ficariam invisíveis.

**Combinação Retry + Circuit Breaker** (ordem das anotações importa em Resilience4j: CB é o outermost, Retry é interno):

1. Chamada entra → CB CLOSED → Retry tenta até 3x → sucesso ou exception propaga.
2. Se as exceptions acumulam (≥50% de falha em 20 chamadas), CB **abre**.
3. Próximas chamadas → CB OPEN lança `CallNotPermittedException` **imediatamente**, sem nem entrar no Retry. **Fail-fast** evita retry storm contra dependência morta.
4. `CallNotPermittedException` **não** está na lista `retry-exceptions` — propaga direto, SQS reentrega, eventualmente cai na DLQ.
5. Depois de 30s, CB → HALF_OPEN → permite 3 chamadas de teste. Sucessos → CLOSED, falhas → OPEN de novo.

Detalhes em [`docs/conceitos/circuit-breaker.md`](../conceitos/circuit-breaker.md).

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
POST /todos
Idempotency-Key: 7f3a-8c12-...
Content-Type: application/json

{ "title": "...", "description": "...", "completed": false }
```

No projeto, isso está implementado em [`IdempotencyService.executeIdempotent`](../../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyService.java) e invocado pelo [`TodoController.create`](../../todo-service/src/main/java/com/microservices/todo/controller/TodoController.java) (esqueleto):

```java
public <T> T executeIdempotent(String idempotencyKey,
                               String operationFingerprint,   // ex: "POST /todos"
                               Object requestPayload,
                               Class<T> responseType,
                               Supplier<T> operation) {
    if (idempotencyKey == null) return operation.get();        // opt-in

    String requestHash = hash(operationFingerprint, requestPayload);  // SHA-256
    try {
        repository.insert(IdempotencyKey.builder()                    // claim atômico
                .key(idempotencyKey).requestHash(requestHash).build());
    } catch (DuplicateKeyException e) {
        return replayExisting(idempotencyKey, requestHash, responseType);  // 1ª chamada já passou
    }
    T response = operation.get();
    cacheResponseBestEffort(claim, response);                         // grava response cacheada
    return response;
}
```

Diferenças do "esquema básico" de Idempotency-Key:

- **Hash do payload**: a key sozinha não basta — também valida o SHA-256 do body. Mesma key + body diferente = `409 PAYLOAD_MISMATCH`. Protege contra erro de cliente (reusar key em request diferente).
- **Fingerprint da operação**: `"POST /todos"` entra no hash. Mesma key em endpoints diferentes não colide.
- **Claim atômico via `insert()`**: o índice único em `_id` resolve race entre duas chamadas simultâneas; quem chega primeiro grava o claim, quem chega depois cai no replay.
- **Liberação em falha**: se `operation.get()` lança, o claim é deletado — cliente pode retentar com a mesma key.
- **TTL**: a collection `idempotency_keys` tem TTL index em `expires_at` (Mongock V004) — Mongo apaga docs expirados em background sem job manual.

Próxima chamada com mesmo `Idempotency-Key` **e mesmo body** retorna o `Todo` anterior sem criar de novo. É o que conversamos em [`docs/conceitos/idempotencia.md`](../conceitos/idempotencia.md).

---

## Referências

- [`docs/sqs/dlq.md`](./dlq.md) — onde a mensagem para depois de N retries falharem.
- [`docs/sqs/visibility-timeout.md`](./visibility-timeout.md) — backoff entre tentativas no SQS.
- [`docs/sqs/outbox.md`](./outbox.md) — retry de publish (coluna `attempts`).
- [`.spec/01-issues/closed/idempotency.md`](../../.spec/01-issues/closed/idempotency.md) — design de idempotência no projeto.
- [Resilience4j — Retry](https://resilience4j.readme.io/docs/retry).
- [AWS Architecture Blog — Exponential Backoff and Jitter](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/).
