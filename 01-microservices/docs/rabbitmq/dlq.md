# Dead Letter Queue (DLQ)

## Visão Geral

Uma **Dead Letter Queue** é uma fila secundária pra onde o RabbitMQ desvia mensagens que **não puderam ser processadas com sucesso** — payload malformado, exceção persistente no handler, downstream fora do ar depois de N tentativas. Sem DLQ, essas mensagens ficariam em loop infinito (consumer pega → falha → requeue → pega de novo → ...) ou seriam descartadas silenciosamente. Com DLQ, ficam num "compartimento" separado, pra inspeção e reprocessamento manual.

No RabbitMQ, DLQ é sempre o **destino**; o que faz a mensagem chegar lá é uma **Dead Letter Exchange (DLX)** — uma exchange normal cujo único papel é receber as msgs descartadas e roteá-las pras DLQs. Tecnicamente: a *fila principal* tem um argumento `x-dead-letter-exchange` apontando pra DLX; quando o broker decide descartar uma msg dessa fila, ele a publica nessa DLX, que então roteia pra DLQ via binding.

```
                                          falha 3x + sem requeue
   ┌──────────────────────┐               ┌──────────────────┐
   │ todo.created.queue   │ ─────────────►│ todo.dlx (topic) │
   │ (x-dead-letter-      │   rejeita     └────────┬─────────┘
   │  exchange: todo.dlx) │                        │ rk=todo.created
   └──────────────────────┘                        ▼
                                          ┌──────────────────┐
                                          │ todo.created.dlq │
                                          └──────────────────┘
                                                   │
                                                   ▼
                                          TodoEventDlqListener (loga WARN)
```

---

## Topologia no projeto

São **4 DLQs**, distribuídas em **2 DLX**:

| DLX | Fila principal | DLQ irmã | Routing key do binding |
|---|---|---|---|
| `todo.dlx` | `todo.created.queue` | `todo.created.dlq` | `todo.created` |
| `todo.dlx` | `todo.updated.queue` | `todo.updated.dlq` | `todo.updated` |
| `todo.dlx` | `todo.deleted.queue` | `todo.deleted.dlq` | `todo.deleted` |
| `todo.audit.dlx` | `todo.audit.queue` | `todo.audit.dlq` | `#` (catch-all) |

**Por que duas DLX em vez de uma só?** Isolamento. Falhas de envio de e-mail (notification) e falhas de gravação de auditoria (audit) são problemas de domínios diferentes — operados por times diferentes em uma empresa real, com playbooks de resolução diferentes. Misturar tudo em uma DLX comum exigiria filtrar a DLQ por origem; manter DLXs separadas resolve no nível da topologia.

---

## Os 2 ingredientes que fazem a msg cair na DLQ

### 1. Argumentos na declaração da fila — `x-dead-letter-exchange`

Cada fila principal é declarada com `x-dead-letter-exchange` apontando pra DLX correspondente. No notification, também tem `x-dead-letter-routing-key` pra **preservar a routing key original** (sem isso, o broker usaria a rk que a msg tinha quando foi recebida — funciona no caso simples, mas é frágil a refactors):

```java
// notification-service/.../config/RabbitMQConfig.java
@Bean
public Queue createdQueue() {
    return QueueBuilder.durable(QUEUE_CREATED)
            .withArgument("x-dead-letter-exchange", DLX)               // "todo.dlx"
            .withArgument("x-dead-letter-routing-key", ROUTING_CREATED) // "todo.created"
            .build();
}
```

No audit, a routing key é preservada implicitamente (sem `x-dead-letter-routing-key`), e o binding da DLQ é `#`:

```java
// audit-service/.../config/RabbitMQConfig.java
@Bean
public Queue auditQueue() {
    return QueueBuilder.durable(QUEUE_AUDIT)
            .withArgument("x-dead-letter-exchange", DLX_AUDIT) // "todo.audit.dlx"
            // sem x-dead-letter-routing-key → preserva a routing key original
            .build();
}
```

> ⚠️ **Argumentos de queue são imutáveis depois de declarados.** Se uma fila foi criada antes (ex: pelo `todo-service`, que ainda declara as filas principais **sem** DLX) e depois o consumer tenta declarar com `x-dead-letter-exchange`, o broker responde `PRECONDITION_FAILED` e o consumer falha no startup. A correção é **apagar a fila** (`rabbitmqctl delete_queue todo.created.queue`) e deixar o consumer recriá-la com os args corretos. Por isso o padrão de produção é "só o consumer dono declara a fila" — ver nota no final de [`filas.md`](./filas.md).

### 2. Listener configurado pra rejeitar **sem requeue**

Argumentos na queue **sozinhos não bastam**. O broker só roteia pra DLX quando a msg é descartada da fila principal por um dos motivos abaixo:

- **Rejeitada (`basic.reject` / `basic.nack`) com `requeue=false`** — é o caso que nos importa.
- TTL da mensagem expirou (não usamos).
- Fila estourou o `x-max-length` (não usamos).

