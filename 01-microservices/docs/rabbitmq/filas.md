# Filas

O que cada fila RabbitMQ do projeto representa. Todas vivem no broker `rabbitmq` (container do [`docker-compose.yml`](../../docker-compose.yml)) e são **declaradas pelos próprios consumers** na inicialização — o publisher não declara fila nenhuma.

São **8 filas** no total: 4 principais (3 por tipo de evento + 1 de auditoria) e 4 DLQs irmãs (sufixo `.dlq`).

> **Importante**: o `todo-service` **não publica direto nas filas**. Ele registra o evento no outbox com destino igual à exchange `todo.exchange` e uma routing key (`todo.created` / `todo.updated` / `todo.deleted`) — ver [`TodoService.create/update/delete`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java). O [`OutboxPublisher`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java) lê do outbox e publica na exchange. Quem coloca a mensagem em cada fila é o **broker**, com base nos *bindings* declarados por cada consumer. Padrão: [pub/sub via topic exchange](./publisher.md).

---

## Fluxo completo do evento

```
 Cliente
   │  POST /todos
   ▼
┌─────────────────────────────┐
│ todo-service                │
│ 1. Salva o Todo no banco    │
│ 2. Anota o evento numa      │
│    "lista de pendentes"     │
│    (tudo na mesma operação) │
└──────────────┬──────────────┘
               │
               ▼  (a cada 2 segundos)
┌─────────────────────────────┐
│ OutboxPublisher             │
│ Pega o que está pendente    │
│ e entrega ao "carteiro"     │
│ (exchange todo.exchange)    │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ todo.exchange (topic)       │
│ Olha a routing key da msg   │
│ (todo.created / todo.updated│
│  / todo.deleted) e faz      │
│ CÓPIAS pras filas bindadas  │
│ que casam com aquele padrão │
└──┬──────┬──────┬─────────┬──┘
   │      │      │         │
   ▼      ▼      ▼         ▼
created updated deleted   audit       ← as 4 filas (caixas de mensagem)
queue   queue   queue     queue
   │      │      │         │
   ▼      ▼      ▼         ▼
┌───────────────────┐  ┌─────────────┐
│ notification-     │  │ audit-      │
│ service           │  │ service     │
│ (manda email)     │  │ (grava log) │
└───────────────────┘  └─────────────┘

   Se um consumer falhar 3x na mesma mensagem
   (retry in-memory esgotado), o broker roteia
   a msg pela DLX e ela cai na DLQ irmã (ex:
   todo.created.dlq) pra ninguém perder o sinal.
```

### Exemplo: POST /todos { "title": "Estudar RabbitMQ" }

1. **todo-service** salva o Todo + grava a linha no `outbox_event` (mesma transação no Postgres) e responde 201 ao cliente.
2. Em até 2s, o **`OutboxPublisher`** dá `claim` na linha pendente e chama `rabbitTemplate.convertAndSend("todo.exchange", "todo.created", payload)` com `messageId = outbox.id` (pra dedupe no consumer).
3. A **`todo.exchange`** (topic) entrega cópias em todas as filas com binding que case com `todo.created`: `todo.created.queue` (match exato) e `todo.audit.queue` (`todo.#` wildcard). As outras duas (`todo.updated.queue`, `todo.deleted.queue`) são ignoradas.
4. O **notification-service** lê a `todo.created.queue` e dispara o email de "Todo criado".
5. O **audit-service** lê a `todo.audit.queue` e persiste o evento no `todo_audit_log` (Postgres `auditdb`) — **em paralelo** ao passo 4.

---

## Como a exchange conversa com as filas

A `todo.exchange` é um **topic exchange**: quem publica (o producer) joga uma mensagem com uma **routing key** e **não sabe** quem vai receber. Quem decide "essa mensagem chega em mim" são os **bindings** — pequenos registros declarados pelo consumer que ligam uma fila à exchange com um padrão de routing key. No nosso caso, todas as 4 filas principais estão bindadas na mesma exchange `todo.exchange`.

### O fluxo concreto, passo a passo

Quando o `OutboxPublisher` precisa publicar um evento de `CREATED`, o código efetivamente faz:

```java
rabbitTemplate.convertAndSend(
    "todo.exchange",     // exchange destino
    "todo.created",      // routing key
    payload,             // o TodoEvent serializado em JSON
    msg -> {             // post-processor: anexa messageId pra dedupe no consumer
        msg.getMessageProperties().setMessageId(event.getId());
        return msg;
    }
);
```

A partir daí, **o producer está fora**. O que acontece dentro da exchange:

```
                                   ┌──────────────────────────────────────────────┐
                                   │ todo.exchange (topic)                        │
                                   │                                              │
publish(payload,                   │  Pra cada binding existente na exchange:     │
        rk=todo.created) ────────► │   1. Pega o padrão da routing key            │
                                   │   2. Compara com a routing key da msg        │
                                   │   3. Se bate, entrega uma cópia na fila      │
                                   │      destino daquele binding                 │
                                   └──────┬───────────────────────────────────────┘
                                          │
            ┌─────────────────────────────┼─────────────────────────────┐
            │                             │                             │
            ▼                             ▼                             ▼
  ┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
  │ binding pattern: │         │ binding pattern: │         │ binding pattern: │
  │ todo.created     │         │ todo.updated     │         │ todo.#           │
  │                  │         │                  │         │                  │
  │  ✓ MATCH         │         │  ✗ skip          │         │  ✓ MATCH (tudo)  │
  └────────┬─────────┘         └──────────────────┘         └────────┬─────────┘
           ▼                                                         ▼
  todo.created.queue                                          todo.audit.queue
  (notification-service)                                      (audit-service)
```

Note que `todo.updated.queue` e `todo.deleted.queue` **também são consideradas** pela exchange, mas o broker descarta antes de entregar — o padrão do binding não bate. Já a `todo.audit.queue` recebe tudo porque seu binding é `todo.#` (wildcard que casa "qualquer routing key começando com `todo.`").

### Os 3 ingredientes que fazem isso funcionar

1. **O binding**. Cada consumer declara seus próprios bindings via `@Bean Binding` no `RabbitMQConfig`. O notification declara 3 bindings (um por routing key exata); o audit declara 1 binding com `todo.#`. A declaração é idempotente — se o binding já existir, o RabbitMQ ignora. Sem binding, a fila simplesmente nunca vê nada da exchange, mesmo existindo no mesmo broker.

2. **A routing key, que carrega o critério de roteamento**. Quando o producer publica com `"todo.created"`, esse valor vai junto da mensagem como propriedade AMQP. É justamente esse campo que a exchange topic lê pra decidir o roteamento, comparando contra o padrão de cada binding. O critério **precisa** estar na routing key; a exchange topic não consegue espiar dentro do JSON do body (pra isso usaríamos uma `headers exchange`, abordagem mais cara e que o projeto não usa).

3. **O padrão do binding**. É a string registrada junto com o binding na exchange. Pode ser exata (`todo.created`) ou usar curingas: `*` casa uma palavra, `#` casa zero ou mais palavras separadas por ponto. Binding sem padrão não existe — toda fila bindada precisa de um (pra `fanout` o padrão é ignorado, mas o conceito de bind segue valendo).

### `__TypeId__` e o cross-service de classes — por que o consumer recebe o `TodoEvent` direto

O `Jackson2JsonMessageConverter` do **publisher** serializa o payload e anexa um header AMQP `__TypeId__` com o FQN da classe (`com.microservices.todo.event.TodoEvent`).

No **consumer**, o conversor usa esse header pra escolher a classe alvo da desserialização. Como cada serviço tem sua **própria** `TodoEvent` (FQN diferente: `com.microservices.notification.event.TodoEvent` / `com.microservices.audit.event.TodoEvent`), o conversor precisa de um `idClassMapping` explícito — caso contrário, lança `ClassNotFoundException` ou recusa por segurança (Spring AMQP 3.x só carrega classes "trusted"):

```java
typeMapper.setIdClassMapping(Map.of(
    "com.microservices.todo.event.TodoEvent", TodoEvent.class
));
typeMapper.setTypePrecedence(TypePrecedence.INFERRED); // defesa extra se vier sem __TypeId__
```

Ambos os consumers (notification e audit) fazem esse mapeamento. Resultado: o `@RabbitListener` recebe o `TodoEvent` já tipado, no FQN local.

### Em uma frase

O producer joga 1 mensagem com 1 routing key (`todo.created`) na `todo.exchange`; a exchange pergunta a cada binding "esse padrão casa com a routing key?"; pra cada "sim", grava uma cópia da mensagem na fila daquele binding; o consumer da fila lê normalmente, sem saber que veio de uma exchange.

---

## Filas principais

### `todo.created.queue`

Declarada por [`notification-service/.../RabbitMQConfig.java`](../../notification-service/src/main/java/com/microservices/notification/config/RabbitMQConfig.java). Bind em `todo.exchange` com routing key **exata** `todo.created` — só recebe mensagens publicadas com essa rk. Consumida pelo `notification-service` ([`TodoEventListener.onTodoCreated`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java)), que dispara o email de "Todo criado". Idempotência via [`processed_message`](../../notification-service/src/main/java/com/microservices/notification/infrastructure/entity/ProcessedMessage.java) (chave = `messageId` setado pelo `OutboxPublisher`).

### `todo.updated.queue`

Bind em `todo.exchange` com `todo.updated`. Consumida pelo `notification-service` pra mandar o email de "Todo atualizado". PUT no-op no `todo-service` ainda gera evento (não há comparação de diff antes do `outbox.record`) — então a fila pode ver `UPDATED` com payload idêntico ao estado anterior; cabe ao consumer decidir se quer otimizar.

### `todo.deleted.queue`

Bind em `todo.exchange` com `todo.deleted`. Consumida pelo `notification-service` pra mandar o email de "Todo removido". DELETE no `todo-service` lança `EntityNotFoundException` na segunda chamada (não é idempotente do lado HTTP), então só o primeiro DELETE de cada id chega aqui.

