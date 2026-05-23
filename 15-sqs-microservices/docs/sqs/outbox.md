# Pattern — Transactional Outbox

Garantia de **atomicidade efetiva** entre escrita no banco e publicação em fila/topico. Resolve o problema de *dual-write* sem distributed transaction.

Implementado em [`todo-service`](../../todo-service) (commit do PR do outbox). Consumer dedupe complementar ja existia em [`notification-service`](../../notification-service) via `processed_messages`.

---

## Problema que resolve

Sequencia ingenua:

```java
repository.save(todo);                  // 1) banco
sqsTemplate.send(QUEUE_CREATED, event); // 2) fila
```

Sao **duas escritas em sistemas diferentes**, sem coordenacao. Falhas possiveis:

| Cenario | Resultado |
|---|---|
| Save OK, SQS cai antes do `send` | Banco tem o Todo, fila nao. **Evento perdido.** |
| Save OK, processo crasha entre 1 e 2 | Idem. |
| Save falha mas alguem inverte a ordem | Fila recebe evento de Todo que nao existe — evento fantasma. |

`@TransactionalEventListener` do Spring **nao resolve**: ele garante "rodar X depois do commit", mas se o processo morre no meio, X nunca acontece. Falta **durabilidade**.

---

## Ideia central

Transformar "publicar no SQS" em **uma escrita a mais no mesmo banco**, dentro da mesma transacao do save da entidade:

```
TX inicia
  ├─ repository.save(todo)              ┐
  ├─ outbox.save(event)                 │ atomico — ambos commitam
  └─ TX commita                          ┘ ou ambos rollback
```

Um processo **separado** (`@Scheduled`) le da tabela/collection `outbox_events` e publica no SQS, marcando como publicado em transacao distinta. Se o publish falhar, o evento fica pendente — proximo ciclo retenta.

**Garantia final**: se o Todo esta no banco, o evento *eventualmente* chega no SQS. Latencia controlada pelo `poll-interval` (2s no projeto).

---

## Componentes

### 1. Collection `outbox_events` (Mongo)

Mapeada em [`OutboxEvent.java`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java).

| Campo | Tipo | Para que serve |
|---|---|---|
| `_id` | UUID string | Chave do evento — vira `messageDeduplicationId` natural se migrar pra SQS FIFO |
| `aggregate_id` | string | ID do Todo (futuramente outras entidades) |
| `aggregate_type` | string | `"Todo"` |
| `event_type` | string | `CREATED` / `UPDATED` / `DELETED` |
| `destination` | string | Nome da fila SQS |
| `payload` | string (JSON) | `TodoEvent` serializado |
| `created_at` | datetime | Quando o evento foi gravado |
| `published_at` | datetime null | `null` = pendente, preenchido = publicado |
| `attempts` | int | Quantas tentativas de publish ja falharam |
| `last_error` | string null | Resumo do ultimo erro (truncado em 2000 chars) |
| `processing_node` | string null | Worker que detem o lease atual |
| `lease_expires_at` | datetime null | Quando o lease expira |

**Indice `published_at`** (via `@Indexed`): essencial para o publisher filtrar pendentes rapido.

A collection eh criada automaticamente no primeiro insert. Indices vem de `spring.data.mongodb.auto-index-creation: true` (lendo as anotacoes da entidade).

### 2. `OutboxService.record(...)`

[`OutboxService.java`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxService.java) — serializa o payload e insere o doc.

**Nao tem `@Transactional` proprio.** Roda dentro da TX do `TodoService` chamador (regra do anti-pattern: TX no service de negocio, nao em service de infra que apenas participa dela).

### 3. `TodoService` — fluxo de escrita

[`TodoService.java`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java) — todos os metodos de escrita sao `@Transactional` e chamam `outboxService.record(...)` em vez de `sqsTemplate.send(...)`. **`SqsTemplate` nao eh mais dependencia do `TodoService`** — soh do `OutboxPublisher`.

```java
@Transactional
public TodoResponseDTO create(TodoRequestDTO dto) {
    Todo todo = repository.save(...);
    outboxService.record(QUEUE_CREATED, todo.getId(), "CREATED", event);
    return mapper.toResponse(todo);
}
```

### 4. `OutboxPublisher` — scheduler

[`OutboxPublisher.java`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java).

```
@Scheduled(fixedDelay=2s)
publishPending():
   loop ate batchSize (50):
      event = repository.claimNext(nodeId, leaseDuration)
      if event == null: return
      self.publishOne(event)   // chamada via proxy

@Transactional(REQUIRES_NEW)
publishOne(event):
   try:
      sqsTemplate.send(event.destination, deserialize(event.payload))
      event.markPublished()
   catch e:
      event.markFailed(reason)
   repository.save(event)
```

Tres detalhes nao-obvios:

**a) Self-injection com `@Lazy`** — chamada `this.publishOne(...)` ignoraria `@Transactional`. A solucao eh injetar a propria classe e chamar `self.publishOne(...)`, passando pelo proxy do Spring. O `@Lazy` quebra o ciclo de dependencia na criacao do bean. Lombok `@RequiredArgsConstructor` **nao propaga** `@Lazy` pro parametro do construtor — por isso o construtor eh escrito a mao.

**b) `REQUIRES_NEW` por evento** — isola falhas: se um evento falha, o save do `attempts++` commita normalmente; nao rola back o lote inteiro.

**c) Lease pattern (Mongo)** — substitui `SELECT FOR UPDATE SKIP LOCKED` do Postgres. Implementado em [`OutboxEventRepositoryImpl.claimNext`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/repository/OutboxEventRepositoryImpl.java) via `findAndModify` atomico. Multiplos workers competem; cada doc soh eh entregue a um por vez. Lease com TTL evita pendentes presos se um worker crashar.

