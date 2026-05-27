# Exchange

## Visão Geral

No RabbitMQ, **o publisher nunca escreve direto numa fila**. Ele entrega a mensagem numa **exchange**, e é a exchange que decide pra quais filas a mensagem deve ser copiada. Pensa na exchange como o **roteador** do broker: ela recebe mensagens com um "endereço" (a *routing key*) e usa uma tabela de bindings — declarada pelos consumers — pra escolher os destinos.

```
                    ┌─────────────────────────┐
publisher ─publish─►│       exchange          │─copy─► queue A
                    │  (roteador do broker)   │─copy─► queue B
                    │                         │   ✗    queue C  (binding não casou)
                    └─────────────────────────┘
```

Esse desenho é o que viabiliza pub/sub no RabbitMQ:

- O publisher conhece **só a exchange e a routing key** — não sabe quem ouve.
- O consumer declara a fila e o **binding** (qual padrão de routing key essa fila quer receber).
- Adicionar um novo consumer = declarar uma fila nova com binding novo. **Zero alteração no publisher.**

Esse projeto usa três exchanges, todas do tipo **topic**: `todo.exchange` (eventos de domínio), `todo.dlx` (DLX do notification-service) e `todo.audit.dlx` (DLX dedicada do audit-service). Detalhe de cada uma na seção "Como o projeto usa".

---

## Os 4 tipos de exchange

O AMQP 0-9-1 define quatro tipos. A escolha do tipo é o que define **a regra de roteamento** entre exchange e fila.

| Tipo | Regra | Quando usar |
|---|---|---|
| `direct` | match **exato** entre routing key da msg e do binding | Quando só existem N rotas conhecidas e estáticas |
| `topic` ✅ | match por **padrão** com curingas `*` (1 palavra) e `#` (N palavras) | Quando a routing key tem **hierarquia** (`dominio.acao`) e novos consumers podem querer fatias diferentes |
| `fanout` | ignora routing key — copia pra **todas** as filas bindadas | Broadcast puro (ex: invalidar cache em todos os nós) |
| `headers` | roteia por **headers AMQP** em vez de routing key | Roteamento por múltiplos critérios (raro; geralmente topic resolve) |

O projeto adotou **topic** porque a routing key segue o padrão `dominio.acao` (`todo.created`, `todo.updated`, `todo.deleted`). Isso dá dois ganhos imediatos:

1. O `notification-service` se inscreve em routing keys **exatas** (`todo.created`) e separa em 3 filas diferentes — uma por ação.
2. O `audit-service` se inscreve em `todo.#` e recebe **tudo** do domínio numa fila só, sem precisar listar cada ação. Amanhã, se aparecer `todo.archived`, o audit pega de graça; o notification ignora até alguém declarar uma fila pra ele.

Direct funcionaria pro notification, mas amarraria o audit a listar todas as ações conhecidas. Fanout cobriria o audit, mas o notification perderia a capacidade de filtrar created/updated/deleted no broker — teria que filtrar em código. Topic dá os dois comportamentos com a mesma exchange.

---

## Routing keys

A **routing key** é uma string que viaja junto da mensagem, separada do body, como propriedade AMQP. Em topic exchange, ela é interpretada como **palavras separadas por ponto** (`todo.created`, `user.login.failed`, etc.).

No projeto, as routing keys são constantes em [`RabbitMQConfig`](../../todo-service/src/main/java/com/microservices/todo/config/RabbitMQConfig.java) do `todo-service`:

```java
public static final String ROUTING_CREATED = "todo.created";
public static final String ROUTING_UPDATED = "todo.updated";
public static final String ROUTING_DELETED = "todo.deleted";
```

Quem escolhe a routing key é o **publisher** no momento da publicação. No nosso caso, é o [`OutboxPublisher`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java):

```java
rabbitTemplate.convertAndSend(
    event.getExchange(),    // "todo.exchange"
    event.getRoutingKey(),  // "todo.created" / "todo.updated" / "todo.deleted"
    payload,
    msg -> { msg.getMessageProperties().setMessageId(event.getId()); return msg; }
);
```

A routing key é gravada no outbox junto do evento (ver coluna `routing_key` da `outbox_event`), então a decisão de rota é tomada **no momento do `outbox.record`** dentro da transação do domínio — não no momento da publicação. Garantia: a rota é parte do fato persistido.

### Curingas em topic exchange

Em **binding** (não na mensagem!) é onde os curingas entram. Dois símbolos:

| Símbolo | Significado | Exemplo | Casa com |
|---|---|---|---|
| `*` | **uma palavra** (entre pontos) | `todo.*` | `todo.created`, `todo.deleted` — mas **não** `todo` nem `todo.foo.bar` |
| `#` | **zero ou mais palavras** | `todo.#` | `todo`, `todo.created`, `todo.foo.bar` |

No projeto, **só `#` está em uso** — no binding da `todo.audit.queue` em `todo.exchange`. Os bindings do notification são todos exatos.

```
binding pattern           casa com publicação rk=todo.created ?
─────────────────────────────────────────────────────────────
todo.created              ✓ (match exato)
todo.updated              ✗
todo.*                    ✓
todo.#                    ✓
user.created              ✗
#                         ✓ (curinga-mor — pega tudo, em qualquer exchange topic)
```

> ⚠️ Curinga é **só pro padrão do binding**, não pra routing key da msg. Publicar uma mensagem com rk `"todo.#"` não dá match em nada — vira uma string literal. O broker compara *literal-da-msg* contra *padrão-do-binding*, nunca o contrário.