### `todo.audit.queue`

Declarada por [`audit-service/.../RabbitMQConfig.java`](../../audit-service/src/main/java/com/microservices/audit/config/RabbitMQConfig.java). Bind em `todo.exchange` com o **wildcard** `todo.#` — recebe **todos** os eventos do domínio Todo (`todo.created`, `todo.updated`, `todo.deleted`). Consumida pelo `audit-service` ([`TodoAuditListener`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoAuditListener.java)), que persiste cada evento como linha append-only em `todo_audit_log` (Postgres `auditdb`). Dedupe via `messageId` na própria tabela (UPSERT idempotente).

---

## DLQs

Cada fila principal tem uma DLQ correspondente. O caminho até a DLQ é configurado em **duas pontas**:

- **Na fila principal**, via `x-dead-letter-exchange` (e opcionalmente `x-dead-letter-routing-key`) declarados no `QueueBuilder` — diz pro broker: "msg rejeitada sem requeue vai pra essa DLX".
- **No listener** (`application.yml`), via `spring.rabbitmq.listener.simple.default-requeue-rejected: false` + `retry.max-attempts: 3` — diz pro Spring AMQP: "tente 3x em memória e, se esgotar, **rejeite sem requeue**". Sem o `default-requeue-rejected: false` a msg voltaria pra fila e ficaria em loop.

Resumo do gatilho: 3 tentativas em memória → falhou → rejeitada sem requeue → broker roteia pela DLX → cai na DLQ irmã.

### `todo.created.dlq`

Holding pen das mensagens que falharam em `todo.created.queue`. Roteada via a DLX **compartilhada** do notification (`todo.dlx`) com routing key `todo.created`. Inspecionada por [`TodoEventDlqListener`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventDlqListener.java) (loga e mantém a msg lá pra análise). Causas típicas: SMTP fora depois do retry esgotar, payload malformado, bug determinístico no handler.

### `todo.updated.dlq`

Equivalente pra `todo.updated.queue`. Roteada via `todo.dlx` com routing key `todo.updated`. Mesmas causas; mantida separada pra a inspeção/log ficar por tipo de evento.

### `todo.deleted.dlq`

Equivalente pra `todo.deleted.queue`. Roteada via `todo.dlx` com routing key `todo.deleted`. Tende a ser a menos movimentada — o payload de `DELETED` é o mais simples, então tem menos superfície pra falhar.

### `todo.audit.dlq`

Holding pen da `todo.audit.queue`. Usa uma **DLX dedicada** (`todo.audit.dlx`) — isolada da DLX do notification de propósito, pra que falhas no audit não se misturem com falhas no envio de email. Bind catch-all (`#`). Inspecionada por [`TodoAuditDlqListener`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoAuditDlqListener.java). Como o audit é append-only com dedupe via `messageId`, o caminho realista pra cair aqui é Postgres fora durante o insert ou payload malformado.

---

## Resumo

| Fila | Tipo | Consumer | Binding em `todo.exchange` |
|---|---|---|---|
| `todo.created.queue` | principal | notification-service | `todo.created` (exato) |
| `todo.updated.queue` | principal | notification-service | `todo.updated` (exato) |
| `todo.deleted.queue` | principal | notification-service | `todo.deleted` (exato) |
| `todo.audit.queue` | principal | audit-service | `todo.#` (wildcard — recebe tudo) |
| `todo.created.dlq` | DLQ | notification-service (inspeção) | n/a (bind em `todo.dlx`) |
| `todo.updated.dlq` | DLQ | notification-service (inspeção) | n/a (bind em `todo.dlx`) |
| `todo.deleted.dlq` | DLQ | notification-service (inspeção) | n/a (bind em `todo.dlx`) |
| `todo.audit.dlq` | DLQ | audit-service (inspeção) | n/a (bind em `todo.audit.dlx`) |

### Onde cada fila é declarada

| Fila | `@Bean` em |
|---|---|
| `todo.created.queue` / `todo.updated.queue` / `todo.deleted.queue` | `notification-service` → `RabbitMQConfig` |
| `todo.audit.queue` | `audit-service` → `RabbitMQConfig` |
| `todo.created.dlq` / `todo.updated.dlq` / `todo.deleted.dlq` | `notification-service` → `RabbitMQConfig` |
| `todo.audit.dlq` | `audit-service` → `RabbitMQConfig` |

> O `todo-service` também declara `todo.created.queue` / `todo.updated.queue` / `todo.deleted.queue` no [seu próprio `RabbitMQConfig`](../../todo-service/src/main/java/com/microservices/todo/config/RabbitMQConfig.java) **sem** argumentos de DLX. Como `queue.declare` é idempotente, isso só vira problema se o publisher subir **antes** do notification — nesse caso a fila nasce sem `x-dead-letter-exchange` e precisa ser apagada e recriada (queue arguments são imutáveis depois da declaração). Padrão de produção é **só o consumer dono declarar** a fila; o publisher conhece apenas a exchange.
