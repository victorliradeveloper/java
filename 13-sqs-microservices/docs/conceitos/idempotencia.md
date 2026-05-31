# Idempotência

Propriedade de uma operação que pode ser executada **N vezes** com o mesmo efeito de ter sido executada **1 vez**. Em sistemas distribuídos, é o que permite **retry seguro** — sem ela, retry vira duplicação.

Este projeto implementa idempotência em **3 camadas independentes**:

| Camada | Onde | Mecanismo |
|---|---|---|
| HTTP — POST `/todos` | [`TodoController`](../../todo-service/src/main/java/com/microservices/todo/controller/TodoController.java) + [`IdempotencyService`](../../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyService.java) | `Idempotency-Key` header (Stripe-style) |
| HTTP — PUT/DELETE | [`TodoService.update`/`delete`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java) | Diff antes/depois (PUT) + `findById.ifPresent` (DELETE) |
| Mensageria — consumers SQS | [`TodoEventListener`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java) e [`TodoEventAuditListener`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoEventAuditListener.java) | Dedupe via `messageId` (Mongo unique constraint) |

---

## Por que importa em microservices

Em monolito, vc chama um método, ele executa. Falhou? Erro. Não falhou? Sucesso. Determinístico.

Em microservices, todo request passa por **rede**, e rede tem 3 estados, não 2:

```
1. Sucesso confirmado (request chegou, resposta voltou)
2. Falha confirmada (timeout, erro, resposta voltou)
3. INCERTEZA (timeout SEM resposta — não sabe se chegou)
```

O caso (3) é o que quebra tudo. Cliente não sabe se a operação aconteceu. Se ele **retentar**, pode duplicar. Se ele **desistir**, pode ter perdido. **Idempotência transforma o caso (3) em algo seguro de retentar.**

Exemplos do caso (3) que matam sistemas sem idempotência:

| Cenário | Sem idempotência | Com idempotência |
|---|---|---|
| Frontend timeout no POST /payment, usuário aperta de novo | Cobra 2x | Cobra 1x, retorna mesma resposta |
| SQS reentrega mesma mensagem (at-least-once) | Manda 2 emails | Manda 1 email |
| Service A chama B, B responde mas A não recebe → A retenta | B executa 2x | B detecta retry, retorna resposta anterior |
| K8s reinicia pod no meio do request | Operação em estado indeterminado | Próxima tentativa converge |

---

## Os 3 níveis de idempotência

### Nível 1 — Idempotência natural por verbo HTTP

Alguns verbos **já têm contrato** de idempotência na spec do HTTP:

| Verbo | Idempotente? | Por contrato |
|---|---|---|
| `GET /todos/123` | ✅ | Não muda estado |
| `PUT /todos/123` | ✅ | Sobrescreve — chamar N vezes = mesmo estado final |
| `DELETE /todos/123` | ✅ | Remove — segundo DELETE deve retornar 204 (não 404) |
| `POST /todos` | ❌ | Cria um novo recurso — cada chamada gera um ID diferente |

Implementação no projeto ([`TodoService.java`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java)):

**DELETE** usa `findById(id).ifPresent(...)` — segunda chamada não lança `TodoNotFoundException`, retorna 204 silenciosamente. Cliente que retentou após timeout não recebe 404 confuso.

**PUT** captura snapshot antes/depois via `TodoSnapshot.from(todo)`. Se nada mudou, não bumpa `updatedAt` nem publica evento `UPDATED`:
```java
if (!before.equals(after)) {
    todo.setUpdatedAt(LocalDateTime.now());
    outboxService.record(..., "UPDATED", ...);
}
```
Sem isso, 100 PUTs no-op gerariam 100 eventos UPDATED na fila — desperdício e ruído.

### Nível 2 — Idempotência forçada via `Idempotency-Key`

`POST` não é idempotente por contrato. Pra fazer ficar, o cliente coopera mandando um header único:

```
POST /todos
Idempotency-Key: 7f3a-8c12-4d56-bcde
Content-Type: application/json

{"title":"comprar leite"}
```

