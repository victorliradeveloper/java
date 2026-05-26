# Consumer

## Visão Geral

No projeto, o **consumer** é o `notification-service`. Ele **não expõe endpoints REST de domínio** — sua razão de existir é ficar escutando filas do RabbitMQ e reagir aos eventos publicados pelo `todo-service`.

```
[todo.exchange] ──route──► todo.created.queue ──┐
                       │                        │
                       ├─► todo.updated.queue ──┼──► notification-service (TodoEventListener)
                       │                        │
                       └─► todo.deleted.queue ──┘
```

O consumer é a outra ponta da arquitetura event-driven: enquanto o publisher dispara fatos e segue a vida, o consumer **consome esses fatos no seu próprio ritmo**, em outro processo, sem nenhum acoplamento de chamada direta.

---

## O modelo "push" do RabbitMQ

Diferente do SQS (onde o consumer chama `receive-message` em loop — modelo *pull*), o RabbitMQ usa **push**: o broker entrega a mensagem ao consumer assim que ela chega na fila e há um consumer ativo registrado.

O fluxo é:

1. O consumer abre uma conexão AMQP com o broker.
2. Cria um *channel* e declara o consumo da fila com `basic.consume`.
3. O broker passa a empurrar mensagens pelo canal conforme chegam.
4. O consumer processa e devolve um `basic.ack` (ou `basic.nack`).
5. Só depois do `ack`, o broker remove a mensagem da fila.

A diferença mais prática: no SQS você paga a latência do polling; no RabbitMQ a entrega é praticamente imediata.

---

## Anatomia do consumer no projeto

### 1. Configuração espelhada — `notification-service/.../config/RabbitMQConfig.java`

O `notification-service` tem o **mesmo** `RabbitMQConfig` do publisher: declara a exchange, as três filas e os três bindings. Isso é intencional:

- Cada serviço declara a topologia de que depende.
- A declaração é idempotente — se já existir, o RabbitMQ ignora.
- Não importa quem sobe primeiro: a topologia estará lá.

Também declara o `Jackson2JsonMessageConverter`, que aqui faz o caminho inverso: bytes JSON → objeto `TodoEvent`. O conversor usa o header `__TypeId__` que o publisher anexa pra escolher a classe alvo da desserialização.

> **Detalhe interessante:** o `TodoEvent` do consumer é uma **classe diferente** da do publisher (`com.microservices.notification.event.TodoEvent` vs `com.microservices.todo.event.TodoEvent`). Mesma estrutura, pacotes diferentes. O Spring AMQP resolve isso automaticamente porque o `Jackson2JsonMessageConverter` aceita mapeamento por estrutura — não exige FQN igual. Em produção, vale considerar uma biblioteca compartilhada de eventos pra evitar duplicação.

### 2. O listener — `listener/TodoEventListener.java`

```java
@Slf4j
@Component
public class TodoEventListener {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CREATED)
    public void onTodoCreated(TodoEvent event) {
        log.info("[NOTIFICATION] Todo CRIADO -> id={} | title='{}' | em={}",
                 event.todoId(), event.title(), event.occurredAt());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_UPDATED)
    public void onTodoUpdated(TodoEvent event) { /* ... */ }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DELETED)
    public void onTodoDeleted(TodoEvent event) { /* ... */ }
}
```

A anotação `@RabbitListener(queues = ...)` é o coração do consumer. Por baixo dela, o Spring AMQP:

1. No startup, registra um `SimpleMessageListenerContainer` (ou `DirectMessageListenerContainer`) por método anotado.
2. O container abre conexão e channel com o broker, declara `basic.consume` na fila informada.
3. Quando uma mensagem chega, o container:
   - Lê os bytes do payload.
   - Usa o `Jackson2JsonMessageConverter` (achado no contexto Spring) pra desserializar em `TodoEvent`.
   - Invoca o método anotado com o objeto desserializado.
4. **Se o método retorna sem exceção**, o container envia `basic.ack` ao broker → a mensagem é descartada da fila.
5. **Se o método lança exceção**, o comportamento depende da configuração (ver "Tratamento de erro" abaixo).

### 3. Uma fila, um método

O projeto separa propositalmente um método por fila — `onTodoCreated`, `onTodoUpdated`, `onTodoDeleted`. Outra opção seria um único método que recebe todos os eventos e despacha por `event.action()`. A separação em três métodos:

- Deixa o código autoexplicativo.
- Permite **paralelizar** o consumo (cada fila pode ter sua própria concorrência via `concurrency` no `@RabbitListener`).
- Permite, no futuro, mover uma das responsabilidades pra outro serviço sem mexer no resto.

---

## Acknowledgements: o que garante "at-least-once"

Por padrão, o Spring AMQP usa **ack automático** (modo `AUTO`):

- Sucesso (sem exceção) → `ack` → mensagem sai da fila.
- Exceção → `nack` com requeue → mensagem **volta** pra fila e é re-entregue.

Isso dá a garantia *at-least-once*: a mensagem chega pelo menos uma vez ao consumer. Mas tem um efeito colateral: **uma exceção persistente faz a mensagem ficar circulando em loop** (consumer pega → falha → requeue → pega de novo → ...). É um "poison message".

Padrões reais de mercado pra resolver isso:

- **Dead Letter Queue (DLQ)** — declarar uma DLQ e configurar a fila principal pra mandar mensagens recusadas pra lá depois de N tentativas. Hoje o projeto não tem DLQ configurada.
- **Retry com backoff** — `spring.rabbitmq.listener.simple.retry.enabled=true` com `max-attempts` e `initial-interval`. O Spring tenta N vezes antes de desistir e mandar pra DLQ (se houver).

A garantia *at-least-once* também implica que **o consumer precisa ser idempotente**: a mesma mensagem pode chegar duas vezes (em retry, em republish do publisher, em network glitch). Hoje o `notification-service` só faz `log.info()`, então naturalmente é idempotente. Quando o consumer começar a fazer side effects (gravar no banco, mandar e-mail), idempotência vira preocupação central.

---

## Concorrência e prefetch

Dois parâmetros que tipicamente importam num consumer real:

- **`concurrency`** (`@RabbitListener(concurrency = "3-10")`): quantas threads simultaneamente consomem da mesma fila. Mais threads = mais throughput, até o ponto em que o gargalo vira o downstream (banco, API externa).
- **`prefetch`** (`spring.rabbitmq.listener.simple.prefetch=10`): quantas mensagens o broker pode entregar a um consumer **antes** dele ackar. Prefetch alto = throughput maior, mas se o consumer cair, mais mensagens precisam ser re-entregues.

Hoje o projeto usa os **defaults do Spring AMQP** (concurrency = 1, prefetch = 250). Suficiente pra carga didática, mas em produção esses são botões importantes pra ajustar.

---

## Como testar manualmente

### 1. Disparar a publicação

```powershell
Invoke-RestMethod -Uri 'http://localhost:8081/todos' -Method Post `
  -Headers @{ 'Content-Type'='application/json' } `
  -Body '{"title":"testar consumer","description":"ver log"}'
```

### 2. Observar o consumer processar em tempo real

```powershell
docker logs -f notification-service
```

Saída esperada (quando tudo funciona):

```
[NOTIFICATION] Todo CRIADO -> id=486c3c4d-... | title='testar consumer' | em=2026-05-26T18:05:39
```

### 3. Confirmar que a fila esvaziou

```powershell
docker exec rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged
```

- `messages = 0` em todas → consumer está acompanhando o ritmo.
- `messages_unacknowledged > 0` → mensagem **entregue** ao consumer mas ainda não foi ackada (processamento em andamento, ou consumer travado).
- `messages_ready > 0` e nada caindo → **não há consumer ativo na fila** (provavelmente o `notification-service` não subiu, ou perdeu conexão).

### 4. Simular um problema

Pra ver o que acontece quando o consumer está fora:

```powershell
docker stop notification-service
Invoke-RestMethod -Uri 'http://localhost:8081/todos' -Method Post `
  -Headers @{ 'Content-Type'='application/json' } `
  -Body '{"title":"fila enchendo","description":""}'

docker exec rabbitmq rabbitmqctl list_queues name messages
```

A mensagem fica na fila esperando. Subindo o consumer de novo, ele drena automaticamente:

```powershell
docker start notification-service
docker logs -f notification-service
```

Esse é o **valor central de uma fila**: desacoplar disponibilidade. O publisher não precisou esperar nem falhar — o broker absorveu, e o consumer recuperou no seu próprio tempo.
