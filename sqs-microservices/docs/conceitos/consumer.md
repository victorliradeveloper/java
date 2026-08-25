# Consumer

Componente que **lê mensagens / eventos** de um broker e aplica o side-effect correspondente (enviar email, gravar audit log, processar pagamento). É o oposto do [producer](./producer.md) — onde o producer "injeta", o consumer "drena".

No projeto, dois consumers, cada um com um padrão de dedupe diferente:

| Serviço | Filas que consome | Padrão de dedupe |
|---|---|---|
| [`notification-service`](../../notification-service) | 3 filas filtradas (`todo-created-queue`, `todo-updated-queue`, `todo-deleted-queue`) + 3 DLQs | Collection separada `processed_messages` |
| [`audit-service`](../../audit-service) | 1 fila sem filtro (`todo-audit-queue`) + DLQ | `_id = MessageId` na própria collection de domínio |

---

## A trinca: producer, broker, consumer

```
 ┌──────────┐   publish()    ┌────────┐   poll/push    ┌──────────┐
 │ Producer ├───────────────►│ Broker ├───────────────►│ Consumer │
 └──────────┘                └────────┘                └──────────┘
   todo-service            SNS + SQS                notification-service
                          (LocalStack)              audit-service
```

| Papel | Responsabilidade | Não conhece |
|---|---|---|
| **Producer** | Gerar mensagem + publicar no broker | Quem vai consumir |
| **Broker** | Receber, persistir, entregar | Domínio do payload |
| **Consumer** | Ler do broker, aplicar side-effect | Quem produziu |

Detalhes do producer em [`docs/conceitos/producer.md`](./producer.md). Este doc cobre o lado do consumer.

---

## `@SqsListener` — o consumer em Spring Cloud AWS

