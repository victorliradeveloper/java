# Todo Microservices — Sincrono (HTTP)

Mesma arquitetura do `01-microservices`, porem **sem mensageria**. Comunicacao
entre servicos eh 100% sincrona via HTTP (Feign + Eureka + Resilience4j).

> Companion do projeto `01-microservices` (versao com RabbitMQ + Outbox + DLQ).
> Compare os dois pra ver os trade-offs de cada estilo.

## Servicos

| Servico | Porta | Descricao |
|---|---|---|
| `eureka-server` | 8761 | Service Discovery |
| `api-gateway` | 8090 | Ponto de entrada da API + rate limit |
| `todo-service` | 8081 | CRUD de tarefas (chama os dois abaixo via HTTP) |
| `notification-service` | 8082 | Recebe eventos via HTTP e envia email |
| `audit-service` | 8083 | Recebe eventos via HTTP e grava audit log |
| `postgres` | 5432 | Banco (um DB por servico) |
| `redis` | 6379 | Rate limit do gateway |

## Pre-requisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado e rodando

## Como rodar

```bash
# dev (Gmail SMTP)
docker compose --env-file .env.dev up --build

# prod simulado
docker compose --env-file .env.prod up --build
```

Na primeira execucao aguarde ~3-5 minutos para download das imagens e compilacao.

## Endpoints

Todos os requests do cliente externo devem ir pelo gateway em `http://localhost:8090`.

| Metodo | Rota | Descricao |
|---|---|---|
| `POST` | `/todos` | Criar tarefa |
| `GET` | `/todos` | Listar todas as tarefas |
| `GET` | `/todos/{id}` | Buscar tarefa por ID |
| `PUT` | `/todos/{id}` | Atualizar tarefa |
| `DELETE` | `/todos/{id}` | Deletar tarefa |

### Exemplos

```bash
# Criar tarefa
curl -X POST http://localhost:8090/todos \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"title": "Minha tarefa", "description": "Descricao opcional"}'

# Listar
curl http://localhost:8090/todos

# Atualizar
curl -X PUT http://localhost:8090/todos/{id} \
  -H "Content-Type: application/json" \
  -d '{"completed": true}'

# Deletar
curl -X DELETE http://localhost:8090/todos/{id}
```

## Endpoints internos (chamados pelo todo-service)

Nao sao expostos pelo gateway, mas estao acessiveis direto pra debug:

```bash
# Audit (8083): grava log de auditoria
curl -X POST http://localhost:8083/audit-logs \
  -H "Content-Type: application/json" \
  -d '{"eventId":"...","todoId":"...","title":"X","action":"CREATED","occurredAt":"2026-06-01T10:00:00"}'

# Notification (8082): envia email
curl -X POST http://localhost:8082/notifications/todo-events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"...","todoId":"...","title":"X","action":"CREATED","occurredAt":"2026-06-01T10:00:00"}'
```

## Monitoramento

| Painel | URL |
|---|---|
| Eureka Dashboard | http://localhost:8761 |
| Actuator (todo) | http://localhost:8081/actuator |
| Actuator (notification) | http://localhost:8082/actuator |
| Actuator (audit) | http://localhost:8083/actuator |
| Circuit Breakers (todo) | http://localhost:8081/actuator/circuitbreakers |
| Circuit Breakers (notification) | http://localhost:8082/actuator/circuitbreakers |

## Comandos uteis

```bash
docker compose --env-file .env.dev up --build -d  # background
docker compose logs -f todo-service               # logs de um servico
docker compose down                                # parar
docker compose down -v                             # parar + apagar dados
```

## Documentacao

- [Arquitetura sincrona vs assincrona](docs/arquitetura-sincrona.md) — trade-offs, quando usar cada um
- [Resilience4j](docs/resilience4j.md) — Circuit Breaker + Retry + Fallback no todo-service
- [Idempotencia](docs/idempotencia.md) — chave Stripe-style no POST + dedupe nos consumers