O servidor:
1. Vê a key e calcula hash do payload.
2. Tenta gravar `(key, hash, ...)` na collection `idempotency_keys`.
3. Se inseriu → executa criação, cacheia resposta.
4. Se já existia → retorna resposta cacheada (se hash bate) ou 409 (se cliente reusou key em payload diferente).

**Resultado**: cliente que retentou após timeout com mesma key recebe **exatamente a mesma resposta**, sem criar segundo Todo. É o padrão usado por Stripe, PayPal, Shopify, GitHub API.

Implementação detalhada abaixo em §"POST /todos — Idempotency-Key".

### Nível 3 — Idempotência no consumer de mensageria

SQS (e Kafka, RabbitMQ) entregam **at-least-once**: a mesma mensagem pode chegar 2x ao consumer. Causa típica:

```
T+0s   consumer recebe msg
T+25s  consumer ainda processando, faltam 5s pra terminar
T+30s  visibility timeout expira (default 30s)
T+30s  SQS reentrega a msg pra outro consumer
T+31s  primeiro consumer termina e tenta deletar — mas o segundo já tá processando
```

Sem dedupe, o efeito do processamento (manda email, grava log de auditoria, processa pagamento) acontece 2x. Implementação no projeto: cada consumer grava o `messageId` antes do side-effect; se o `messageId` já existe, ignora.

Detalhes em §"Consumers SQS — Dedupe via messageId".

---

## POST /todos — Idempotency-Key

Implementação Stripe-style em [`IdempotencyService.java`](../../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyService.java).

### Fluxo end-to-end

```
POST /todos                                      T+0.000s
Header: Idempotency-Key: ABC
Body:   {"title":"comprar leite"}

  └─ TodoController.create
       └─ idempotencyService.executeIdempotent(
             "ABC", "POST /todos", dto, TodoResponseDTO.class,
             () -> todoService.create(dto)
          )
            │
            ├─ validateKey("ABC")                ─→  ASCII imprimível, ≤ 255 chars
            ├─ hash = SHA-256("POST /todos\n{...}")
            ├─ MDC.put("idempotencyKey", "ABC")
            │
            ├─ try repository.insert(claim):     ─→  unique index em _id é o lock atômico
            │   │
            │   ├─ SUCESSO (claim novo):
            │   │   ├─ operation.get()           ─→  todoService.create(dto) — @Transactional
            │   │   │     ├─ save(todo)           ┐
            │   │   │     └─ outbox.record(...)   ┘ commit atômico (pattern outbox)
            │   │   │
            │   │   ├─ cacheResponseBestEffort:
            │   │   │     ├─ claim.markCompleted(201, json)
            │   │   │     └─ repository.save(claim)
            │   │   │           (falha aqui → loga ERROR, retorna 201 normal)
            │   │   │
            │   │   └─ return response           ─→  201 Created
            │   │
            │   └─ DuplicateKeyException (claim já existia):
            │       └─ replayExisting:
            │             ├─ hash mismatch → 409 PAYLOAD_MISMATCH
            │             ├─ response ainda null → 409 IN_PROGRESS
            │             └─ response cacheada → desserializa e retorna 201
            │
            └─ finally MDC.remove("idempotencyKey")
```

### Componentes

#### 1. Entity [`IdempotencyKey`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/IdempotencyKey.java)

Collection `idempotency_keys`:

| Campo | Tipo | Função |
|---|---|---|
| `_id` (`key`) | string | Header `Idempotency-Key` enviado pelo cliente — chave natural |
| `request_hash` | string | SHA-256 de `"<fingerprint>\n<json>"` — detecta reuso de key com payload diferente |
| `response_status` | int / null | `null` enquanto a operação está rodando; preenchido após sucesso |
| `response_body` | string / null | JSON serializado da resposta — pra replay |
| `created_at` | datetime | Quando o claim foi feito |
| `expires_at` | datetime | TTL — Mongo apaga doc automaticamente após esse instante |

#### 2. TTL index ([`V004_IdempotencyKeyIndexes`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V004_IdempotencyKeyIndexes.java))

