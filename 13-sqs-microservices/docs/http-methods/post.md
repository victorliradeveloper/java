# POST

Cria um recurso novo. **Não é idempotente por contrato** — cada chamada gera um `id` diferente. Neste projeto a idempotência é *forçada* via header `Idempotency-Key` (padrão Stripe).

| | |
|---|---|
| Rota | `POST /todos` |
| Status sucesso | `201 Created` |
| Idempotência | Opcional, via header `Idempotency-Key` |
| Publica evento | `CREATED` (via outbox) |

---

## Implementação

### Controller — [`TodoController.create`](../../todo-service/src/main/java/com/microservices/todo/controller/TodoController.java)

```java
@PostMapping
public ResponseEntity<TodoResponseDTO> create(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody @Valid TodoRequestDTO dto) {
    TodoResponseDTO body = idempotencyService.executeIdempotent(
            idempotencyKey,
            CREATE_FINGERPRINT,          // "POST /todos"
            dto,
            TodoResponseDTO.class,
            () -> service.create(dto)
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
}
```

Pontos:
- `@Valid` aciona o bean validation no body. `title` é `@NotBlank` em [`TodoRequestDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoRequestDTO.java) → body sem `title` retorna `400`.
- O header `Idempotency-Key` é `required = false` — POST sem o header funciona normalmente, só perde a proteção de retry.
- A criação roda **dentro** do wrapper `executeIdempotent`, que envolve o `service.create` no claim atômico. Detalhes do mecanismo em [idempotencia.md §POST /todos](../conceitos/idempotencia.md#post-todos--idempotency-key).

### Service — [`TodoService.create`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java)

```java
@Transactional
public TodoResponseDTO create(TodoRequestDTO dto) {
    Todo entity = mapper.toEntity(dto);
    entity.setId(UUID.randomUUID().toString());      // id gerado no server
    LocalDateTime now = LocalDateTime.now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    if (entity.getPriority() == null) {
        entity.setPriority(Priority.MEDIUM);          // default de prioridade
    }
    Todo todo = repository.save(entity);
    TodoResponseDTO response = mapper.toResponse(todo);

    outboxService.record(..., "CREATED", TodoEvent.of(...));   // outbox, mesmo commit
    return response;
}
```

Pontos:
- `id` é um UUID gerado no servidor — é por isso que POST não é naturalmente idempotente (duas chamadas = dois UUIDs).
- `priority` ausente vira `MEDIUM` (default).
- `completed` nasce `false` (`@Mapping(target = "completed", constant = "false")` no [`TodoMapper`](../../todo-service/src/main/java/com/microservices/todo/mapper/TodoMapper.java)).
- O `save` + `outboxService.record` acontecem no **mesmo `@Transactional`** → commit atômico (pattern outbox). Ver [outbox.md](../sqs/outbox.md).

---

## Como testar

### Criar (sem idempotência)

```bash
curl -i -X POST http://localhost:8080/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"comprar leite","description":"no mercado","priority":"HIGH"}'
```
→ `201 Created`, body com `id` novo.

### Criar com Idempotency-Key (retry seguro)

```bash
curl -i -X POST http://localhost:8080/todos \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 7f3a-8c12-4d56-bcde" \
  -d '{"title":"comprar leite","priority":"HIGH"}'
```
Rode **duas vezes**: a segunda retorna o **mesmo `id`** (replay da resposta cacheada), sem criar segundo Todo.

### Conflito de payload (mesma key, body diferente)

```bash
curl -i -X POST http://localhost:8080/todos \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 7f3a-8c12-4d56-bcde" \
  -d '{"title":"comprar pão","priority":"LOW"}'
```
→ `409` com `"code": "PAYLOAD_MISMATCH"`.

### Body inválido (sem title)

```bash
curl -i -X POST http://localhost:8080/todos \
  -H "Content-Type: application/json" \
  -d '{"description":"sem titulo"}'
```
→ `400 Bad Request`, `ProblemDetail` estruturado ([`GlobalExceptionHandler.handleValidation`](../../todo-service/src/main/java/com/microservices/todo/exception/GlobalExceptionHandler.java)) com `"code": "VALIDATION_ERROR"` e `"errors": { "title": "must not be blank" }`.

---

## Relacionado

- [Idempotência §POST](../conceitos/idempotencia.md#post-todos--idempotency-key) — fluxo completo do `IdempotencyService`.
- [Outbox](../sqs/outbox.md) — como o evento `CREATED` sai atomicamente com o save.
- [put.md](put.md) / [delete.md](delete.md) — os verbos idempotentes por natureza.