### 5. `@EnableScheduling`

Em [`TodoServiceApplication.java`](../../todo-service/src/main/java/com/microservices/todo/TodoServiceApplication.java). Sem isso o `@Scheduled` nao roda.

### 6. Configuracao

[`application.yml`](../../todo-service/src/main/resources/application.yml):

```yaml
outbox:
  poll-interval-ms: 2000      # quanto tempo entre ciclos
  batch-size: 50              # max eventos por ciclo
  lease-duration-ms: 30000    # TTL do lease (deve ser > tempo plausivel de publish)
```

---

## Fluxo end-to-end

```
POST /todos                     T+0.000s
  └─ TodoService.create [@TX]
       ├─ repository.save(todo)        ┐
       └─ outboxService.record(...)    ┘ commit atomico

@Scheduled OutboxPublisher       T+~2.000s
  ├─ claimNext()                       (lease pattern)
  ├─ sqsTemplate.send(QUEUE_CREATED, payload)
  └─ event.markPublished() + save     (REQUIRES_NEW)

SQS                              T+~2.001s

TodoEventListener (notification) T+~2.500s
  ├─ alreadyProcessed(messageId)?     (dedupe via processed_messages)
  └─ EmailService.send(...)
```

Latencia tipica do projeto: 2–3s entre POST e email. Aceitavel pra notificacao de Todo. Ajustavel via `outbox.poll-interval-ms`.

---

## O que da pra mudar e o que **nao** da

### Da pra mudar livre

- **Poll interval / batch size / lease duration** — sao puramente operacionais.
- **Adicionar tipos de evento** — basta `outboxService.record(NOVA_FILA, id, "NOVO_TIPO", payload)`.
- **Migrar pra outro destino** (Kafka, RabbitMQ, webhook) — soh o `OutboxPublisher` muda. `TodoService` continua igual.

### Cuidado redobrado

- **Mudar `@Transactional` do `TodoService`** — se cair, o outbox volta a ser dual-write quebrado.
- **Chamar `this.publishOne(...)` no publisher** — bypassa o proxy, perde `REQUIRES_NEW`, uma falha rola back tudo.
- **Remover o `@Lazy`** — circular dependency, app nao sobe.
- **Mexer no `lease_expires_at` manualmente** — pode liberar lease ativo e causar publish duplicado.

### Nao funciona

- **Replicar a logica com `@TransactionalEventListener`** — sem durabilidade, vai perder evento em crash.
- **Garantir exactly-once no consumer** — outbox eh **at-least-once**. Duplicacao acontece se o publisher publica mas crasha antes de marcar `published_at`. **O consumer precisa ser idempotente** (no projeto: dedupe via `processed_messages`).

---

## Sintomas de problema

| Sintoma | Causa provavel | Onde olhar |
|---|---|---|
| `outbox_events` cresce mas `published_at` fica `null` | Publisher nao roda. Verificar `@EnableScheduling` e log `[OUTBOX] publisher iniciado` | startup log do `todo-service` |
| `attempts` subindo em todos os eventos | SQS / LocalStack down. `last_error` mostra a causa | `db.outbox_events.find({attempts:{$gt:0}})` |
| Evento publicado mas notification nao recebe | Filtro do consumer ou nome da fila divergente | comparar `destination` no doc com `SqsConfig` do notification |
| Notification recebe evento 2x mas so 1 email | Dedupe funcionando (esperado). `processed_messages` tem entrada duplicada | logs do listener |
| Duas instancias do `todo-service`, publish duplicado | Lease nao implementado direito ou `lease-duration-ms` muito curto | revisar `claimNext` e o TTL configurado |

---

## Decisoes / trade-offs registrados

- **Lease em vez de SKIP LOCKED**: Mongo nao tem SKIP LOCKED. Lease com `findAndModify` atomico cumpre o mesmo papel — cada doc reivindicado por um worker so.
- **Sem backoff**: erro retenta no proximo ciclo (~2s). Simples. Se algum integrador real ficar fora por horas, ajustar pra `next_attempt_at` com backoff exponencial.
- **Retencao**: eventos publicados nao sao apagados. Util pra debug. Em producao, job de cleanup com `published_at < now() - N dias`. Divida registrada.
- **`payload` como JSON string, nao BSON**: nao consultamos o conteudo, soh deserializamos. JSON string eh mais simples e portavel.
- **Schema versionado via Mongock** (ver [`mongock.md`](./mongock.md)): indice em `outbox_events.published_at` eh criado pela `@ChangeUnit V001_BaselineIndexes` do `todo-service`. Anotacao `@Indexed` foi removida da entidade.

---

## Quando usar este pattern

**Use** quando:
- Voce escreve no banco **e** publica num broker/HTTP externo na mesma operacao logica.
- O sistema externo eh assincrono (fila, topico, webhook) — latencia de segundos eh aceitavel.
- Voce precisa de garantia de "se commitou no banco, o evento sai".

**Nao use** quando:
- A operacao precisa ser sincronamente confirmada pelo destinatario (use chamada HTTP transacional + saga, nao outbox).
- O destinatario nao tolera duplicatas e voce nao consegue garantir idempotencia nele.
- A latencia tem que ser sub-100ms — outbox adiciona pelo menos o `poll-interval`.

---

## Referencias

- Spec original de design (mais detalhado): [`01-issues/closed/outbox.md`](../01-issues/closed/outbox.md)
- Anti-pattern relacionado (TX + side-effect): [`02-anti-patterns/java-spring.md`](../02-anti-patterns/java-spring.md) §Transacoes
- Consumer dedupe (par natural do outbox): `notification-service/.../ProcessedMessageRepositoryImpl.java`