```java
mongoTemplate.indexOps("idempotency_keys")
    .ensureIndex(new Index().on("expires_at", Sort.Direction.ASC).expire(0));
```

`expireAfterSeconds=0` **não significa** "expira imediatamente". Significa **"expira quando o campo `expires_at` for menor que `now()`"**. É o idiom canônico do MongoDB pra TTL por valor de campo (não por delta de criação). Substitui job de cleanup manual.

#### 3. [`IdempotencyService`](../../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyService.java)

Wrapper **genérico, reutilizável em qualquer endpoint**. Assinatura:

```java
public <T> T executeIdempotent(
        String idempotencyKey,
        String operationFingerprint,    // ex: "POST /todos"
        Object requestPayload,
        Class<T> responseType,
        Supplier<T> operation)
```

Pontos de design importantes:

**a) Atomic claim via `DuplicateKeyException`** — `repository.insert(claim)` lança quando o `_id` colide. Idiom equivalente ao `INSERT ... ON CONFLICT DO NOTHING` do Postgres e `SETNX` do Redis. **Único mecanismo seguro** contra race condition entre o "verifica se existe" e o "insere" — fazer dois passos separados quebra. Mesmo padrão do `audit-service` ([ver `TodoEventAuditListener`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoEventAuditListener.java)).

**b) Hash inclui fingerprint da operação** — `"POST /todos\n{...}"` em vez de só `"{...}"`. Previne que o cliente reuse a mesma key em endpoints diferentes (`POST /todos` vs `POST /payments`) e ganhe replay errado.

**c) Cache best-effort após sucesso** — se `repository.save(claim)` falhar APÓS a operação ter sucesso, o erro é logado mas **não propaga**. Cliente recebe 201 normalmente. Trade-off documentado: retry com a mesma key dentro da janela TTL pode receber 409 `IN_PROGRESS`, mas isso é muito melhor que retornar 500 com o recurso já criado (cliente nunca saberia que o Todo existe).

**d) Cleanup do claim em falha da operação** — se `operation.get()` lança, o claim é deletado. Cliente pode retentar com a mesma key. Trade-off vs Stripe (que cacheia o erro): mais permissivo, melhor pra falhas transientes em ambiente interno; pior pra auditoria.

**e) MDC com `idempotencyKey`** — propaga a key em todos os logs do request (estrutura compatível com Logstash / Datadog).

#### 4. Resposta estruturada via [`ProblemDetail`](../../todo-service/src/main/java/com/microservices/todo/exception/GlobalExceptionHandler.java) (RFC 7807)

Cliente recebe erro estruturado, programável:

```json
{
  "type": "about:blank",
  "title": "Idempotency-Key conflict",
  "status": 409,
  "detail": "Idempotency-Key 'ABC' rejeitada: payload difere do enviado na primeira chamada com esta key",
  "code": "PAYLOAD_MISMATCH",
  "idempotencyKey": "ABC"
}
```

`code` é enum: `PAYLOAD_MISMATCH`, `IN_PROGRESS`, `INVALID_KEY`. Cliente switch sobre `code`, não sobre regex em `detail`.

#### 5. Configuração ([`application.yml`](../../todo-service/src/main/resources/application.yml))

```yaml
idempotency:
  key-ttl: 24h         # janela de retry — Stripe usa 24h
  max-key-length: 255  # Stripe spec
```

`Duration` parseado por Spring (`24h`, `90m`, `7d` aceitos).

---

## Consumers SQS — Dedupe via messageId

SQS Standard é **at-least-once**. Mesmo com idempotência perfeita no publisher (via outbox), o consumer pode receber a mesma mensagem 2x. Dedupe é responsabilidade do consumer.

### Padrão 1 — Collection separada (notification-service)

[`TodoEventListener.alreadyProcessed`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java):

```java
boolean inserted = processedMessageRepository.tryInsert(messageId.toString());
if (!inserted) {
    log.info("[DEDUPE] mensagem duplicada descartada messageId={}", messageId);
    return;
}
emailService.send(event);
```

