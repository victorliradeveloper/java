# Pattern — Dead Letter Queue (DLQ)

Holding pen pra mensagens que falharam N vezes consecutivas no consumer. Isola **poison messages** (payload malformado, dependência indisponível, bug no handler) do fluxo normal, sem ficar fazendo loop infinito na fila principal.

Implementado em [`localstack/init-aws.sh`](../../localstack/init-aws.sh) (provisionamento) e [`notification-service`](../../notification-service) (listener de observabilidade).

---

## Problema que resolve

Sem DLQ, uma mensagem que **sempre falha** no consumer fica em loop:

```
receive → handler throws → no ack → visibility timeout → receive → throws → ...
```

Sintomas:

- **CPU desperdiçada** processando a mesma mensagem ruim repetidamente.
- **Fila principal entope** — mensagem ruim bloqueia o slot de processamento (depende da config do listener; com concorrência baixa, atrasa todas as outras).
- **Log spam** — mesma exceção a cada visibility timeout (30s default).
- **Sem visibilidade**: ninguém sabe que tem mensagem morrendo, a não ser que olhe o log na hora certa.

Casos que causam isso no projeto:

| Origem | Exemplo |
|---|---|
| Publisher novo subiu schema novo | Campo obrigatório que o consumer antigo não conhece, Jackson falha |
| Mensagem manual de teste | O `send-message` que você rodou com `{id,title}` em vez de `TodoEvent` completo |
| Bug no handler | NPE em um caso de borda específico |
| Dependência fora | Mongo / SMTP fora — depende de onde a falha acontece (ver §"Modos de falha cobertos") |

---

## Ideia central

Configurar `RedrivePolicy` em cada fila principal apontando pra uma **DLQ** (fila irmã, sufixo `-dlq`). Após N entregas com falha (`maxReceiveCount=3`), o SQS move a mensagem automaticamente pra DLQ — sem código do consumer fazer nada.

```
fila principal              DLQ
      │
      ├─ tentativa 1: falha
      ├─ tentativa 2: falha
      ├─ tentativa 3: falha
      └─ tentativa 4 ───────► move pra DLQ (sai da principal)
```

A mensagem fica retida na DLQ até inspeção manual + correção do bug + redrive (volta pra principal pra reprocessar).

---

## Componentes

### 1. Filas + RedrivePolicy ([`localstack/init-aws.sh`](../../localstack/init-aws.sh))

Para cada fila principal existe uma DLQ correspondente:

| Principal | DLQ |
|---|---|
| `todo-created-queue` | `todo-created-dlq` |
| `todo-updated-queue` | `todo-updated-dlq` |
| `todo-deleted-queue` | `todo-deleted-dlq` |

A `RedrivePolicy` é uma string JSON-encoded com o ARN da DLQ e o `maxReceiveCount`:

```json
{
  "RedrivePolicy": "{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:todo-created-dlq\",\"maxReceiveCount\":\"3\"}"
}
```

`maxReceiveCount=3` significa: o SQS entrega até 3 vezes; na 4ª tentativa, em vez de entregar de novo, move pra DLQ.

### 2. Constantes ([`SqsConfig.java`](../../notification-service/src/main/java/com/microservices/notification/config/SqsConfig.java))

Nomes das DLQs como constantes pra evitar drift entre script de provisionamento e código:

```java
public static final String QUEUE_CREATED_DLQ = "todo-created-dlq";
public static final String QUEUE_UPDATED_DLQ = "todo-updated-dlq";
public static final String QUEUE_DELETED_DLQ = "todo-deleted-dlq";
```

### 3. Listener de observabilidade ([`TodoEventDlqListener.java`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventDlqListener.java))

Loga `WARN` com `messageId` + body bruto quando uma mensagem cai numa DLQ. Recebe `String` (não `TodoEvent`) porque o motivo mais comum de cair na DLQ é justamente body malformado — tentar desserializar de novo causaria o mesmo erro.

```java
@SqsListener(SqsConfig.QUEUE_CREATED_DLQ)
public void onCreatedDlq(String body, @Header(MessageHeaders.ID) UUID messageId) {
    log.warn("[DLQ] {} -> messageId={} body={}", SqsConfig.QUEUE_CREATED_DLQ, messageId, body);
}
```

O listener **acka** (auto-delete no return). Em produção real esse trade-off é diferente — ver §"Trade-offs".

---

## Modos de falha cobertos

Sutil: DLQ **não** captura todas as falhas do projeto hoje. Depende de **onde** a exceção acontece no ciclo do consumer:

| Falha | Acontece antes do dedupe? | Vai pra DLQ? |
|---|---|---|
| Payload JSON inválido | Sim (desserialização antes do método) | ✅ Sim |
| Mongo fora (dedupe falha) | Sim (`tryInsert` lança) | ✅ Sim |
| SMTP fora / email rejeitado | **Não** (dedupe já gravou `processed_messages`) | ❌ Não — perda silenciosa |

O caso SMTP é **design explícito** documentado em [`TodoEventListener.java`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java) (linha do `alreadyProcessed`): dedupe-antes-do-send escolhe "perde raro" em vez de "duplica raro". Se quisermos que falhas de SMTP também passem por DLQ, precisaríamos inverter pra dedupe-depois-do-send (e aceitar duplicação) ou rastrear o email separadamente (tabela `email_attempts`).

Esse trade-off está registrado em [`01-issues/closed/idempotency.md`](../01-issues/closed/idempotency.md).

---

## Inspeção e redrive (comandos)

### Quantas mensagens em cada DLQ

```powershell
docker exec localstack awslocal sqs get-queue-attributes `
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/todo-created-dlq `
  --attribute-names ApproximateNumberOfMessages
```

### Espiar uma mensagem sem consumir

