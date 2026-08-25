# GET

Lê recurso(s). **Idempotente e seguro (safe)** por contrato HTTP: não muda estado, pode ser chamado N vezes sem efeito colateral. É o único verbo que pode ser cacheado por proxies/CDN sem risco.

| | |
|---|---|
| Rotas | `GET /todos` (lista) e `GET /todos/{id}` (por id) |
| Status sucesso | `200 OK` |
| Idempotência | Natural — não muda estado |
| Publica evento | Não |

---

## Implementação

### Controller — [`TodoController`](../../todo-service/src/main/java/com/microservices/todo/controller/TodoController.java)

```java
@GetMapping
public ResponseEntity<List<TodoResponseDTO>> findAll() {
    return ResponseEntity.ok(service.findAll());
}

@GetMapping("/{id}")
public ResponseEntity<TodoResponseDTO> findById(@PathVariable String id) {
    return ResponseEntity.ok(service.findById(id));
}
```

### Service — [`TodoService`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java)

```java
public List<TodoResponseDTO> findAll() {
    return repository.findAll().stream()
            .map(mapper::toResponse)
            .toList();
}

public TodoResponseDTO findById(String id) {
    return mapper.toResponse(getOrThrow(id));   // 404 se não existe
}

private Todo getOrThrow(String id) {
    return repository.findById(id)
            .orElseThrow(() -> new TodoNotFoundException(id));
}
```

Pontos:
- Os dois métodos **não** têm `@Transactional` — é leitura pura, não precisa de transação de escrita.
- `findById` lança [`TodoNotFoundException`](../../todo-service/src/main/java/com/microservices/todo/exception/TodoNotFoundException.java) quando o id não existe. O [`GlobalExceptionHandler`](../../todo-service/src/main/java/com/microservices/todo/exception/GlobalExceptionHandler.java) traduz pra `404` (RFC 7807).
- `findAll` retorna lista vazia `[]` com `200` quando não há nada — **não** é `404`. Coleção vazia ≠ recurso ausente.

### Resposta — [`TodoResponseDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/response/TodoResponseDTO.java)

```java
public record TodoResponseDTO(
        String id, String title, String description, boolean completed,
        Priority priority, LocalDateTime createdAt, LocalDateTime updatedAt) {}
```

A entidade [`Todo`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/Todo.java) é mapeada pra DTO via [`TodoMapper.toResponse`](../../todo-service/src/main/java/com/microservices/todo/mapper/TodoMapper.java) — nunca expomos a entidade direto.

---

## Como testar

### Listar todos

```bash
curl -i http://localhost:8080/todos
```
→ `200`, array (pode ser `[]`).

### Buscar por id

```bash
curl -i http://localhost:8080/todos/<id-retornado-no-POST>
```
→ `200` com o objeto.

### Id inexistente

```bash
curl -i http://localhost:8080/todos/nao-existe
```
→ `404` com `ProblemDetail` (`"title": "Todo not found"`).

---

## Relacionado

- [HTTP Methods (índice)](README.md)
- [Idempotência §Quando aplicar](../conceitos/idempotencia.md#quando-aplicar-cada-nível) — GET não precisa de nada, é idempotente por contrato.