Por padrão, o Spring AMQP rejeita com `requeue=true` (volta pra fila → loop). Pra ativar o caminho da DLX, o `application.yml` dos consumers tem:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        retry:
          enabled: true
          max-attempts: 3            # tenta 3x em memória, com backoff
          initial-interval: 2000ms
          multiplier: 2.0
          max-interval: 10000ms
        # Crítico: sem isso a DLX nunca dispara — msg fica em loop.
        default-requeue-rejected: false
```

**Como as duas configs trabalham juntas:**

1. Listener lança exception.
2. Interceptor de retry do Spring AMQP segura a msg em memória e tenta de novo (intervalo: 2s → 4s → 8s, no máximo 10s).
3. Esgotadas as 3 tentativas, o Spring AMQP **rejeita** a msg via `basic.reject` com `requeue=false`.
4. Broker descarta a msg da fila principal e a publica na DLX `todo.dlx` com a routing key preservada.
5. Binding `todo.created` da `todo.created.dlq` casa → broker copia a msg pra DLQ.
6. `TodoEventDlqListener` recebe e loga.

> Note que o retry do Spring AMQP é **in-memory** e **bloqueia a thread do listener** durante o backoff. Pra carga alta isso vira gargalo — alternativa de produção é `RepublishMessageRecoverer` ou retry baseado em filas com TTL ("retry queues"). O projeto não precisa disso hoje.

---

## Os DLQ listeners

Cada DLQ tem um listener próprio que loga a mensagem em WARN e ackka — não reprocessa, não republica. Implementação intencionalmente simples:

```java
// notification-service/.../listener/TodoEventDlqListener.java
@RabbitListener(queues = RabbitMQConfig.DLQ_CREATED)
public void onCreatedDlq(Message message) {
    String body = new String(message.getBody(), StandardCharsets.UTF_8);
    Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
    log.warn("[DLQ] {} -> messageId={} x-death={} body={}",
            RabbitMQConfig.DLQ_CREATED,
            message.getMessageProperties().getMessageId(),
            xDeath,
            body);
}
```

Detalhes deliberados:

- **Recebe `Message` bruta**, não `TodoEvent`. A causa mais comum de cair na DLQ é exatamente **payload que não desserializa** — tentar desserializar de novo no listener da DLQ daria o mesmo erro em loop, e a msg seria rejeitada de novo. `Message` aceita qualquer body, sem conversão.
- **Ack via retorno normal** (sem exception) — em dev, isso evita poluir o log com reentregas cíclicas; a msg sai da DLQ depois do log. Em **produção**, esse listener seria substituído por alarme em cima da métrica `messages` da DLQ, com as msgs **mantidas** lá (sem ack) pra inspeção manual + redrive.
- **Loga o header `x-death`** — o RabbitMQ anexa esse header automaticamente em msgs roteadas pela DLX. Contém `count` (quantas vezes foi rejeitada), `queue` (origem), `reason` (`rejected` / `expired` / `maxlen`) e `time`. É a principal pista de diagnóstico.

O `audit-service` tem o equivalente em [`TodoAuditDlqListener`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoAuditDlqListener.java).

---

## Hook de teste embutido

O `TodoEventListener` do notification tem um gatilho proposital pra exercitar o caminho retry → DLQ **sem precisar quebrar o serviço**: se o título do Todo começar com `!fail`, o handler lança `IllegalStateException`.

```java
// notification-service/.../listener/TodoEventListener.java
private static final String FAIL_PREFIX = "!fail";

private void doWork(TodoEvent event) {
    if (event.title() != null && event.title().startsWith(FAIL_PREFIX)) {
        throw new IllegalStateException("Falha simulada por prefixo '" + FAIL_PREFIX + "'");
    }
    // ...
}
```

### Roteiro de teste end-to-end

```powershell
# 1. Cria um Todo com prefixo de falha
Invoke-RestMethod -Uri 'http://localhost:8090/todos' -Method Post `
  -Headers @{ 'Content-Type'='application/json' } `
  -Body '{"title":"!fail teste dlq","description":""}'
```

Logs esperados no `notification-service` (3 tentativas, espaçadas por backoff):

```
ERROR ... IllegalStateException: Falha simulada por prefixo '!fail'
ERROR ... IllegalStateException: Falha simulada por prefixo '!fail'
ERROR ... IllegalStateException: Falha simulada por prefixo '!fail'
WARN  ... [DLQ] todo.created.dlq -> messageId=... x-death=[{count=1, reason=rejected, queue=todo.created.queue, ...}] body={"todoId":"...","title":"!fail teste dlq","action":"CREATED",...}
```

E na `todo.audit.queue` a mesma msg foi processada **com sucesso** — o audit-service não tem o hook `!fail`, então grava normal. Bom contra-exemplo do isolamento entre as DLX: a falha do notification não afetou o audit.

```powershell
# 2. Confirma na DLQ
docker exec rabbitmq rabbitmqctl list_queues name messages | Select-String "dlq"
```

```
todo.created.dlq    0   ← se o DlqListener já ackou
todo.audit.dlq      0
todo.deleted.dlq    0
todo.updated.dlq    0
```

> Como o `TodoEventDlqListener` ackka, a contagem volta a 0 logo após o log. Pra **manter** a msg na DLQ e poder inspecionar, comente o `@RabbitListener` do DlqListener temporariamente, ou olhe pelo painel web em tempo real.

---

## Como inspecionar manualmente

### Contar quantas mensagens estão presas

```powershell
docker exec rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged | Select-String "dlq"
```

### Espiar uma msg sem consumir (`ack_requeue_true` devolve pra fila)

```powershell
curl.exe -s -u guest:guest -X POST `
  http://localhost:15672/api/queues/%2F/todo.created.dlq/get `
  -H "content-type: application/json" `
  --data-binary '{"count":10,"ackmode":"ack_requeue_true","encoding":"auto"}'
```

