# Eventos do Sistema (RabbitMQ)

## Visão Geral

O `todo-service` publica eventos no RabbitMQ sempre que um Todo é criado, atualizado ou deletado. O `notification-service` consome esses eventos de forma assíncrona e independente.

```
todo-service  ──publish──►  [todo.exchange]  ──route──►  queue  ──consume──►  notification-service
```

---

## Estrutura do Evento (`TodoEvent`)

Todos os três eventos compartilham o mesmo payload:

| Campo        | Tipo            | Descrição                                                        |
|--------------|-----------------|------------------------------------------------------------------|
| `todoId`     | `String`        | ID único do Todo no banco de dados                               |
| `title`      | `String`        | Título do Todo no momento do evento                              |
| `action`     | `String`        | Ação que originou o evento: `CREATED`, `UPDATED` ou `DELETED`    |
| `occurredAt` | `LocalDateTime` | Timestamp de quando o evento foi gerado                          |

**Exemplo de payload (JSON serializado pelo Jackson):**
```json
{
  "todoId": "abc123",
  "title": "Estudar RabbitMQ",
  "action": "CREATED",
  "occurredAt": "2026-05-07T14:30:00"
}
```

---

## Eventos

### `todo.created`

| Propriedade | Valor                |
|-------------|----------------------|
| Routing Key | `todo.created`       |
| Fila        | `todo.created.queue` |
| Exchange    | `todo.exchange`      |

**Quando é disparado:** Imediatamente após um novo Todo ser salvo no banco, dentro do método `TodoService.create()`.

**Fluxo:**
```
POST /todos
  └─► TodoService.create()
        └─► repository.save(todo)        ← persiste no banco
        └─► publish("todo.created", ...) ← publica o evento
```

**Utilidade:** Permite que outros serviços reajam à criação de um Todo sem consultar o `todo-service`. Exemplo: enviar e-mail de confirmação, registrar auditoria, ou acionar um fluxo de aprovação.

---

### `todo.updated`

| Propriedade | Valor                |
|-------------|----------------------|
| Routing Key | `todo.updated`       |
| Fila        | `todo.updated.queue` |
| Exchange    | `todo.exchange`      |

**Quando é disparado:** Após qualquer alteração (título, descrição ou status `completed`) ser salva, dentro do método `TodoService.update()`.

**Fluxo:**
```
PUT /todos/{id}
  └─► TodoService.update()
        └─► repository.save(todo)        ← persiste a alteração
        └─► publish("todo.updated", ...) ← publica o evento
```

**Utilidade:** Notifica outros serviços sobre mudanças em um Todo. Exemplo: sincronizar cache externo, notificar usuário sobre mudança de status, ou registrar histórico de alterações.

---

### `todo.deleted`

| Propriedade | Valor                |
|-------------|----------------------|
| Routing Key | `todo.deleted`       |
| Fila        | `todo.deleted.queue` |
| Exchange    | `todo.exchange`      |

**Quando é disparado:** Após o Todo ser removido do banco, dentro de `TodoService.delete()`. Os dados são capturados **antes** da deleção, pois após `repository.delete()` o objeto não existe mais.

**Fluxo:**
```
DELETE /todos/{id}
  └─► TodoService.delete()
        └─► getOrThrow(id)               ← carrega os dados antes de deletar
        └─► repository.delete(todo)      ← remove do banco
        └─► publish("todo.deleted", ...) ← publica com dados capturados antes
```

**Utilidade:** Permite que outros serviços façam limpeza ou registro quando um Todo é removido. Exemplo: remover dados relacionados, arquivar o registro, ou notificar usuários.

---

## Consumidor atual: `notification-service`

O `notification-service` escuta as três filas e por enquanto apenas loga os eventos recebidos:

| Método            | Fila consumida       | Ação atual                            |
|-------------------|----------------------|---------------------------------------|
| `onTodoCreated()` | `todo.created.queue` | Loga `[NOTIFICATION] Todo CRIADO`     |
| `onTodoUpdated()` | `todo.updated.queue` | Loga `[NOTIFICATION] Todo ATUALIZADO` |
| `onTodoDeleted()` | `todo.deleted.queue` | Loga `[NOTIFICATION] Todo DELETADO`   |

> O `notification-service` é o ponto de extensão natural para implementar notificações reais (e-mail, push, webhook) sem alterar o `todo-service`.

---

## Infraestrutura RabbitMQ

| Recurso          | Nome                  | Tipo           |
|------------------|-----------------------|----------------|
| Exchange         | `todo.exchange`       | Topic Exchange |
| Fila criação     | `todo.created.queue`  | Durable Queue  |
| Fila atualização | `todo.updated.queue`  | Durable Queue  |
| Fila deleção     | `todo.deleted.queue`  | Durable Queue  |

- As filas são **durable** (`true`): sobrevivem a reinicializações do RabbitMQ.
- A serialização é feita em **JSON** via `Jackson2JsonMessageConverter`.
- O exchange do tipo **Topic** permite adicionar novos consumidores no futuro sem alterar o publisher.

---

## Arquivos de referência

| Arquivo | Responsabilidade |
|---------|-----------------|
| `todo-service/.../service/TodoService.java` | Publica os eventos após cada operação |
| `todo-service/.../event/TodoEvent.java` | Define o payload do evento |
| `todo-service/.../config/RabbitMQConfig.java` | Configura exchange, filas, bindings e RabbitTemplate |
| `notification-service/.../listener/TodoEventListener.java` | Consome os eventos |
| `notification-service/.../config/RabbitMQConfig.java` | Espelha a configuração de filas no lado do consumidor |