Collection `processed_messages` com `messageId` como `_id`. `tryInsert` usa `MongoTemplate.upsert` com `$setOnInsert` (equivalente a `INSERT ... ON CONFLICT DO NOTHING`). Retorna `true` se inseriu (mensagem nova), `false` se já existia.

**Ordem de operações** (insert-antes-do-send):
- Email falha após insert → próxima entrega é descartada como duplicada → **perde raro** ✓
- Email sucesso, crash antes do return → próxima entrega é descartada → 1 email enviado ✓

Alternativa "insert-depois-do-send" daria **duplica raro** em vez de **perde raro**. Decisão registrada em [`.spec/01-issues/closed/idempotency.md`](../../.spec/01-issues/closed/idempotency.md) §1.3 — pra email transacional, "perde raro" é o trade-off escolhido.

### Padrão 2 — `_id = messageId` na própria collection de domínio (audit-service)

[`TodoEventAuditListener.onTodoEvent`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoEventAuditListener.java):

```java
TodoAuditLog log = TodoAuditLog.builder()
    .id(messageId.toString())   // _id = messageId — dedupe natural
    .aggregateId(event.todoId())
    ...
    .build();
try {
    repository.insert(log);
} catch (DuplicateKeyException e) {
    // já processei essa msg, ack normal
}
```

Mais barato e correto que tabela auxiliar — o próprio insert é a verificação atômica. Funciona porque audit log é append-only por natureza.

**Quando usar cada padrão**:
- Collection de domínio cresce de forma controlada e tem chave natural compatível → **Padrão 2** (audit).
- Collection de domínio tem chave natural distinta (`email_id`, `payment_id`) e ler/dedupar exigiria query separada → **Padrão 1** (notification).

---

## Por que idempotência é pré-requisito do `retry`

Retry sem idempotência **vira bug**. Olha o que acontece:

```
1ª tentativa: cliente manda POST → server cria todo (id=X) → resposta perde no caminho
2ª tentativa: cliente retenta    → server cria todo (id=Y) → resposta chega
```

Cliente acha que tem 1 todo. Server tem 2. Estado divergente, "fantasma" no DB.

Com `Idempotency-Key`:

```
1ª: POST + Key=ABC → cria todo X, cacheia → resposta perde
2ª: POST + Key=ABC → claim já existe, retorna resposta cacheada → cliente vê X
```

Estado convergente.

**Regra geral**: nunca configure retry (Resilience4j, SQS `maxReceiveCount`, frontend retry) em operação não-idempotente. Detalhes em [`docs/sqs/retry.md`](../sqs/retry.md).

---

## Trade-offs registrados no projeto

| Decisão | Alternativa | Por que escolhemos esta |
|---|---|---|
| `Idempotency-Key` **opcional** (`required=false`) | Header obrigatório | Mantém compatibilidade com clientes que não cooperam. REST não exige idempotência em POST. |
| Cache **só de sucesso** (delete claim em falha) | Cache de erro também (Stripe-style) | Mais permissivo pra falhas transientes em ambiente interno. Stripe-style requer mapear exceções → status code/body, fora do escopo aqui. |
| Cache `best-effort` (não falha resposta se `save(claim)` quebrar) | Atomic ou rollback | Operação já sucedeu — cliente precisa do 201. Retry pode bater em 409 `IN_PROGRESS`, mas isso é muito melhor que perder a confirmação do recurso criado. |
| TTL = **24h** | 1h / 7d / configurável por endpoint | Padrão Stripe. Configurável via `idempotency.key-ttl`. |
| SQS Standard + dedupe no consumer | SQS FIFO com `messageDeduplicationId` | Standard tem throughput maior. FIFO dedupe é janela de 5min só. Consumer dedupe cobre 100%. |
| Notification: dedupe **antes** do email | Dedupe **depois** | Aceita "perde raro" em vez de "duplica raro" — email transacional é mais tolerante a perda que a duplicação. |
| Audit: `_id = messageId` natural | Collection separada | Append-only — insert duplicado é a verificação atômica. Sem tabela extra. |

---

## Pegadinhas comuns

