# Publisher (Producer)

## Visão Geral

No projeto, o **publisher** é o `todo-service`. Sempre que uma operação de escrita acontece no domínio (`POST`, `PUT`, `DELETE` em `/todos`), o serviço **publica um evento** no RabbitMQ para que outros consumidores reajam de forma assíncrona, sem que o `todo-service` precise saber quem está ouvindo.

```
HTTP request ─► TodoController ─► TodoService ─► repository.save()
                                          └─────► rabbitTemplate.convertAndSend()
                                                          │
                                                          ▼
                                                  [todo.exchange]
                                                          │
                                            ┌─────────────┼─────────────┐
                                            ▼             ▼             ▼
                                  todo.created.queue  todo.updated.queue  todo.deleted.queue
```

O publisher não conhece as filas. Ele só conhece **a exchange** e **a routing key**. Quem decide pra que fila a mensagem vai é o próprio broker (RabbitMQ), guiado pelos *bindings* que foram declarados na inicialização.

---

## Por que publicar em vez de chamar direto?

O `todo-service` poderia chamar o `notification-service` por HTTP. Não faz. Os motivos centrais:

1. **Desacoplamento temporal** — se o consumer estiver fora do ar, a mensagem fica esperando na fila. O publisher não trava nem falha.
2. **Desacoplamento de conhecimento** — o publisher não precisa saber quantos consumers existem, nem onde estão. Pode haver 0, 1 ou N consumidores; o código do publisher não muda.
3. **Fan-out natural** — basta adicionar um novo binding pra uma nova fila e um novo serviço passa a receber os mesmos eventos sem nenhuma mudança no publisher.
4. **Suavização de picos** — a fila absorve rajadas. O consumer processa no ritmo que aguenta.

Esse é o coração de uma arquitetura **event-driven**.

---

## Anatomia do publisher no projeto

### 1. Configuração — `todo-service/.../config/RabbitMQConfig.java`

Declara três coisas no broker (idempotente — se já existir, o RabbitMQ ignora):

- **Exchange** `todo.exchange` do tipo `topic`
- Três **filas duráveis**: `todo.created.queue`, `todo.updated.queue`, `todo.deleted.queue`
- Três **bindings** ligando a exchange às filas via as routing keys `todo.created`, `todo.updated`, `todo.deleted`

> O publisher **também** declara as filas, mesmo não consumindo delas. Isso garante que a topologia exista mesmo que nenhum consumer tenha subido ainda — se você publicar antes do consumer ligar, a mensagem fica esperando na fila ao invés de ser jogada fora.

Também define dois beans cruciais:

```java
@Bean
public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
}

@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(messageConverter());
    return template;
}
```

- O **`Jackson2JsonMessageConverter`** transforma o `TodoEvent` em JSON e adiciona automaticamente o header `__TypeId__` (FQN da classe) — é por isso que o consumer consegue desserializar mesmo que o nome do tipo seja diferente do outro lado.
- O **`RabbitTemplate`** é o cliente de alto nível pra publicar. Sem ele, você cairia na API crua do `Channel` do RabbitMQ Java client.

### 2. O DTO do evento — `event/TodoEvent.java`

```java
public record TodoEvent(
        String todoId,
        String title,
        String action,
        LocalDateTime occurredAt
) {
    public static TodoEvent of(String todoId, String title, String action) {
        return new TodoEvent(todoId, title, action, LocalDateTime.now());
    }
}
```

Características importantes:

- É um **record** imutável. Eventos representam fatos ocorridos no passado — não devem ser mutáveis depois de criados.
- Carrega **só o essencial** (`todoId`, `title`, `action`, `occurredAt`). Não carrega a entidade inteira. Isso reduz o acoplamento entre publisher e consumer ao schema do banco do `todo-service`.
- O `occurredAt` é gerado **no publisher**, não no consumer. É a hora em que o fato aconteceu, não a hora em que foi processado.

### 3. A publicação — `service/TodoService.java`

```java
public TodoResponseDTO create(TodoRequestDTO dto) {
    Todo todo = repository.save(mapper.toEntity(dto));
    TodoResponseDTO response = mapper.toResponse(todo);
    publish(RabbitMQConfig.ROUTING_CREATED, TodoEvent.of(response.id(), response.title(), "CREATED"));
    return response;
}

private void publish(String routingKey, TodoEvent event) {
    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, event);
}
```

O método `convertAndSend(exchange, routingKey, payload)` faz três coisas:

1. Usa o `Jackson2JsonMessageConverter` pra serializar o `TodoEvent` em bytes UTF-8 com `Content-Type: application/json`.
2. Adiciona o header `__TypeId__` apontando pra `com.microservices.todo.event.TodoEvent`.
3. Envia o frame AMQP `basic.publish` pro broker, endereçado à exchange `todo.exchange` com a routing key dada.

Por padrão, mensagens enviadas via `RabbitTemplate` são **persistentes** (`delivery_mode = 2`) — sobrevivem a restart do broker, contanto que a fila também seja durável (e a nossa é, com `new Queue(name, true)`).

---

## Topic Exchange + Routing Key: por que essa combinação

O projeto usa exchange do tipo **topic**. As routing keys (`todo.created`, `todo.updated`, `todo.deleted`) seguem um padrão de **palavras separadas por ponto**. Isso é deliberado.

- Um consumer hoje se inscreve em uma routing key específica (`todo.created`).
- Amanhã, um novo serviço pode se inscrever em `todo.*` e receber **todos** os eventos do domínio Todo, sem alteração nenhuma no publisher.
- Se houvesse outro agregado, tipo `user.created`, um serviço de auditoria poderia escutar `#.created` e pegar todos os "criados" do sistema.

Outras escolhas possíveis e por que não foram usadas:

| Tipo de exchange | Comportamento | Por que **não** aqui |
|---|---|---|
| `direct` | match exato da routing key | Funciona, mas perde a flexibilidade de wildcards |
| `fanout` | manda pra **todas** as filas ligadas | Não dá pra diferenciar created/updated/deleted no broker |
| `headers` | roteia por headers em vez de routing key | Mais complexo, sem benefício pro caso de uso |
| `topic` ✅ | padrões com `*` (1 palavra) e `#` (N palavras) | Bom equilíbrio entre seletividade e extensibilidade |

---

## Garantias e limites do publisher atual

O `TodoService.create()` faz o `repository.save()` e depois o `rabbitTemplate.convertAndSend()` **no mesmo método, sem coordenação transacional**. Isso significa:

- ✅ Em 99% dos casos os dois acontecem.
- ⚠️ Se o RabbitMQ estiver fora do ar **depois** do `save()` e **antes** do `convertAndSend()`, o Todo é salvo no banco mas **o evento se perde**. Inconsistência silenciosa.
- ⚠️ Não há **publisher confirms** habilitados. Mesmo que o `convertAndSend()` retorne sem exceção, em teoria isso só garante que o frame foi colocado no socket, não que o broker persistiu.

Padrões reais de mercado pra fechar esse gap:

- **Transactional Outbox**: dentro da mesma transação do banco, gravar uma linha numa tabela `outbox`. Um worker separado lê dessa tabela e publica no broker, marcando como enviado.
- **Publisher Confirms**: habilitar `spring.rabbitmq.publisher-confirm-type=correlated` e tratar callbacks de `ack`/`nack` do broker.
- **Retry com backoff** em torno do `convertAndSend()`, mas isso só protege contra falhas transitórias — não resolve o caso de crash entre o `save()` e o `publish()`.

Hoje o projeto não implementa nenhum desses — é didático e foca primeiro em entender o fluxo básico. O passo natural de evolução é introduzir o Outbox.

---

## Como testar manualmente

Disparar uma publicação via API HTTP do publisher:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8081/todos' -Method Post `
  -Headers @{ 'Content-Type'='application/json' } `
  -Body '{"title":"manual","description":"teste rabbit"}'
```

Conferir que a mensagem chegou na fila:

```powershell
docker exec rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged
```

Espiar o conteúdo sem consumir (`ack_requeue_true` devolve pra fila):

```powershell
curl.exe -s -u guest:guest -X POST `
  http://localhost:15672/api/queues/%2F/todo.created.queue/get `
  -H "content-type: application/json" `
  --data-binary '{"count":10,"ackmode":"ack_requeue_true","encoding":"auto"}'
```

Ver o que aparece:

```json
{
  "exchange": "todo.exchange",
  "routing_key": "todo.created",
  "properties": {
    "delivery_mode": 2,
    "content_type": "application/json",
    "headers": { "__TypeId__": "com.microservices.todo.event.TodoEvent" }
  },
  "payload": "{\"todoId\":\"...\",\"title\":\"manual\",\"action\":\"CREATED\",\"occurredAt\":[...]}"
}
```

Tudo isso confirma que o publisher fez o trabalho: serializou o evento em JSON, marcou como persistente, e entregou na exchange com a routing key correta.
