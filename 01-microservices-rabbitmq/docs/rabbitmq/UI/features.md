# UI Features (Management Console)

A aba **Queues and Streams** do RabbitMQ Management UI (`http://localhost:15672`) mostra uma coluna chamada **Features**. Cada badge ali resume um argumento ou flag com que a fila foi declarada. Útil pra bater olho e validar topologia sem precisar inspecionar a fila uma a uma.

Este doc explica cada badge que aparece no projeto e o que ele implica.

---

## Mapa do projeto

| Fila | Features | Declarada em |
|---|---|---|
| `todo.created.queue` | D, DLX, DLK | [`notification-service/RabbitMQConfig`](../../../notification-service/src/main/java/com/microservices/notification/config/RabbitMQConfig.java) + [`todo-service/RabbitMQConfig`](../../../todo-service/src/main/java/com/microservices/todo/config/RabbitMQConfig.java) |
| `todo.updated.queue` | D, DLX, DLK | idem |
| `todo.deleted.queue` | D, DLX, DLK | idem |
| `todo.audit.queue`   | D, DLX      | [`audit-service/RabbitMQConfig`](../../../audit-service/src/main/java/com/microservices/audit/config/RabbitMQConfig.java) |
| `todo.created.dlq`   | D           | [`notification-service/RabbitMQConfig`](../../../notification-service/src/main/java/com/microservices/notification/config/RabbitMQConfig.java) |
| `todo.updated.dlq`   | D           | idem |
| `todo.deleted.dlq`   | D           | idem |
| `todo.audit.dlq`     | D           | [`audit-service/RabbitMQConfig`](../../../audit-service/src/main/java/com/microservices/audit/config/RabbitMQConfig.java) |

---

## Badges presentes

### `D` — Durable

A fila **sobrevive a restart do broker**. Sem esse flag, ela seria deletada na hora que o RabbitMQ caísse — péssimo pra qualquer uso de produção.

> **Atenção:** durabilidade da fila ≠ durabilidade das mensagens. Pra mensagem persistir em disco também, ela precisa ser publicada com `delivery_mode=2` (persistent). Spring AMQP faz isso por default via `RabbitTemplate.convertAndSend(...)`.

Todas as filas do projeto têm `D`. Sem ele, a topologia inteira sumiria após `docker compose restart rabbitmq`.

---

### `DLX` — Dead Letter Exchange

A fila tem `x-dead-letter-exchange` configurado. Quando uma mensagem é:
- **rejeitada sem requeue** (consumer chamou `basic.reject` com `requeue=false`, ou esgotou o retry do Spring AMQP), ou
- **expirou por TTL** (`x-message-ttl`), ou
- **estourou o limite de tamanho da fila** (`x-max-length`),

o RabbitMQ a **roteia automaticamente** pra exchange configurada nesse argumento. É o mecanismo que faz a [DLQ](../dlq.md) funcionar — sem `DLX` na fila principal, mensagens problemáticas seriam descartadas silenciosamente.

No projeto, o valor é `todo.dlx` (declarado no [`notification-service/RabbitMQConfig`](../../../notification-service/src/main/java/com/microservices/notification/config/RabbitMQConfig.java)).

> **Importante:** as filas `.dlq` **não** têm `DLX` — é proposital. Se a DLQ tivesse DLX, uma mensagem morta poderia morrer de novo, criando loop infinito. DLQ é onde a mensagem **para**.

---

### `DLK` — Dead Letter Routing Key

A fila tem `x-dead-letter-routing-key`. Quando manda uma mensagem morta pra DLX, usa **essa** routing key em vez da que a mensagem tinha originalmente.

Sem `DLK`, o RabbitMQ preserva a routing key original — funciona, mas é implícito. Setar `DLK` é defesa-em-profundidade: mesmo que algum middleware reescreva a routing key no caminho, o destino na DLQ continua determinístico.

Compare na imagem:

| Fila | Features | Estratégia |
|---|---|---|
| `todo.created.queue` | D, **DLX**, **DLK** | Explícita — `x-dead-letter-routing-key=todo.created` |
| `todo.audit.queue`   | D, **DLX**          | Implícita — preserva a routing key original |

Ambas estratégias funcionam. A explícita (`DLK`) é mais defensiva; a implícita é mais enxuta. Escolha local.

---

## Outros badges que você pode encontrar fora do projeto

Não aparecem aqui, mas é útil saber identificar:

| Badge | Argumento | Significa |
|---|---|---|
| `AD`   | `auto-delete=true`         | Fila some quando o último consumer desconecta |
| `Excl` | `exclusive=true`           | Só a conexão que declarou pode usar — some quando essa conexão fecha |
| `TTL`  | `x-message-ttl`            | Mensagens expiram após N ms (e vão pra DLX se houver) |
| `Lim`  | `x-max-length` / `x-max-length-bytes` | Limite de tamanho — quando estoura, mensagens mais antigas vão pra DLX |
| `Lazy` | `x-queue-mode=lazy`        | Mensagens em disco, não em RAM — pra filas gigantes onde latência baixa não importa |
| `Q`    | quorum queue               | Réplica via Raft (HA) — substitui mirrored queues do RabbitMQ 3.x |
| `Stream` | stream                   | Tipo `stream` (RabbitMQ 3.9+) — log append-only, replay possível |

---

## Como usar isso na prática

**Validar topologia depois de deploy:**
Abra o UI, olhe a coluna Features das filas principais. Se faltar `DLX` numa fila que deveria ter, a DLQ não vai receber nada quando algo falhar — falha silenciosa pior que ruidosa.

**Diagnosticar `PRECONDITION_FAILED` na inicialização do app:**
Esse erro acontece quando o app tenta redeclarar uma fila existente com argumentos diferentes. A coluna Features mostra o que o broker **tem**; o código do app mostra o que ele **quer**. Comparar os dois localiza o desalinhamento (ver [shared-queue-args](../../15-sqs-microservices) na memória do projeto).

**Conferir que `.dlq` não tem `DLX`:**
Se aparecer `DLX` numa fila `.dlq`, é bug de config — risco de loop.
