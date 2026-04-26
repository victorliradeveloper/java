# Todo Microservices

API de gerenciamento de tarefas construída com arquitetura de microserviços.

## Serviços

| Serviço | Porta | Descrição |
|---|---|---|
| `eureka-server` | 8761 | Service Discovery |
| `api-gateway` | 8090 | Ponto de entrada da API |
| `todo-service` | 8081 | CRUD de tarefas |
| `postgres` | 5432 | Banco de dados |
| `rabbitmq` | 5672 / 15672 | Message broker |

## Pré-requisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado e rodando

## Como rodar

```bash
docker compose up --build
```

Na primeira execução aguarde ~3-5 minutos para download das imagens e compilação.

## Endpoints

Todos os requests devem ser feitos via gateway em `http://localhost:8090`.

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/todos` | Criar tarefa |
| `GET` | `/todos` | Listar todas as tarefas |
| `GET` | `/todos/{id}` | Buscar tarefa por ID |
| `PUT` | `/todos/{id}` | Atualizar tarefa |
| `DELETE` | `/todos/{id}` | Deletar tarefa |

### Exemplos

**Criar tarefa**
```bash
curl -X POST http://localhost:8090/todos \
  -H "Content-Type: application/json" \
  -d '{"title": "Minha tarefa", "description": "Descrição opcional"}'
```

**Listar tarefas**
```bash
curl http://localhost:8090/todos
```

**Atualizar tarefa**
```bash
curl -X PUT http://localhost:8090/todos/{id} \
  -H "Content-Type: application/json" \
  -d '{"completed": true}'
```

**Deletar tarefa**
```bash
curl -X DELETE http://localhost:8090/todos/{id}
```

## Monitoramento

| Painel | URL | Credenciais |
|---|---|---|
| Eureka Dashboard | http://localhost:8761 | — |
| RabbitMQ Management | http://localhost:15672 | guest / guest |

## RabbitMQ

O RabbitMQ sobe automaticamente com o `docker compose up`.

Para acessar o painel de gerenciamento:

1. Abra `http://localhost:15672` no navegador
2. Login: `guest` / Senha: `guest`

No painel é possível visualizar:
- **Queues** — filas criadas e mensagens pendentes
- **Exchanges** — exchanges e bindings configurados
- **Connections** — conexões ativas dos serviços
- **Overview** — taxa de mensagens em tempo real

## Outros comandos

```bash
# Rodar em background
docker compose up --build -d

# Ver logs de um serviço
docker compose logs -f todo-service
docker compose logs -f rabbitmq

# Parar os serviços
docker compose down

# Parar e apagar os dados do banco
docker compose down -v
```
