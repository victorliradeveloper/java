# Fluxo das Mensagens (End-to-End)

## Visão Geral

Este doc descreve o caminho completo de uma mensagem no projeto — desde o
`POST /todos` que sai do cliente até o `INSERT` na tabela de auditoria e o
e-mail saindo pelo SMTP. A intenção é colar **as peças que os outros docs
descrevem isoladas** (gateway, outbox, exchange, consumers, DLQ) num único
fio narrativo.

A topologia geral (referência rápida):

```
                        ┌──────────────────┐
                        │   API Gateway    │  rate limit (Redis), Eureka discovery
                        │      :8090       │
                        └────────┬─────────┘
                                 │ HTTP
                                 ▼
                        ┌──────────────────┐
                        │   todo-service   │  controller -> service -> outbox
                        │      :8081       │  (TX unica: entity + outbox)
                        └────────┬─────────┘
                                 │ scheduler (2s)
                                 ▼
                       ┌────────────────────┐
                       │   OutboxPublisher  │  claim com lease + publish
                       └────────┬───────────┘
                                │ AMQP basic.publish
                                ▼
                       ┌────────────────────┐
                       │   todo.exchange    │  (topic) — roteamento via binding
                       └─┬──────┬──────┬──┬─┘
                         │      │      │  └──────────────────────────┐
                         ▼      ▼      ▼                             ▼
                   created  updated  deleted                    audit
                   .queue   .queue   .queue                     .queue
                         │      │      │                             │
                         ▼      ▼      ▼                             ▼
                  ┌──────────────────────────┐         ┌──────────────────────┐
                  │  notification-service    │         │   audit-service      │
                  │  :8082                   │         │   :8083              │
                  │  dedupe + CB + retry     │         │   dedupe via PK      │
                  │  e-mail SMTP             │         │   INSERT append-only │
                  └──────────────────────────┘         └──────────────────────┘
                              │                                       │
                              │ fail >3x                              │ fail >3x
                              ▼                                       ▼
                      ┌─────────────────┐                  ┌─────────────────────┐
                      │ todo.dlx (topic)│                  │ todo.audit.dlx (top)│
                      └──┬───────┬──┬───┘                  └──────────┬──────────┘
                         ▼       ▼  ▼                                 ▼
                      created  upd  del                          audit.dlq
                      .dlq    .dlq .dlq
```

Os arquivos que cobrem cada peça em profundidade: [`publisher.md`](./publisher.md),
[`consumer.md`](./consumer.md), [`exchange.md`](./exchange.md),
[`filas.md`](./filas.md), [`dlq.md`](./dlq.md). Aqui o foco é o **fluxo**, não
o componente.

---

## Os 9 atos do fluxo

Vou seguir o caminho de **uma única requisição** `POST /todos` no caso feliz.
Variações (UPDATE, DELETE, falha, dedupe) aparecem ao longo nas seções de
"variações".

### Ato 1 — Cliente bate no Gateway

Cliente (Postman, frontend, curl) faz:

```http
POST http://localhost:8090/todos
Content-Type: application/json
Idempotency-Key: 4d3a9a02-...        ← opcional
X-Forwarded-For: 187.x.x.x           ← se vier de proxy

{"title":"estudar fluxo","description":"trace ponta a ponta"}
```

O gateway resolve **três coisas** antes de repassar:

1. **Rate limit** (`RequestRateLimiter`): 10 req/s, burst 20, chave = IP do
   cliente (extraído via `KeyResolver` que prioriza `X-Forwarded-For`).
   Estado vive no Redis — então funciona com gateway escalado horizontalmente.
2. **Discovery**: a rota `/todos/**` aponta pra `lb://todo-service`. O
   `LoadBalancerClient` consulta o cache local da Eureka e escolhe uma
   instância saudável de `todo-service` registrada como `TODO-SERVICE`.
3. **Repasse**: HTTP request vai pra `http://<ip-do-pod>:8081/todos` com
   headers preservados (incluindo `Idempotency-Key`).

