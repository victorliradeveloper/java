# Como os Serviços se Comunicam

## Visão Geral

Este projeto é composto por 4 serviços que trabalham juntos. Em vez de um único sistema grande (monólito), cada serviço tem uma responsabilidade específica e se comunica com os outros de formas diferentes.

```
Cliente (browser, Postman, app)
        │
        ▼
  [ API Gateway :8090 ]         ← porta de entrada única
        │
        ▼
  [ Todo Service :8081 ]        ← faz o trabalho (CRUD de tarefas)
        │  \
        │   └──── publica evento no RabbitMQ
        │                │
        ▼                ▼
  [ PostgreSQL ]   [ Notification Service :8082 ]   ← consome o evento
```

O **Eureka Server** fica "por trás" de tudo, ajudando os serviços a se encontrarem.

---

## Os 4 Serviços

### 1. Eureka Server (porta 8761) — Catálogo de Endereços

Funciona como uma **lista telefônica** dos serviços. Quando o `todo-service` ou o `api-gateway` sobem, eles se registram aqui com seu nome e endereço.

Assim, o API Gateway não precisa saber o IP fixo do `todo-service` — ele pergunta ao Eureka: _"onde está o todo-service?"_ e recebe o endereço atual.

> Acesse o painel: http://localhost:8761

---

### 2. API Gateway (porta 8090) — Porteiro

É a **única porta de entrada** do sistema. O cliente nunca fala diretamente com o `todo-service`.

**Regra de roteamento configurada:**

| Requisição do cliente    | Encaminhada para    |
|--------------------------|---------------------|
| `http://localhost:8090/todos/**` | `todo-service:8081` |

O prefixo `lb://` significa _load balanced_ — se houvesse várias instâncias do `todo-service`, o gateway distribuiria as requisições entre elas automaticamente.

---

### 3. Todo Service (porta 8081) — Núcleo do Sistema

Responsável pelo CRUD de tarefas. Persiste os dados no **PostgreSQL** e publica **eventos** no **RabbitMQ** após cada operação.

**Endpoints disponíveis (acessados via API Gateway):**

| Método | URL                                  | O que faz              |
|--------|--------------------------------------|------------------------|
| POST   | `http://localhost:8090/todos`        | Cria uma tarefa        |
| GET    | `http://localhost:8090/todos`        | Lista todas as tarefas |
| PUT    | `http://localhost:8090/todos/{id}`   | Atualiza uma tarefa    |
| DELETE | `http://localhost:8090/todos/{id}`   | Remove uma tarefa      |

---

### 4. Notification Service (porta 8082) — Ouvinte de Eventos

Não tem endpoints REST. Ele fica **escutando o RabbitMQ** e age quando um evento chega.

Atualmente loga uma mensagem no console, mas poderia enviar e-mail, push notification, SMS, etc.

---

## Os Dois Tipos de Comunicação

### Tipo 1 — Síncrona (REST)

O cliente envia uma requisição e **espera** a resposta.

```
Cliente ──POST /todos──► API Gateway ──► Todo Service ──► PostgreSQL
Cliente ◄── 201 Created ─────────────────────────────────────────────
```

Fluxo de uma criação de tarefa:
1. Cliente envia `POST /todos` com `{ "title": "Estudar Java" }`
2. API Gateway recebe e repassa para o `todo-service`
3. `todo-service` salva no banco de dados
4. `todo-service` retorna `201 Created` com os dados da tarefa
5. API Gateway devolve a resposta ao cliente

---

### Tipo 2 — Assíncrona (RabbitMQ / Eventos)

O `todo-service` publica um evento e **não espera** ninguém processar. O `notification-service` consome quando puder.

```
Todo Service ──publica evento──► RabbitMQ ──► Notification Service
                                              (processa em paralelo)
```

Essa separação é importante: se o `notification-service` cair, os eventos ficam na fila e são processados quando ele voltar. O `todo-service` não é afetado.

---

## Quando os Eventos São Disparados

Toda vez que o `todo-service` faz uma operação, ele publica um evento no RabbitMQ com estas informações:

```json
{
  "todoId": "uuid-da-tarefa",
  "title": "Estudar Java",
  "action": "CREATED",
  "occurredAt": "2026-05-07T19:00:00"
}
```

| Operação       | Evento publicado  | Fila no RabbitMQ      | Routing Key     |
|----------------|-------------------|-----------------------|-----------------|
| Criar tarefa   | `TodoEvent`       | `todo.created.queue`  | `todo.created`  |
| Atualizar tarefa | `TodoEvent`     | `todo.updated.queue`  | `todo.updated`  |
| Deletar tarefa | `TodoEvent`       | `todo.deleted.queue`  | `todo.deleted`  |

Todas as filas usam o mesmo **exchange** chamado `todo.exchange` (do tipo _topic_).

> Acesse o painel do RabbitMQ: http://localhost:15672 (usuário: `guest`, senha: `guest`)

---

## Infraestrutura de Suporte

| Serviço      | Porta  | Função                                  |
|--------------|--------|-----------------------------------------|
| PostgreSQL   | 5432   | Banco de dados do `todo-service`        |
| RabbitMQ     | 5672   | Broker de mensagens (eventos)           |
| RabbitMQ UI  | 15672  | Painel visual para ver filas e mensagens|
| Eureka       | 8761   | Registro e descoberta de serviços       |

---

## Resumo do Fluxo Completo (Criar uma Tarefa)

```
1. Cliente          POST /todos {"title": "Estudar Java"}
        │
2. API Gateway      recebe na porta 8090, consulta Eureka, encaminha para todo-service
        │
3. Todo Service     salva no PostgreSQL
        │
4. Todo Service     publica evento no RabbitMQ (todo.exchange → todo.created.queue)
        │
5. Resposta         201 Created volta para o cliente (passos 1-4 em ~ms)
        │
6. Notification     consome o evento da fila (de forma independente e assíncrona)
            Service loga: "[NOTIFICATION] Todo CRIADO: Estudar Java"
```
