# Todo Microservices (SQS)

API de gerenciamento de tarefas em arquitetura de microserviços, usando **AWS SQS** como message broker (emulado localmente via **LocalStack**).

Versão derivada de `01-microservices`, com RabbitMQ substituído por SQS.

## Serviços

| Serviço | Porta | Descrição |
|---|---|---|
| `eureka-server` | 8761 | Service Discovery |
| `api-gateway` | 8090 | Ponto de entrada da API |
| `todo-service` | 8081 | CRUD de tarefas |
| `notification-service` | 8082 | Consome eventos das filas SQS |
| `postgres` | 5432 | Banco de dados |
| `localstack` | 4566 | Emulador AWS (SQS) |
| `redis` | 6379 | Backend do rate limiter |

## Pré-requisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado e rodando

## Como rodar

```bash
docker compose up --build
```

Na primeira execução aguarde ~3-5 minutos para download das imagens e compilação.

O LocalStack cria automaticamente as 3 filas SQS via script em `localstack/init-aws.sh`.

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
| LocalStack Health | http://localhost:4566/_localstack/health | — |

## SQS via LocalStack

As 3 filas sobem automaticamente quando o container LocalStack está pronto:

- `todo-created-queue`
- `todo-updated-queue`
- `todo-deleted-queue`

### Inspecionar filas via AWS CLI

Instale o [awslocal](https://github.com/localstack/awscli-local) ou use a AWS CLI apontando para o endpoint do LocalStack:

```bash
# Listar filas
aws --endpoint-url=http://localhost:4566 sqs list-queues

# Ver atributos da fila
aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/todo-created-queue \
  --attribute-names All

# Consumir uma mensagem manualmente
aws --endpoint-url=http://localhost:4566 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/todo-created-queue
```

Credenciais aceitas por LocalStack: qualquer valor (use `test` / `test`).

## Outros comandos

```bash
# Rodar em background
docker compose up --build -d

# Ver logs de um serviço
docker compose logs -f todo-service
docker compose logs -f notification-service
docker compose logs -f localstack

# Parar os serviços
docker compose down

# Parar e apagar os dados do banco
docker compose down -v
```