| Pegadinha | Sintoma | Como evitar |
|---|---|---|
| `DuplicateKeyException` **não funciona dentro de transação Mongo** | TX aborta inteira em vez de cair no `catch` | Fazer o `insert(claim)` **fora** de `@Transactional` — exatamente o que o `IdempotencyService` faz |
| Hash só do body | Mesma key em endpoints diferentes (`POST /todos` vs `POST /payments`) colide | Hashear `method + path + body` (o que o `fingerprint` faz) |
| Cliente reusa mesma key em payloads diferentes | Sistema retorna resposta antiga, payload novo nunca processado | 409 `PAYLOAD_MISMATCH` — força o cliente a perceber o bug |
| TTL muito curto (ex: 5min) | Cliente legítimo retenta após 6min → cria duplicata | 24h é o sweet-spot (Stripe). Configurável. |
| Dedupe no consumer **depois** do side-effect | Crash entre o side-effect e o insert do `messageId` → duplica | Dedupe **antes** do side-effect (decisão atual do notification). |
| Self-invocation de método `@Transactional` | Proxy bypassed, idempotência funciona mas TX não | Bean separado (como `IdempotencyService` → `TodoService`) ou self-injection com `@Lazy` |
| Idempotência sem retry | Sistema só "está pronto" pra retry, mas ninguém retenta | Combinar com Resilience4j (HTTP) e DLQ + visibility timeout (SQS) |

---

## Quando aplicar cada nível

| Tipo de operação | Solução |
|---|---|
| `GET` | Não precisa — idempotente por contrato |
| `PUT` / `DELETE` | Comportamento natural — só garantir que server não lança 404 em DELETE de recurso ausente |
| `POST` que cria recurso | **`Idempotency-Key`** (Stripe-style) |
| `POST` que dispara side-effect externo (cobrança, envio de email) | **`Idempotency-Key`** + dedupe natural do side-effect (transação ID) |
| Consumer de fila / topic | **Dedupe via messageId** (Padrão 1 ou 2 conforme caso) |
| Job batch agendado | Cuidado especial: rodar 2x não pode duplicar. Usar lease pattern (ver outbox) |

---

## Como explicar em entrevista

**Pergunta clássica**: *"Cliente manda `POST /payment`, request dá timeout. Usuário aperta de novo. Como garantir que não cobra 2x?"*

Resposta em 3 frases:
1. **Cliente coopera mandando `Idempotency-Key`** — UUID que ele gera antes do POST.
2. **Server faz claim atômico** numa collection de keys (unique index no `_id` previne race). Se já existe, retorna resposta cacheada.
3. **TTL automático** apaga keys antigas (24h Stripe-style). Conjuntamente: dedupe no consumer da fila de cobrança por `messageId`, e operações de update/delete naturalmente idempotentes.

**Pergunta de follow-up**: *"E se o claim sucede mas o pagamento falha?"*

Resposta:
- Stripe-style: cacheia o erro. Retry retorna mesmo erro.
- Implementação deste projeto: deleta o claim. Retry com mesma key processa de novo. **Trade-off consciente** — mais permissivo, menos auditável.

---

## Referências

- [`docs/sqs/retry.md`](../sqs/retry.md) — retry é o consumidor #1 de idempotência. Sem ela, retry vira bug.
- [`docs/sqs/outbox.md`](../sqs/outbox.md) — outbox garante "evento sai do publisher exactly-once-do-DB"; consumer idempotente fecha pra "side-effect roda exactly-once efetivo".
- [`docs/sqs/dlq.md`](../sqs/dlq.md) — DLQ depende de idempotência do consumer pra ser segura (`maxReceiveCount=3` é retry implícito).
- [`.spec/01-issues/closed/idempotency.md`](../../.spec/01-issues/closed/idempotency.md) — punch list original + decisões de design + status de implementação.
- [Stripe — Idempotent Requests](https://docs.stripe.com/api/idempotent_requests)
- [IETF draft — Idempotency-Key HTTP Header Field](https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-idempotency-key-header-06)
- [RFC 7807 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc7807)