Estágio determinístico: nada de mensagens, ainda. Falha aqui = `429` (rate
limit) ou `503` (sem instância saudável de `todo-service`).

> Cliente pode bater direto em `localhost:8081` em dev — pula gateway, pula
> rate limit. Em prod o caminho **sempre** é via gateway.

---

### Ato 2 — Idempotência (apenas POST)

Antes do controller chamar o service, o
[`IdempotencyService`](../../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyService.java)
intercepta:

[`TodoController.java:28-40`](../../todo-service/src/main/java/com/microservices/todo/controller/TodoController.java#L28-L40)

```java
TodoResponseDTO body = idempotencyService.executeIdempotent(
        idempotencyKey,
        CREATE_FINGERPRINT,     // "POST /todos"
        dto,
        TodoResponseDTO.class,
        () -> service.create(dto)
);
```

Três cenários:

| Header `Idempotency-Key` | Comportamento |
|---|---|
| Ausente | Bypass — executa a lambda direto, sem cache |
| Presente, **primeira vez** | Executa, serializa o response, guarda na tabela `idempotency_keys` com TTL 24h |
| Presente, **repetido** com mesmo body | NÃO executa — retorna o response cacheado do registro anterior |
| Presente, **repetido** com body diferente | `409 Conflict` (mesma key + payload diferente é violação de contrato) |

O fingerprint `"POST /todos"` previne que a mesma key seja reusada
acidentalmente em endpoint diferente (ex.: cliente reaproveita key entre
`/todos` e `/orders` por bug). O hash combina key + fingerprint + payload.

> Se a `Idempotency-Key` for uma das já cacheadas, o fluxo **encerra
> aqui** — sem chamar `service.create`, sem novo outbox, sem novo evento
> publicado. O cliente recebe **exatamente** o mesmo body de antes. Ato 3 em
> diante é pulado.

---

### Ato 3 — Transação de domínio (atomic outbox)

Aqui está o coração do "não perder mensagem".

[`TodoService.java:36-52`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java#L36-L52)

```java
@Transactional
public TodoResponseDTO create(TodoRequestDTO dto) {
    Todo todo = repository.save(mapper.toEntity(dto));        // INSERT em todos
    TodoResponseDTO response = mapper.toResponse(todo);
    outboxService.record(                                      // INSERT em outbox_events
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_CREATED,
            response.id(),
            "Todo",
            "CREATED",
            TodoEvent.of(response.id(), response.title(), "CREATED")
    );
    return response;
}
```

A propriedade essencial: **as duas inserções vivem na mesma transação
JDBC**. O
[`OutboxService.record`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxService.java#L34-L54)
propositalmente **não** tem `@Transactional` próprio — herda a TX externa
do caller. Se abrisse TX nova, os dois inserts poderiam divergir.

Resultado prático: ou as duas linhas aparecem juntas no commit, ou nenhuma
aparece. **Não existe estado intermediário "todo salvo mas evento perdido"**.

Conteúdo persistido na `outbox_events`:

| Coluna | Valor |
|---|---|
| `id` | UUID gerado no `OutboxService` (vira o `messageId` AMQP depois) |
| `aggregate_id` | UUID do todo |
| `aggregate_type` | `"Todo"` |
| `event_type` | `"CREATED"` |
| `exchange` | `"todo.exchange"` |
| `routing_key` | `"todo.created"` |
| `payload` | JSON do `TodoEvent` (serializado com Jackson, mesmo formato que o publish enviará) |
| `created_at` | now |
| `published_at` | `NULL` (preenchido depois do publish) |
| `attempts` | `0` |
| `next_attempt_at` | `NULL` (preenchido em retry após falha) |
| `processing_node` / `lease_expires_at` | `NULL` (preenchido durante claim) |

A rota (exchange + routing key) **vira parte do fato persistido**. Isso
significa que mudar a rota no código fonte não retroage em eventos que
estavam pendentes — eles publicam no destino que foi gravado no momento do
`outbox.record`. Migrações de roteamento precisam tratar essa cauda.

> Decisão de routing key acontece **na transação do domínio**, antes do
> commit. Não é o publisher que decide pra onde mandar; ele só executa o
> que a TX original gravou.

---

### Ato 4 — HTTP response volta ao cliente

Depois do `commit`, o controller serializa o `TodoResponseDTO` e responde
**`201 Created`**. Ponto crítico:

> **O evento ainda não foi publicado no RabbitMQ.** Só foi **gravado no
> outbox**. A publicação acontece de forma assíncrona em até ~2s no Ato 5.

Isso é assumido como aceitável — o "evento de domínio" é um detalhe interno;
o que importa pro cliente é que o Todo foi criado, e isso já é fato no banco.
Padrões mais síncronos (publish dentro da TX, transação distribuída,
2PC) trazem complexidade desproporcional ao ganho.

O cliente vê:

```json
HTTP/1.1 201 Created
Location: /todos/486c3c4d-...
Content-Type: application/json

{"id":"486c3c4d-...","title":"estudar fluxo","completed":false,"createdAt":"..."}
```

A partir daqui o cliente está livre. O resto do fluxo é interno.

---

### Ato 5 — OutboxPublisher claim com lease

[`OutboxPublisher.java:68-78`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java#L68-L78)

```java
@Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
public void publishPending() {
    Duration lease = Duration.ofMillis(properties.leaseDurationMs());
    for (int i = 0; i < properties.batchSize(); i++) {
        Optional<OutboxEvent> claimed = repository.claimNext(nodeId, lease);
        if (claimed.isEmpty()) {
            return;
        }
        self.publishOne(claimed.get());
    }
}
```

A cada **2s** (poll-interval-ms), uma thread `@Scheduled` corre. Pra cada
slot do batch (até 50 por ciclo):

1. **`claimNext(nodeId, lease)`** — SQL com `FOR UPDATE SKIP LOCKED` que:
   - Procura linha com `published_at IS NULL` AND (`next_attempt_at IS NULL`
     OR `next_attempt_at <= now`).
   - Filtra também as que **outro nó já claimou** (`processing_node IS NULL`
     OR `lease_expires_at < now`).
   - Marca `processing_node = nodeId` e `lease_expires_at = now + 30s`.
   - Retorna a linha pra esse worker.

   O `nodeId` é gerado uma vez no `@PostConstruct` (`hostname-UUID`),
   garantindo unicidade por instância da app. O `SKIP LOCKED` permite N
   instâncias do `todo-service` rodando em paralelo sem se atropelar — cada
   uma pega linhas diferentes.

2. **`self.publishOne(claimed)`** — chamada via proxy (`@Lazy` no
   construtor) pra forçar passagem por `@Transactional(REQUIRES_NEW)`.
   Cada evento é publicado **isoladamente** — uma falha não invalida o
   batch.

> Se o app morre depois do claim mas antes do publish, a `lease_expires_at`
> protege: depois de 30s a linha "destrava" automaticamente e outro
> worker (ou o próprio depois do restart) re-claima. **Lease-based
> ownership** dispensa coordenação externa (ZooKeeper, Redis lock,
> etc.) — o próprio banco faz o papel.

---

### Ato 6 — Publish AMQP

[`OutboxPublisher.java:83-102`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java#L83-L102)

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void publishOne(OutboxEvent event) {
    try {
        TodoEvent payload = objectMapper.readValue(event.getPayload(), TodoEvent.class);
        rabbitTemplate.convertAndSend(event.getExchange(), event.getRoutingKey(), payload, msg -> {
            msg.getMessageProperties().setMessageId(event.getId());
            return msg;
        });
        event.markPublished();
    } catch (Exception e) {
        event.markFailed(truncate(e.toString()), backoffPolicy::nextAttemptAt);
    }
    repository.save(event);
}
```

O `convertAndSend` por baixo faz:

1. `Jackson2JsonMessageConverter` serializa o `TodoEvent` em **JSON UTF-8**.
2. Anexa headers AMQP cruciais:
   - **`content_type: application/json`**
   - **`__TypeId__: com.microservices.todo.event.TodoEvent`** — FQN da classe
     no publisher. Consumer usa pra resolver classe local (ver Ato 8).
   - **`message_id: <outbox.id>`** — UUID da linha do outbox. É a chave que
     viabiliza dedupe nos consumers.
   - **`delivery_mode: 2`** (persistent) — RabbitTemplate seta por default.
3. Envia frame `basic.publish` pro broker, endereçado à exchange
   `todo.exchange` com routing key `todo.created`.

Caminho feliz: o broker aceita, retorna sem exceção, `event.markPublished()`
seta `published_at = now`. Próxima passagem do scheduler ignora essa linha.

Caminho com falha (Rabbit fora, network glitch, serialização inválida):

- Exceção pega no catch → `event.markFailed(...)` incrementa `attempts` e
  agenda `next_attempt_at = now + backoff(attempts)`.
- Backoff: exponencial com jitter, 2s → 4s → 8s → 16s → 32s → 60s (capped).
- Próximo ciclo do scheduler considera essa linha novamente quando o
  `next_attempt_at` passar.
- A linha **nunca é descartada** — fica retentando indefinidamente. (Em
  produção real, monitorar `attempts > N` e alarmar.)

> **Por que `REQUIRES_NEW`?** Cada `publishOne` é uma transação curta e
> isolada. Se o batch tem 50 eventos e o 30º falha, os 29 anteriores já
> commitaram individualmente — não rola back nada. Sem `REQUIRES_NEW`, uma
> falha invalidaria o batch inteiro (incluindo o claim).

---

### Ato 7 — Roteamento na exchange

A `todo.exchange` é do tipo **topic**. Recebeu uma mensagem com routing
key `todo.created`. O broker varre **todos os bindings declarados** nessa
exchange e pra cada um:

| Binding | Padrão | Casa com `todo.created`? | Resultado |
|---|---|---|---|
| `todo.created.queue` ← `todo.created` | exato | ✓ | cópia entregue |
| `todo.updated.queue` ← `todo.updated` | exato | ✗ | ignorado |
| `todo.deleted.queue` ← `todo.deleted` | exato | ✗ | ignorado |
| `todo.audit.queue` ← `todo.#` | wildcard | ✓ | cópia entregue |

**Resultado: duas cópias da mensagem** — uma em `todo.created.queue` (pro
notification) e outra em `todo.audit.queue` (pro audit). Cada cópia carrega
**os mesmos headers**, incluindo o `message_id`. É isso que permite dedupe
independente em cada consumer.

> O publisher fez **um único `basic.publish`**. A duplicação de cópias
> acontece dentro do broker, durante o roteamento — sem custo adicional pro
> publisher.

Detalhes completos do roteamento em [`exchange.md`](./exchange.md) e
[`filas.md`](./filas.md).

---

### Ato 8 — Consumer notification (dedupe + retry + e-mail)

O `notification-service` tem **três `@RabbitListener`** (um por fila), todos
delegando pro mesmo método privado `process(event, messageId)`. Vou seguir
a cópia que caiu em `todo.created.queue`.

[`TodoEventListener.java:47-51`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java#L47-L51)

```java
@RabbitListener(queues = RabbitMQConfig.QUEUE_CREATED)
public void onTodoCreated(TodoEvent event,
                          @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    process(event, messageId);
}
```

Antes do método rodar, o Spring AMQP container faz:

1. **Pega a mensagem** da fila via `basic.consume` (modelo push — o broker
   empurra; o consumer não faz poll).
2. **Desserialização**: `Jackson2JsonMessageConverter` lê o header
   `__TypeId__`. Como o FQN no header é
   `com.microservices.todo.event.TodoEvent` (do publisher) e a classe local
   é `com.microservices.notification.event.TodoEvent`, o
   `DefaultJackson2JavaTypeMapper` mapeia via `idClassMapping` configurado
   em [`RabbitMQConfig`](../../notification-service/src/main/java/com/microservices/notification/config/RabbitMQConfig.java):

   ```java
   typeMapper.setIdClassMapping(Map.of(
       "com.microservices.todo.event.TodoEvent", TodoEvent.class
   ));
   ```

   Sem esse mapeamento, o Spring AMQP 3.x recusaria a desserialização por
   segurança (whitelisting de classes "trusted"). Mesma estratégia no
   [`RabbitMQConfig`](../../audit-service/src/main/java/com/microservices/audit/config/RabbitMQConfig.java)
   do audit.

3. **Invoca `process(event, messageId)`** com o `TodoEvent` já tipado e o
   `messageId` extraído do header AMQP.

[`TodoEventListener.java:65-87`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java#L65-L87)

```java
if (processedMessageRepository.existsById(messageId)) {
    log.info("[DEDUPE] descartada msg ja processada messageId={}", messageId);
    return;
}
doWork(event);
boolean inserted = processedMessageRepository.tryInsert(messageId);
```

A ordem importa:

| Onde marcar processado | Risco |
|---|---|
| **Antes** do `doWork` | Falha no work deixa mensagem marcada sem efeito visível → **perde raro** |
| **Depois** do `doWork` ✓ | Falha entre work e marca causa reentrega → **duplica raro** |

A escolha é **marcar depois** porque duplicar um e-mail é menos pior que
nunca mandar. Para audit-service a escolha é diferente (Ato 9).

O [`doWork(event)`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java#L89-L99)
chama `emailService.send(event)`:

[`EmailService.java:77-91`](../../notification-service/src/main/java/com/microservices/notification/service/EmailService.java#L77-L91)

```java
@CircuitBreaker(name = "smtp")
@Retry(name = "smtp")
public void send(TodoEvent event) {
    String html = renderTemplate(event);              // Thymeleaf -> HTML
    String subject = SUBJECT_FORMAT.formatted(event.action(), event.title());
    dispatch(subject, html);                          // JavaMailSender -> SMTP
}
```

Resilience4j entra em **duas camadas**:

- **`@Retry`**: tenta 3x com backoff exponencial (200ms, 400ms, 800ms) em
  falhas SMTP transientes (timeout, conexão recusada, etc.). Sucesso
  eventual = totalmente transparente.
- **`@CircuitBreaker`**: se 50% das últimas 20 chamadas (ou 50% delas
  forem slow > 5s), abre. Próximas chamadas lançam
  `CallNotPermittedException` **imediatamente** sem nem tocar no SMTP —
  protege o servidor SMTP de retry storm e o consumer de bloquear thread
  esperando timeout. Após 30s OPEN, vai pra HALF_OPEN e testa com 3
  chamadas; sucesso volta pra CLOSED, falha re-abre.

**Importante**: o retry do **Resilience4j** acontece **dentro do listener**,
antes da exceção propagar pro Spring AMQP. Ou seja:

```
3 retries Resilience4j (rapido, ~1s total)
      ↓ todos falham → exception propaga
3 retries Spring AMQP (lento, 2s/4s/8s backoff)
      ↓ todos falham → basic.reject(requeue=false)
broker roteia pra todo.dlx
```

São **9 tentativas no total** antes da DLQ — 3 níveis × 3 níveis. Pode
parecer demais, mas as camadas têm propósitos distintos: Resilience4j
trata transientes do SMTP, Spring AMQP trata desligamentos completos do
consumer.

Caminho feliz: `send` retorna, `tryInsert(messageId)` grava em
`processed_messages`, Spring AMQP envia `basic.ack`, mensagem some da fila.

---

### Ato 9 — Consumer audit (dedupe via PK)

Em paralelo ao Ato 8, a outra cópia da mensagem (a que foi entregue em
`todo.audit.queue`) é consumida pelo `audit-service`. Modelo similar mas
com **três diferenças deliberadas**.

**Diferença 1: fila única com wildcard.** O `audit-service` tem **um**
`@RabbitListener` em `todo.audit.queue`, bindada com `todo.#`. Pega
created, updated, deleted, e qualquer novo evento futuro do domínio Todo
**sem precisar redeclarar nada**. O notification fragmenta em 3 filas
porque o tratamento difere (subject do e-mail muda por ação); o audit
trata tudo igual.

**Diferença 2: dedupe via INSERT (não SELECT antes).**

[`TodoAuditListener.java:37-56`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoAuditListener.java#L37-L56)

```java
TodoAuditLog auditLog = mapper.toAuditLog(event, messageId);
boolean inserted = repository.insertIfAbsent(auditLog);
```

`insertIfAbsent` (em
[`TodoAuditLogRepository`](../../audit-service/src/main/java/com/microservices/audit/infrastructure/repository/TodoAuditLogRepository.java))
é `INSERT ... ON CONFLICT (message_id) DO NOTHING`. O `messageId` é a **PK
natural** da tabela `todo_audit_log`. Não há tabela separada de
"processados" — o próprio `INSERT` é a operação atômica de dedupe. Mais
simples e mais correto que a abordagem do notification, mas só funciona
porque o "trabalho" é o próprio insert (não tem side effect externo como
envio de e-mail). A conversão de `TodoEvent + messageId` em `TodoAuditLog`
fica no
[`TodoAuditLogMapper`](../../audit-service/src/main/java/com/microservices/audit/mapper/TodoAuditLogMapper.java)
(MapStruct).

**Diferença 3: nenhum retry resilience4j.** O trabalho é puramente
banco — não há SMTP nem dependência externa. Se o INSERT falha (banco
fora), Spring AMQP retenta 3x e cai na DLQ. Sem necessidade de camada
extra.

Caminho feliz: `inserted = true`, log `[AUDIT] registrado`, Spring AMQP
acka, fim. Reentrega da mesma mensagem: `inserted = false`, log
`[AUDIT][DEDUPE] mensagem duplicada descartada`, acka mesmo assim.

---

## Variações do fluxo feliz

### UPDATE / DELETE

`PUT /todos/{id}` ([`TodoService.update`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java#L64-L78))
e `DELETE /todos/{id}` ([`TodoService.delete`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java#L80-L92))
seguem o mesmo fluxo, com 3 diferenças:

1. **Sem idempotency-key** (Ato 2 pulado). PUT é naturalmente idempotente
   pelo HTTP; DELETE atual lança `EntityNotFoundException` na segunda
   chamada (não-idempotente no plano HTTP, mas idempotente no plano de
   estado final).
2. **Routing key diferente**: `todo.updated` ou `todo.deleted`. A
   exchange ainda manda cópia pro `todo.audit.queue` (binding wildcard
   pega tudo) + pra fila correspondente do notification (`todo.updated.queue`
   ou `todo.deleted.queue`).
3. **No notification, listener distinto** (`onTodoUpdated` /
   `onTodoDeleted`) — mas todos delegam pro mesmo `process` privado.

### Mesma Idempotency-Key reusada

Cliente faz `POST /todos` com `Idempotency-Key: X`. Recebe `201`. Cinco
minutos depois, mesma key, mesmo body. Fluxo:

```
Ato 1: gateway repassa (rate limit normal)
Ato 2: IdempotencyService acha registro, mesmo hash de body
       → retorna response cacheada, codigo 201 (sim, mesmo codigo)
       ⛔ STOP. Atos 3-9 nao acontecem. Nenhum novo evento. Nenhum email duplicado.
```

A defesa contra duplicação **acontece bem antes do RabbitMQ**.

### Cópia entregue mas consumer estava fora

Cenário: você publicou `todo.created`, o `notification-service` está down.

- Broker entrega na `todo.created.queue` normalmente.
- Sem consumer registrado, mensagem fica **Ready** (ver
  [`UI/messages.md`](./UI/messages.md)).
- Quando o `notification-service` sobe, o `basic.consume` é registrado e
  o broker imediatamente empurra os Ready acumulados.
- Cada mensagem passa pelo dedupe e pelo work normalmente.

O audit-service consome em paralelo sem ser afetado pelo notification —
foi por isso que o projeto tem **DLX separadas** e **filas independentes**
por consumidor (`x-dead-letter-exchange` diferente em cada um).

### Reentrega depois de crash do consumer (`Unacked` ressurrecto)

Consumer pega mensagem, começa a processar, crashou antes de ackar:

- TCP cai, broker detecta (heartbeat ~60s).
- Mensagem volta de **Unacked** pra **Ready** com flag `redelivered=true`.
- Próximo consumer (mesma instância depois do restart, ou outra)
  reprocessa.
- **Dedupe pega**: se o work já tinha rodado antes do crash, o messageId
  pode (ou não) estar em `processed_messages` — depende de quando o crash
  aconteceu. Caso pior: work roda 2x (e-mail duplicado), que é
  exatamente o trade-off escolhido em "marcar depois".

---

## Caminho de falha → DLQ

Detalhes em [`dlq.md`](./dlq.md). Resumo no fio do fluxo:

```
Ato 8: doWork lanca exception
        ↓ (Resilience4j ja tentou 3x, falhou)
Spring AMQP retry esgota (3 tentativas, 2s/4s/8s backoff)
        ↓
basic.reject(requeue=false)              ← obrigatorio: default-requeue-rejected:false
        ↓
broker descarta da todo.created.queue
        ↓
broker re-publica em todo.dlx com a routing key preservada (x-dead-letter-routing-key=todo.created)
        ↓
binding "todo.created" da todo.created.dlq casa
        ↓
mensagem grava em todo.created.dlq + header x-death anexado
        ↓
TodoEventDlqListener consome, loga WARN, acka
```

O header `x-death` que o RabbitMQ injeta automaticamente carrega o
histórico: quantas vezes a mensagem morreu, em qual fila, por qual motivo,
em qual timestamp. É a principal pista de diagnóstico em logs.

> O audit tem DLX dedicada (`todo.audit.dlx` → `todo.audit.dlq`).
> Justificativa em [`dlq.md`](./dlq.md): falha de envio de e-mail e falha
> de gravação de auditoria são problemas de domínios distintos.

---

## Headers AMQP que viajam com a mensagem

| Header | Quem seta | Para que serve |
|---|---|---|
| `message-id` | `OutboxPublisher` (= `outbox.id`) | Dedupe nos consumers (`processed_messages` e PK do `todo_audit_log`) |
| `__TypeId__` | `Jackson2JsonMessageConverter` no publisher (= FQN do `TodoEvent` do `todo-service`) | Resolver classe alvo na desserialização do consumer (via `idClassMapping`) |
| `content_type` | Converter (= `application/json`) | Informa o formato do body — também ajuda inspeção via Mgmt UI |
| `delivery_mode` | RabbitTemplate (= 2, persistent) | Marca mensagem como durável — sobrevive a restart do broker se a fila for durable |
| `x-death` | RabbitMQ automaticamente, quando msg vai pra DLX | Histórico de "mortes" — count, queue, reason, time |
| `x-first-death-*` | RabbitMQ automaticamente | Primeira morte (útil quando msg morre múltiplas vezes em cadeia) |

Todos esses headers são visíveis no painel **Mgmt UI → Queues → Get
messages** com `encoding=auto`. Ver [`publisher.md`](./publisher.md) para
exemplo de payload completo.

---

## Garantias compostas

O fluxo todo dá:

| Propriedade | Como é garantida | Limite |
|---|---|---|
| **Não-perda na escrita** | Outbox atômico + scheduler com retry indefinido | Banco precisa estar vivo no Ato 3 |
| **At-least-once delivery** | Spring AMQP nack-with-requeue + redelivery automática se consumer crash | Pode duplicar; dedupe trata |
| **Idempotência da operação POST** | `Idempotency-Key` + `idempotency_keys` (TTL 24h) | Cliente precisa enviar a key |
| **Idempotência do consumer notification** | `processed_messages` + check antes do work, insert depois | Janela de "duplica raro" se crash entre work e insert |
| **Idempotência do consumer audit** | PK natural via `messageId` + `INSERT ON CONFLICT DO NOTHING` | Nenhum (mais forte que notification) |
| **Isolamento entre consumers** | Filas separadas + DLX separadas | Cada um cai sozinho; um não derruba o outro |
| **Visibilidade de falha** | DLQ por tipo de evento + log com `x-death` + Mgmt UI | Acka no DlqListener em dev (em prod alarmar antes de ackar) |

O que **não** está garantido:

- **Ordem**: o broker entrega na ordem da fila, mas Resilience4j retry +
  Spring AMQP retry + concurrency > 1 podem embaralhar. Audit é
  append-only com `occurredAt` do publisher — quem precisa de ordem
  reconstrói pela timestamp.
- **Exactly-once**: ninguém oferece de graça. O projeto entrega
  at-least-once + idempotência em todo consumer → equivale a exactly-once
  do ponto de vista do efeito observável.
- **Publish synchronous confirms**: o RabbitTemplate não tem
  `publisher-confirms` ativado. Em teoria o broker pode aceitar o frame
  e morrer antes de persistir. Risco baixo no contexto desse projeto
  (broker single-node), mas mitigação seria habilitar
  `spring.rabbitmq.publisher-confirm-type=correlated` e tratar nacks no
  outbox publisher.

---

## Linha do tempo de exemplo (caso feliz)

```
t=0ms       Cliente -> Gateway: POST /todos
t=1ms       Gateway -> todo-service (Eureka resolve, sem rate limit)
t=2ms       IdempotencyService: key X primeira vez, ok prosseguir
t=3ms       TodoService.create: INSERT em todos + INSERT em outbox_events (mesma TX)
t=15ms      TX commit
t=16ms      todo-service -> Cliente: 201 Created
                                                                    ← cliente livre
t=1.2s      OutboxPublisher.publishPending tick (proximo a partir do t=0)
t=1.21s     claimNext: pega a linha, marca processing_node
t=1.22s     convertAndSend -> rabbitmq:5672
t=1.23s     broker aceita
t=1.24s     OutboxPublisher: markPublished + save
t=1.25s     broker roteia: copia entregue em todo.created.queue e todo.audit.queue
t=1.26s     notification-service: basic.deliver
t=1.27s     notification: dedupe check (miss), doWork
t=1.40s     notification: SMTP responde 250
t=1.41s     notification: tryInsert(messageId)
t=1.42s     notification: basic.ack
t=1.43s     audit-service: basic.deliver (em paralelo!)
t=1.44s     audit: insertIfAbsent (inserted=true)
t=1.45s     audit: basic.ack
```

Latência percebida pelo cliente: **~16ms**. Latência do efeito colateral
(e-mail saindo, audit gravado): **~1.4s** dominada pelo intervalo do
scheduler do outbox. Ajustar `outbox.poll-interval-ms` afeta esse trade-off
diretamente.

---

## Referências cruzadas

- [`publisher.md`](./publisher.md) — Atos 5-6 em profundidade.
- [`consumer.md`](./consumer.md) — Atos 8-9 em profundidade.
- [`exchange.md`](./exchange.md) — Ato 7 em profundidade.
- [`filas.md`](./filas.md) — topologia das 8 filas.
- [`dlq.md`](./dlq.md) — caminho de falha em detalhes.
- [`profiles.md`](../profiles.md) — como o `application.yml` muda o
  comportamento por ambiente.
- [`debug.md`](../debug.md) — debugar o fluxo inteiro no IntelliJ com
  breakpoint no Ato 3.