```powershell
docker exec localstack awslocal sqs receive-message `
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/todo-created-dlq `
  --max-number-of-messages 10 `
  --visibility-timeout 0
```

`--visibility-timeout 0` devolve a mensagem na hora — útil pra inspecionar sem afetar a contagem.

### Redrive (devolve da DLQ pra fila principal)

Depois de corrigir o bug que causou a parada:

```powershell
docker exec localstack awslocal sqs start-message-move-task `
  --source-arn arn:aws:sqs:us-east-1:000000000000:todo-created-dlq
```

O SQS move tudo da DLQ pra fila configurada como source na `RedrivePolicy` (no nosso caso, `todo-created-queue`). A partir daí o consumer normal retoma o processamento.

### Esvaziar uma DLQ (apenas em dev)

```powershell
docker exec localstack awslocal sqs purge-queue `
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/todo-created-dlq
```

⚠️ Apaga **tudo**. Em produção: nunca purga DLQ sem antes salvar o conteúdo (perda de evidência pra debug).

---

## Trade-offs

### O listener acka (consome) as mensagens da DLQ

**Decisão**: o `TodoEventDlqListener` deixa o `@SqsListener` deletar a mensagem ao retornar normalmente.

**Por quê**: dá sinal imediato em dev (uma linha de `WARN` no log = aconteceu uma vez), sem poluir o log a cada visibility timeout. Se não consumisse, a mesma mensagem reapareceria a cada 30s e a saída ficaria irreproduzível.

**Quando mudar**: em produção real esse listener não deveria existir. O padrão é:

1. Não consumir a DLQ automaticamente — deixa as mensagens retidas.
2. Alarme em `ApproximateNumberOfMessagesVisible > 0` (CloudWatch / Prometheus).
3. Inspeção manual via `receive-message`.
4. Redrive após fix.

Pra migrar: remover `TodoEventDlqListener`, adicionar `@Scheduled` que faz `get-queue-attributes` periodicamente e emite métrica.

### `maxReceiveCount = 3`

Padrão razoável. **Subir** se o handler tem retries internos com backoff e 3 não é suficiente pra cobrir falhas transientes (ex: rede flaky). **Descer** se as falhas tendem a ser determinísticas (payload errado) — não adianta tentar 10 vezes algo que vai falhar 10 vezes.

### DLQs não têm DLQ

Por design. Se o `TodoEventDlqListener` falhar (improvável — só faz log), a mensagem volta pra DLQ e tenta de novo. Sem `RedrivePolicy` na DLQ, ela fica lá até ser consumida ou apagada.

### Retenção

LocalStack default = 4 dias. SQS produção default = 4 dias, máximo 14. Pra debug útil em produção, setar pra 14 dias via `MessageRetentionPeriod`.

---

## Sintomas de problema

| Sintoma | Causa provável | Onde olhar |
|---|---|---|
| DLQ cresce sem parar | Bug determinístico no consumer ou schema drift entre publisher e consumer | `docker logs notification-service` filtrando por `[DLQ]` |
| Mensagem fica "presa" na fila principal sem ir pra DLQ | `RedrivePolicy` não foi aplicada (init-aws.sh não rodou ou falhou silenciosamente) | `awslocal sqs get-queue-attributes --queue-url ... --attribute-names RedrivePolicy` |
| Email não chega mas DLQ está vazia | Falha de SMTP pós-dedupe — não passa por DLQ (ver §"Modos de falha cobertos") | log do `EmailService` |
| Redrive não devolve as mensagens | `start-message-move-task` exige `source-arn` da DLQ, não da principal | revisar comando |

---

## O que dá pra mudar e o que **não** dá

### Dá pra mudar livre

- `maxReceiveCount` por fila — operacional.
- `MessageRetentionPeriod` da DLQ — operacional.
- Adicionar `@Scheduled` que monitora profundidade de cada DLQ — substituto razoável pro listener em ambiente sem CloudWatch.

### Cuidado redobrado

- **Apontar a `RedrivePolicy` de duas filas pra mesma DLQ** — perde a separação por tipo de evento. No projeto evitamos isso pra log ficar mais limpo.
- **Mudar a estratégia de dedupe** no listener — pode mover falhas de SMTP pra dentro do raio de cobertura da DLQ, mas custa duplicação. Decisão registrada em [`01-issues/closed/idempotency.md`](../01-issues/closed/idempotency.md).

### Não funciona

- **Tentar resolver poison message com retry no código** — alguns frameworks oferecem `@Retryable`. Não substitui DLQ: retry no processo morre se o pod cai; DLQ é durável no broker.
- **DLQ sem alarme de profundidade em produção** — mensagens podem acumular por dias até alguém perceber. Sem alarme, DLQ é arquivo morto.

---

## Quando usar este pattern

**Use** quando:

- Há consumer com handler que pode falhar de forma determinística (bug, schema, dependência fora).
- Você quer isolar mensagens ruins sem perder o sinal (evidência preservada na DLQ).
- O custo de duplicação no consumer já está endereçado (idempotência).

**Não use** quando:

- A operação é totalmente sincrônica e o caller espera erro imediato (use HTTP error em vez de fila).
- Mensagens com falha precisam de retry imediato em ordem — DLQ "quebra a fila"; ordem se perde.

---

## Referências

- [`02-anti-patterns/general.md`](../02-anti-patterns/general.md) — princípios de fail-fast e visibility.
- [`03-patterns/outbox.md`](./outbox.md) — par natural: outbox garante "evento sai", DLQ garante "evento ruim não afunda a fila".
- [`01-issues/closed/idempotency.md`](../01-issues/closed/idempotency.md) — trade-off dedupe-antes vs dedupe-depois (afeta cobertura da DLQ).
- AWS docs: [Amazon SQS dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html).