A resposta vem em JSON com `payload`, `routing_key`, `properties.headers` (incluindo o `x-death` completo) e mais. É a forma mais detalhada de diagnóstico via terminal.

### Pelo painel web

`http://localhost:15672` → aba **Queues** → clicar na DLQ → seção **Get messages** (no rodapé). Permite `ack_requeue_true` (espia e devolve) ou `ack_requeue_false` (espia e remove).

---

## Reprocessamento (redrive)

O projeto **não** tem redrive automatizado. Em produção real, dois caminhos comuns:

1. **`RabbitMQ Shovel`** (plugin) — copia mensagens da DLQ pra fila principal. Configurável via painel web; bom pra one-shot operacional.
2. **Listener temporário de redrive** — sobe um `@RabbitListener` na DLQ que republica o body original na exchange principal com a routing key extraída do `x-death`, e acka. Útil quando você quer pré-filtrar (ex: só republicar msgs com `action=CREATED`).

Hoje, o fluxo manual é:

1. Olhar o WARN do `[DLQ]` no log, entender por que falhou.
2. Corrigir a causa (bug no handler, SMTP fora, schema do payload).
3. Pegar o `body` do log e republicar na exchange principal via painel web → **Publish message** na `todo.exchange`, com a rk apropriada e a propriedade `message-id` reaproveitada (pra que a dedupe do consumer funcione caso a msg original tenha sido parcialmente processada antes).

---

## Decisões de design notáveis

- **DLX dedicada pro audit.** Justificada em "Topologia no projeto" acima. Mais complexo que uma DLX só, mas mantém os domínios isolados.
- **`default-requeue-rejected: false` é obrigatório, não cosmético.** Já errei isso uma vez em outro projeto — sem essa linha, as msgs voltam pra fila principal num loop infinito e a DLX nunca vê nada. Vale revisar sempre que aparecer "minha DLQ não recebe" como sintoma. (Ver memory `project_rabbit_dlx_requeue.md`.)
- **DLQ listeners ackam em dev, alarmariam em prod.** Decisão deliberada pra didática — o sinal aparece no log imediatamente, sem precisar consultar o painel. Em produção, ack na DLQ é "perda silenciosa de evidência"; o caminho é manter na DLQ + alarme em cima da métrica.
- **`Message` em vez de `TodoEvent` nos DLQ listeners.** Robustez contra payload malformado — desserializar de novo no caminho de erro daria o mesmo erro, virando loop entre DLQ e... ela mesma.
- **Hook de teste `!fail`.** Vivem em código pra exercitar o caminho retry + DLQ sem precisar derrubar serviço ou bagunçar config. Trade-off aceito porque o prefixo é improvável em título real e a "falha" é controlada e local.

---

## Resumo

| Ingrediente | Onde fica | Por que importa |
|---|---|---|
| `x-dead-letter-exchange` na fila | `QueueBuilder.withArgument(...)` no `RabbitMQConfig` do consumer | Diz pro broker pra onde rotear msgs descartadas dessa fila |
| `x-dead-letter-routing-key` (opcional) | Mesmo lugar | Preserva a rk original; sem isso usa a de recepção |
| `default-requeue-rejected: false` | `application.yml` do consumer | Sem isso, msg rejeitada volta pra fila — DLX nunca dispara |
| `retry.max-attempts: 3` | `application.yml` do consumer | Quantas vezes tentar antes de desistir e rejeitar |
| DLX (`TopicExchange`) | `@Bean` no `RabbitMQConfig` | Recebe as msgs descartadas e roteia |
| DLQ (`Queue`) | `@Bean` no `RabbitMQConfig` | Destino final; consumida por DlqListener / inspeção |
| Binding `DLQ → DLX` | `@Bean Binding` | Conecta a DLX à DLQ com routing key/padrão |
| `DlqListener` | `@RabbitListener` na DLQ | Loga (dev) ou alarma (prod) |

Links cruzados:
- [`filas.md`](./filas.md) — visão por fila (principal + DLQ).
- [`exchange.md`](./exchange.md) — as 3 exchanges (`todo.exchange`, `todo.dlx`, `todo.audit.dlx`).
- [`publisher.md`](./publisher.md) — como a msg é colocada na fila principal.
- [`consumer.md`](./consumer.md) — como a msg é processada antes do retry esgotar.
