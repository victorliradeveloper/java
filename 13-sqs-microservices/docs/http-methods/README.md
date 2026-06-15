# HTTP Methods

Como cada verbo HTTP é implementado neste projeto, com referência ao código real.

Todos os endpoints vivem em [`TodoController`](../../todo-service/src/main/java/com/microservices/todo/controller/TodoController.java) (`@RequestMapping("/todos")`) e delegam pra [`TodoService`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java).

| Verbo | Rota | Idempotente? | Status sucesso | Doc |
|---|---|---|---|---|
| `POST` | `/todos` | ❌ (forçada via `Idempotency-Key`) | `201 Created` | [post.md](post.md) |
| `GET` | `/todos` e `/todos/{id}` | ✅ (não muda estado) | `200 OK` | [get.md](get.md) |
| `PUT` | `/todos/{id}` | ✅ (substituição total) | `200 OK` | [put.md](put.md) |
| `PATCH` | `/todos/{id}` | ✅ (merge de campos) | `200 OK` | [patch.md](patch.md) |
| `DELETE` | `/todos/{id}` | ✅ (remove) | `204 No Content` | [delete.md](delete.md) |

> **`PUT` e `PATCH` têm semânticas distintas** (PUT substitui o recurso inteiro — campos omitidos resetam, `title` obrigatório; PATCH faz merge parcial — campos omitidos preservados). Eles **compartilham a orquestração** (`TodoService.applyChange`) e diferem apenas na estratégia de merge (`replaceEntity` vs `patchEntity`). O porquê desse design está em [patch.md §PUT vs PATCH](patch.md#put-vs-patch-neste-projeto).

## Portas

| Caminho | URL base |
|---|---|
| Via API Gateway (rate limiter Redis) | `http://localhost:8080/todos` |
| Direto no todo-service | `http://localhost:8081/todos` |

## Onde os status codes são definidos

- Sucesso: explicitamente no controller via `ResponseEntity.status(...)` / `.ok()` / `.noContent()`.
- Erro: em [`GlobalExceptionHandler`](../../todo-service/src/main/java/com/microservices/todo/exception/GlobalExceptionHandler.java) (RFC 7807 `ProblemDetail`) — `404` pra `TodoNotFoundException`, `409` pra `IdempotencyKeyConflictException`, `400` pra `MethodArgumentNotValidException` (falha de `@Valid`, com mapa `errors` campo→mensagem).

## Relacionado

- [Idempotência](../conceitos/idempotencia.md) — os 3 níveis (verbo HTTP, `Idempotency-Key`, dedupe no consumer).