---

## Como o projeto usa

### `todo.exchange` — eventos de domínio

Exchange principal, do tipo **topic**. Recebe os eventos que o `todo-service` publica via outbox. Declarada nos três serviços (publisher e ambos consumers), porque `exchange.declare` é idempotente — se já existir com o mesmo tipo, o broker ignora.

| Binding | Fila destino | Quem declara |
|---|---|---|
| `todo.created` | `todo.created.queue` | notification-service |
| `todo.updated` | `todo.updated.queue` | notification-service |
| `todo.deleted` | `todo.deleted.queue` | notification-service |
| `todo.#` | `todo.audit.queue` | audit-service |

Quando o publisher chama `convertAndSend("todo.exchange", "todo.created", ...)`, a exchange varre **todos os bindings existentes** e, pra cada um cujo padrão case com `todo.created`, copia a mensagem na fila destino. Por isso uma única `publish` resulta em 2 mensagens entregues (notification + audit), e o publisher segue sem saber.

### `todo.dlx` — DLX compartilhada do notification

Exchange tipo **topic** declarada pelo `notification-service`. Recebe **mensagens que esgotaram retry** nas 3 filas do notification (ver [`filas.md`](./filas.md) pro fluxo). A rota até ela é configurada nas filas principais com `x-dead-letter-exchange=todo.dlx` e `x-dead-letter-routing-key` igual à rk original — então quando uma msg cai pra DLX, a routing key é preservada.

| Binding | Fila destino |
|---|---|
| `todo.created` | `todo.created.dlq` |
| `todo.updated` | `todo.updated.dlq` |
| `todo.deleted` | `todo.deleted.dlq` |

Resultado: a DLQ tem a mesma "forma" da fila principal — 1 DLQ por ação. Facilita inspeção (você sabe por que tipo de evento as falhas estão concentradas).

### `todo.audit.dlx` — DLX dedicada do audit

Exchange tipo **topic** declarada pelo `audit-service`, **separada** da DLX do notification. Decisão deliberada: falha no envio de email não polui o stream de falhas de auditoria, e vice-versa. Mantém os dois domínios de "veneno" isolados.

| Binding | Fila destino |
|---|---|
| `#` | `todo.audit.dlq` |

Catch-all porque o binding original na exchange principal também é `todo.#` — qualquer routing key que tenha chegado na `todo.audit.queue` deve poder cair na DLQ.

---

## Anatomia da declaração

Toda exchange usada no projeto é declarada via `@Bean TopicExchange`. Exemplo do `todo-service` (publisher):

```java
public static final String EXCHANGE = "todo.exchange";

@Bean
public TopicExchange todoExchange() {
    return new TopicExchange(EXCHANGE);
}
```

Quando o `ConnectionFactory` do Spring AMQP abre o channel inicial, ele varre os beans do tipo `Exchange`/`Queue`/`Binding` e roda `exchange.declare`/`queue.declare`/`queue.bind` correspondentes. Tudo idempotente:

- Se a exchange **não existe**, é criada.
- Se **existe com mesmo tipo**, ok — segue em frente.
- Se **existe com tipo diferente** (ex: alguém criou `todo.exchange` como `direct` antes), o broker responde com `PRECONDITION_FAILED` e o app **falha no startup**. Sinal claro pra apagar a exchange velha e deixar a nova ser criada — argumentos de exchange/queue são imutáveis depois de declarados.

### Durabilidade

Por padrão, `TopicExchange` do Spring AMQP é declarada **durable=true**: sobrevive a restart do broker. Combinada com filas duráveis (`QueueBuilder.durable(...)`) e mensagens persistentes (default do `RabbitTemplate`, `delivery_mode=2`), o conjunto garante que nada se perde se o broker reiniciar.

---

## Como inspecionar

### Listar exchanges existentes

```powershell
docker exec rabbitmq rabbitmqctl list_exchanges name type durable
```

Esperado em ambiente saudável:

```
todo.exchange        topic   true
todo.dlx             topic   true
todo.audit.dlx       topic   true
amq.direct           direct  true   ← built-in do RabbitMQ
amq.topic            topic   true   ← built-in
...
```

### Listar bindings de uma exchange

```powershell
docker exec rabbitmq rabbitmqctl list_bindings source_name routing_key destination_name destination_kind
```

Filtre pela exchange `todo.exchange` mentalmente — deve aparecer 4 linhas (uma por binding listado na tabela acima).

### Pelo painel web

`http://localhost:15672` → aba **Exchanges** → clicar em `todo.exchange`. Mostra a lista de bindings, taxa de publicação em tempo real, e tem um botão "Publish message" útil pra testar uma rota sem subir o serviço.

---

## Resumo

- **Exchange é o roteador** do RabbitMQ. Publisher entrega na exchange; ela decide as filas via bindings.
- Tipo **topic** foi escolhido pra permitir routing key hierárquica (`dominio.acao`) e bindings com curingas.
- O projeto tem **três exchanges**: `todo.exchange` (eventos), `todo.dlx` (DLX do notification) e `todo.audit.dlx` (DLX do audit). Todas topic, todas durables.
- Routing key é parte da mensagem; padrão de match é parte do binding. Curingas vão **só no binding**.
- Detalhe de cada fila bindada e o gatilho da DLQ estão em [`filas.md`](./filas.md). Visão do publisher em [`publisher.md`](./publisher.md). Visão do consumer em [`consumer.md`](./consumer.md).
