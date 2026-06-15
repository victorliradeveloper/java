# PUT

Substitui um recurso existente **por inteiro**. O body representa o recurso completo: campos omitidos são **resetados** (não preservados). Idempotente por contrato — mandar o mesmo body N vezes leva ao mesmo estado final.

| | |
|---|---|
| Rota | `PUT /todos/{id}` |
| Status sucesso | `200 OK` |
| Idempotência | Natural — substitui para o mesmo estado |
| `title` | **Obrigatório** (`@NotBlank`) |
| Campos omitidos | `description` → `null`, `completed` → `false`, `priority` → `MEDIUM` |
| Publica evento | `UPDATED` (somente se houve mudança real) |

> Contraste com **PATCH** (atualização parcial, campos omitidos preservados): ver [patch.md](patch.md).

---

## Implementação

### Controller — [`TodoController.update`](../../todo-service/src/main/java/com/microservices/todo/controller/TodoController.java)

```java
@PutMapping("/{id}")
public ResponseEntity<TodoResponseDTO> update(@PathVariable String id, @RequestBody @Valid TodoReplaceDTO dto) {
    return ResponseEntity.ok(service.update(id, dto));
}
```

- Usa [`TodoReplaceDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoReplaceDTO.java) (não o `TodoUpdateDTO` do PATCH) — `title` é `@NotBlank`.
- `@Valid` aciona o bean validation → body sem `title` retorna `400`.

### DTO — [`TodoReplaceDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoReplaceDTO.java)

```java
public record TodoReplaceDTO(
        @NotBlank String title,      // obrigatório — PUT representa o recurso inteiro
        String description,
        Boolean completed,
        Priority priority
) {}
```

### Service — [`TodoService.update`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java)

```java
@Transactional
public TodoResponseDTO update(String id, TodoReplaceDTO dto) {
    return applyChange(id, dto, mapper::replaceEntity);   // estratégia = substituição total
}
```

PUT e PATCH compartilham a **orquestração** (`applyChange`: carrega, diff, bumpa `updatedAt`, publica `UPDATED` só em mudança real) e diferem **apenas na estratégia de merge**. Detalhes dessa decisão em [patch.md §PUT vs PATCH](patch.md#put-vs-patch-neste-projeto).

### O merge — [`TodoMapper.replaceEntity`](../../todo-service/src/main/java/com/microservices/todo/mapper/TodoMapper.java)

```java
// SEM @BeanMapping IGNORE → usa a estratégia default (SET_TO_NULL): campos
// ausentes SOBRESCREVEM o estado. defaultValue cobre os campos com default.
@Mapping(target = "completed", source = "completed", defaultValue = "false")
@Mapping(target = "priority", source = "priority", defaultValue = "MEDIUM")
void replaceEntity(TodoReplaceDTO dto, @MappingTarget Todo todo);
```

Código gerado pelo MapStruct (`target/generated-sources/.../TodoMapperImpl.java`) — note que `description` é **sempre** setado (null limpa), diferente do PATCH:

```java
public void replaceEntity(TodoReplaceDTO dto, Todo todo) {
    if (dto.completed() != null) todo.setCompleted(dto.completed());
    else                        todo.setCompleted(false);          // reset
    if (dto.priority() != null) todo.setPriority(dto.priority());
    else                        todo.setPriority(Priority.MEDIUM);  // reset
    todo.setTitle(dto.title());                                     // sempre (não-null garantido)
    todo.setDescription(dto.description());                         // sempre — null LIMPA
}
```

---

## PUT vs PATCH neste projeto

| | PUT (full replace) | PATCH (partial merge) |
|---|---|---|
| DTO | `TodoReplaceDTO` (`title` obrigatório) | `TodoUpdateDTO` (tudo opcional) |
| Mapper | `replaceEntity` (SET_TO_NULL) | `patchEntity` (IGNORE) |
| Campo omitido | **reseta** (default/null) | **preserva** |
| Idempotente | ✅ | ✅ (merge de campos) |
| Orquestração | `TodoService.applyChange` (compartilhada) | `TodoService.applyChange` (compartilhada) |

A regra de design: **compartilhar a orquestração** (que é genuinamente comum) e **separar a estratégia de merge** (que difere por contrato REST). Explicação completa em [patch.md](patch.md#put-vs-patch-neste-projeto).

---

## Como testar

### Substituir o recurso (envie a representação completa)

```bash
curl -i -X PUT http://localhost:8080/todos/<id> \
  -H "Content-Type: application/json" \
  -d '{"title":"comprar leite","description":"no mercado","completed":true,"priority":"HIGH"}'
```
→ `200`, recurso substituído.

### Reset por omissão (cuidado — é o comportamento do PUT)

```bash
curl -i -X PUT http://localhost:8080/todos/<id> \
  -H "Content-Type: application/json" \
  -d '{"title":"só o titulo"}'
```
→ `200`, mas `description` vira `null`, `completed` vira `false`, `priority` vira `MEDIUM`. Pra atualizar **só um campo** sem resetar o resto, use [PATCH](patch.md).

### Title ausente

```bash
curl -i -X PUT http://localhost:8080/todos/<id> \
  -H "Content-Type: application/json" -d '{"completed":true}'
```
→ `400 Bad Request`, `ProblemDetail` estruturado ([`GlobalExceptionHandler.handleValidation`](../../todo-service/src/main/java/com/microservices/todo/exception/GlobalExceptionHandler.java)):
```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Um ou mais campos sao invalidos",
  "code": "VALIDATION_ERROR",
  "errors": { "title": "must not be blank" }
}
```

### Id inexistente

```bash
curl -i -X PUT http://localhost:8080/todos/nao-existe \
  -H "Content-Type: application/json" -d '{"title":"x"}'
```
→ `404`.

---

## Relacionado

- [patch.md](patch.md) — o verbo irmão (atualização parcial); compartilha a orquestração.
- [Idempotência §Nível 1](../conceitos/idempotencia.md#nível-1--idempotência-natural-por-verbo-http) — o raciocínio do diff antes/depois.
- [Outbox](../sqs/outbox.md) — publicação atômica do evento `UPDATED`.