A biblioteca [`spring-cloud-aws-starter-sqs`](https://docs.awspring.io/spring-cloud-aws/docs/3.2.1/reference/html/index.html#sqs-integration) (versão 3.2.1) abstrai todo o protocolo SQS via anotação:

```java
@SqsListener(SqsConfig.QUEUE_CREATED)
public void onTodoCreated(TodoEvent event, @Header(MessageHeaders.ID) UUID messageId) {
    // body já desserializado pra TodoEvent
    // messageId vem do MessageId do SQS — mesmo valor em reentregas
}
```

O que o Spring faz por baixo:

```
Container do listener (boot)
  │
  ├─ Abre N threads (default: 10)
  │
  ▼
[Loop infinito por thread]
  │
  ├─ awsClient.receiveMessage(queue, longPolling=20s)
  │     └─ retorna até 10 msgs (default)
  │
  ├─ pra cada msg recebida:
  │     ├─ Jackson desserializa Body → TodoEvent (parâmetro do método)
  │     ├─ extrai message attributes → @Header(...)
  │     ├─ chama o seu método handler
  │     ├─ se retorna normal → awsClient.deleteMessage(receiptHandle) ← ACK
  │     └─ se lança exceção → NÃO deleta → visibility timeout expira → SQS reentrega
  │
  └─ volta pro receiveMessage
```

**Princípio chave**: o "ack" no SQS é **`DeleteMessage`**. Sem chamar isso, a msg volta a ficar visível depois do `VisibilityTimeout` e é reentregue. Lançar exceção do handler **não chama** `DeleteMessage`, então é o sinal natural pro Spring "não ackar".

---

## Os dois consumers do projeto

### `notification-service` — fan-out filtrado + collection de dedupe

[`TodoEventListener`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java) tem 3 métodos:

```java
@SqsListener(SqsConfig.QUEUE_CREATED)
public void onTodoCreated(TodoEvent event, @Header(MessageHeaders.ID) UUID messageId) {
    process(event, messageId);
}

@SqsListener(SqsConfig.QUEUE_UPDATED)
public void onTodoUpdated(...) { process(event, messageId); }

@SqsListener(SqsConfig.QUEUE_DELETED)
public void onTodoDeleted(...) { process(event, messageId); }
```

Cada um lê de uma fila filtrada por `action`. A `FilterPolicy` é aplicada **no SNS** antes da msg chegar na fila — `created-queue` literalmente não recebe `DELETED` (não é o consumer que descarta; é o broker).

O método `process` (privado) é o coração:

```java
private void process(TodoEvent event, UUID messageId) {
    if (processedMessageRepository.existsById(messageId.toString())) {
        log.info("[DEDUPE] mensagem ja processada, descartada");
        return;   // sai do método, msg é ack'd normalmente
    }

    emailService.send(event);   // protegido por @CircuitBreaker + @Retry

    boolean inserted = processedMessageRepository.tryInsert(messageId.toString());
    if (!inserted) {
        log.warn("[DEDUPE] race detectada — outra thread tambem enviou");
    }
}
```

Ordem: **check → send → mark processed**. Detalhes do trade-off em [`docs/conceitos/idempotencia.md`](./idempotencia.md) e [`docs/conceitos/circuit-breaker.md`](./circuit-breaker.md).

### `audit-service` — fila única + dedupe natural via `_id`

[`TodoEventAuditListener`](../../audit-service/src/main/java/com/microservices/audit/listener/TodoEventAuditListener.java) tem 1 método só:

```java
@SqsListener(SqsConfig.QUEUE_AUDIT)
public void onTodoEvent(TodoEvent event, @Header(MessageHeaders.ID) UUID messageId) {
    TodoAuditLog log = TodoAuditLog.builder()
            .id(messageId.toString())   // ← _id = MessageId
            .aggregateId(event.todoId())
            .title(event.title())
            .eventType(event.action())
            .occurredAt(event.occurredAt())
            .recordedAt(LocalDateTime.now())
            .build();
    try {
        repository.insert(log);
    } catch (DuplicateKeyException e) {
        // já processei, ack normal
    }
}
```

Lê **todos** os eventos (fila sem `FilterPolicy`). Dedupe via `_id = messageId` na própria collection `todo_audit_log` — sem tabela auxiliar. O insert duplicado é a verificação atômica.

---

## Os dois padrões de dedupe

| Padrão | Quando usar | Exemplo | Trade-off |
|---|---|---|---|
| **Collection separada** (`processed_messages`) | Consumer com side-effect externo (email, HTTP, fila secundária). Não tem uma collection de domínio compatível como chave | `notification-service` | 1 write extra por msg, mas separação clara entre "processamento" e "domínio" |
| **`_id = messageId` na collection de domínio** | Consumer cuja única operação é gravar no DB e a tabela é append-only | `audit-service` | Sem write extra. Mais simples. Limitação: chave do domínio = chave do broker (sem semântica de negócio) |

### Por que `notification` não usa o padrão #2

A entidade de domínio do notification não é o "email enviado" — é mais conceitual ("registro de que processei a msg"). Não tem aggregate natural pra usar como `_id`. Padrão #1 (collection separada) é o ajuste certo.

### Por que `audit` não usa o padrão #1

Audit log é **append-only por natureza** — cada msg vira 1 linha imutável. `_id = MessageId` resolve dedupe E é a chave da entidade. Tabela extra seria duplicação.

Detalhes adicionais em [`docs/conceitos/idempotencia.md`](./idempotencia.md) §"Consumers SQS — Dedupe via messageId".

---

## Visibility timeout — o ack implícito

SQS não tem "ack" no sentido AMQP. O mecanismo é:

```
T+0s    receive-message → msg "em voo" (invisível pra outros consumers)
        Visibility Timeout começa (default 30s)
T+X     consumer chama delete-message → msg desaparece ✓
        OU
T+30s   timeout expira sem delete → msg volta a ficar visível ✗ outro consumer pode pegar
```

Spring Cloud AWS amarra:

| Resultado do handler | O que o Spring faz | Consequência |
|---|---|---|
| Retorna normalmente | `deleteMessage(receiptHandle)` | Msg removida da fila |
| Lança exceção | Nada | Visibility timeout expira → msg reentregue |

**Implicação**: pra mandar a msg de volta pra fila, basta **lançar uma exceção** do handler. Não há `ack()` explícito como em outros frameworks. Mais ergonômico, mas exige cuidado: exceção não tratada = retry.

### Por que o consumer **precisa** ser idempotente

Mesmo num sistema perfeito, a msg pode chegar 2x ao consumer:

1. **Broker reentrega** — SQS Standard é at-least-once por design.
2. **Visibility timeout expirou no meio do processamento** — handler levou 35s, msg voltou pra fila aos 30s, outro thread pegou. Resultado: 2 threads processando a mesma msg.
3. **Outbox publisher publicou 2x** — crash entre `SnsTemplate.send()` e `markPublished()`. Próximo ciclo republica.

Sem idempotência no consumer, qualquer um desses 3 cenários duplica o side-effect (email 2x, registro 2x).

Detalhes em [`docs/conceitos/idempotencia.md`](./idempotencia.md).

---

## DLQ — destino do consumer "envenenado"

Se o handler **sempre falha** pra uma msg específica (payload malformado, bug em borda, dependência fora), o ciclo "lança → visibility timeout → reentrega → lança" entra em loop infinito. SQS resolve com `RedrivePolicy`:

```
fila principal              DLQ
      │
      ├─ tentativa 1: handler lança
      ├─ tentativa 2: handler lança
      ├─ tentativa 3: handler lança
      └─ tentativa 4 ───────► move pra DLQ (sai da principal)
```

No projeto, `maxReceiveCount=3` (em [`localstack/init-aws.sh`](../../localstack/init-aws.sh)). Detalhes em [`docs/sqs/dlq.md`](../sqs/dlq.md).

### Listener da DLQ

Cada consumer tem um listener da DLQ que **loga em WARN**:

```java
@SqsListener(SqsConfig.QUEUE_CREATED_DLQ)
public void onCreatedDlq(String body, @Header(MessageHeaders.ID) UUID messageId) {
    log.warn("[DLQ] {} -> messageId={} body={}", QUEUE_CREATED_DLQ, messageId, body);
}
```

Importante: recebe **`String`** (não `TodoEvent`) — porque a causa #1 de cair na DLQ é **payload malformado**. Tentar desserializar de novo daria o mesmo erro em loop.

Em produção real, esse listener seria substituído por **alarme** em `ApproximateNumberOfMessagesVisible > 0` da DLQ. O log atual é trade-off explícito pra ter feedback imediato em dev.

---

## Concorrência: quantas mensagens em paralelo?

Spring Cloud AWS abre múltiplas threads por listener. Config padrão:

| Parâmetro | Default | Significado |
|---|---|---|
| `maxConcurrentMessages` | 10 | Quantas msgs em processamento simultâneo por listener |
| `maxMessagesPerPoll` | 10 | Quantas msgs vêm por chamada `ReceiveMessage` (máx SQS) |
| `pollTimeout` | 10s | Quanto tempo aguarda no long polling |

Pode customizar com `@SqsListener(value = "...", maxConcurrentMessages = "5")` ou via `SqsContainerOptions` programaticamente.

### Implicações da concorrência

**1. Race condition no dedupe** — duas threads processam a mesma msg simultaneamente (visibility timeout muito curto vs processamento longo). O `processed_messages.tryInsert` é atômico — uma vence, a outra ganha `false` e loga WARN.

**2. Beans são compartilhados** — listener é singleton, threads dividem o mesmo `EmailService`, `processedMessageRepository`, etc. Não usar campo mutável no listener.

**3. Throughput vs latência** — mais threads = mais throughput mas pressão maior na dependência (SMTP, Mongo). Combine com Circuit Breaker pro consumer não derrubar a dependência.

---

## Resiliência: Circuit Breaker no consumer

O consumer que chama dependência externa instável (SMTP, REST de terceiro) deve protegê-la com Circuit Breaker. No projeto:

```java
@CircuitBreaker(name = "smtp")
@Retry(name = "smtp")
public void send(TodoEvent event) {
    // ... SMTP call
}
```

Fluxo combinado:

```
@SqsListener(...) → process(event, messageId)
  ├─ existsById → SKIP if already processed
  ├─ emailService.send(event)
  │     ├─ @CircuitBreaker:
  │     │    ├─ CLOSED: tenta, conta sucesso/falha
  │     │    ├─ OPEN:   throws CallNotPermittedException (fail-fast)
  │     │    └─ HALF_OPEN: 3 testes pra decidir
  │     └─ @Retry: até 3 tentativas, backoff exponencial 200→400→800ms
  │
  ├─ Sucesso → tryInsert(messageId)
  │
  └─ Exceção propaga → @SqsListener NÃO acka → SQS reentrega
        └─ Após 3 falhas (visibility expirou 3x) → DLQ
```

Detalhes em [`docs/conceitos/circuit-breaker.md`](./circuit-breaker.md).

### Por que o `audit-service` não tem CB

Audit só chama o Mongo local — mesma rede, mesmo container stack. Driver do Mongo já tem retry/timeout interno. CB adicional seria overhead sem benefício real. CB faz sentido em **dependência externa instável**, não em recurso interno controlado.

---

## Long polling — não confundir com visibility timeout

São coisas diferentes:

| Conceito | O que controla | Configurado em |
|---|---|---|
| **Long polling** (`ReceiveMessageWaitTimeSeconds`) | Quanto tempo o `ReceiveMessage` espera por mensagens novas antes de retornar vazio | 20s no projeto (`init-aws.sh`) — máximo permitido pelo SQS |
| **Visibility timeout** | Quanto tempo a msg fica invisível pra outros consumers após `ReceiveMessage` | 30s default |

Long polling **reduz custo** (1 chamada cobre 20s em vez de loop apertado) e **reduz latência** (msg chega → retorna imediato). Detalhes em [`docs/sqs/long-polling.md`](../sqs/long-polling.md) e [`docs/sqs/visibility-timeout.md`](../sqs/visibility-timeout.md).

---

## Fluxo end-to-end de uma mensagem (caminho feliz)

```
T+0s     OutboxPublisher publica no SNS
T+0.01s  SNS distribui via FilterPolicy:
         ├─ action=CREATED → todo-created-queue
         └─ sem filtro     → todo-audit-queue

T+0.05s  Spring (notification) está em long polling no todo-created-queue
         ReceiveMessage retorna na hora com a msg

T+0.05s  Spring (audit) idem no todo-audit-queue

T+0.06s  notification-service.process(event, messageId):
           ├─ existsById(messageId) → false (msg nova)
           ├─ emailService.send(event)
           │    └─ CB CLOSED → SMTP envia (~500ms)
           ├─ tryInsert(messageId) → true (gravou)
           └─ retorna normal → Spring deleta msg do SQS

T+0.55s  audit-service.onTodoEvent(event, messageId):
           ├─ repository.insert(TodoAuditLog com _id=messageId)
           │    └─ sucesso (msg nova)
           └─ retorna normal → Spring deleta msg do SQS
```

Tudo isso em paralelo entre os 2 consumers. Falha de um não afeta o outro — isolamento de fato.

---

## Pegadinhas comuns

| Pegadinha | Sintoma | Como evitar |
|---|---|---|
| `try/catch (Exception)` engolindo erro no handler | Msg é ack'd mesmo com falha real, side-effect perdido sem rastro | Só capturar exceção quando o tratamento é parte do fluxo. Em erro de verdade, deixar propagar |
| Dedupe **depois** de side-effect crítico | Crash entre side-effect e dedupe → duplica na próxima entrega. **Pode ser aceitável** se idempotente | Conscientemente escolher entre "perde raro" (dedupe-antes) e "duplica raro" (dedupe-depois) |
| Handler com processamento longo (>visibility timeout) | Msg duplicada (outro consumer pega antes do `delete`) | Chamar `Visibility.changeTo(N)` no listener pra estender, ou aumentar default no `set-queue-attributes` |
| Esquecer dedupe | Email duplicado em redelivery legítima do SQS | Tratar idempotência como **requisito**, não otimização |
| Campo mutável no listener | Data corruption em concorrência | Listener é singleton — só campos `final` injetados |
| Listener da DLQ tipado em `TodoEvent` | Listener da DLQ falha tentando desserializar payload malformado | Receber `String` no listener da DLQ — `body` pode estar quebrado |
| Long polling ausente | Throughput vazio com 1000 chamadas/s "estou vivo?" | `ReceiveMessageWaitTimeSeconds=20` na fila (SQS API) ou `pollTimeout` no listener config |
| `@Transactional` no handler do `@SqsListener` | TX abre, msg é ack'd, mas commit roda em background → race entre ack e commit | Não usar `@Transactional` no método anotado com `@SqsListener` — sub-chamadas internas podem ser transacionais |

---

## Quando o consumer **não** deveria estar idempotente

Praticamente nunca. Há **um** caso aceitável:

- Side-effect é **naturalmente idempotente** (ex: `UPDATE todos SET title='X' WHERE id=Y`) — chamar 2x dá o mesmo estado final. Aí dedupe não é estritamente necessário.

Mesmo nesse caso, dedupe explícito ajuda em:
- **Observabilidade** (saber que houve redelivery)
- **Side-effects secundários** (métrica de "emails enviados" — se conta 2x, métrica fica errada)

A regra default deve ser: **todo consumer é idempotente**. Documentar exceções, não a regra.

---

## Pra entrevista

**Pergunta clássica**: *"Como você consome uma fila SQS em Spring?"*

Resposta em 3 frases:
1. **`@SqsListener(name)`** do `spring-cloud-aws-starter-sqs`. Método recebe o payload já desserializado e headers via `@Header`.
2. **Ack implícito**: handler retorna normal → Spring chama `DeleteMessage`. Handler lança exceção → não deleta → msg reentregue após visibility timeout.
3. **Idempotência obrigatória**: SQS é at-least-once. Padrão de dedupe via `processed_messages` table ou `_id = MessageId` na collection de domínio (Mongo `DuplicateKeyException` resolve em ambos).

**Follow-up clássico**: *"E se a msg falhar pra sempre?"*

Resposta: **DLQ via `RedrivePolicy`**. Após `maxReceiveCount` falhas (3 no projeto), SQS move pra fila secundária (`*-dlq`). Em produção, alarme em `ApproximateNumberOfMessagesVisible > 0` da DLQ. Inspeção manual + correção do bug + redrive (`start-message-move-task`).

**Follow-up técnico**: *"O que acontece se o handler levar 60s e o visibility timeout for 30s?"*

Resposta: aos 30s, outro consumer pega a mesma msg → 2 threads processando em paralelo → side-effect potencialmente duplicado. Solução: chamar `Visibility.changeTo(120)` no listener pra estender o lock, ou aumentar o default da fila. Idempotência continua sendo a defesa principal.

---

## Referências

- [`docs/conceitos/producer.md`](./producer.md) — o lado simétrico desta conta
- [`docs/conceitos/idempotencia.md`](./idempotencia.md) — pré-requisito de qualquer consumer at-least-once
- [`docs/conceitos/circuit-breaker.md`](./circuit-breaker.md) — proteção do consumer quando chama dependência externa
- [`docs/sqs/visibility-timeout.md`](../sqs/visibility-timeout.md) — o "ack implícito" do SQS
- [`docs/sqs/long-polling.md`](../sqs/long-polling.md) — como reduzir custo e latência do `ReceiveMessage`
- [`docs/sqs/dlq.md`](../sqs/dlq.md) — destino das msgs envenenadas
- [`docs/sqs/message-attributes.md`](../sqs/message-attributes.md) — como o `action` chega no consumer
- [`.spec/03-patterns/fan-out.md`](../../.spec/03-patterns/fan-out.md) — 4 filas, 2 consumers, padrões diferentes
- [Spring Cloud AWS — SQS](https://docs.awspring.io/spring-cloud-aws/docs/3.2.1/reference/html/index.html#sqs-integration)
- [AWS SQS — Amazon SQS short and long polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html)
