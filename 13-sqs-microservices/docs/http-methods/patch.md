# PATCH

Atualização **parcial** de um recurso: aplica apenas os campos enviados, **preservando** o resto. Pela spec HTTP (RFC 5789), `PATCH` **não é garantidamente idempotente** — depende da operação. Aqui a implementação é idempotente porque é um *merge de campos* (mandar o mesmo body N vezes leva ao mesmo estado).

| | |
|---|---|
| Rota | `PATCH /todos/{id}` |
| Status sucesso | `200 OK` |
| Idempotência | Sim, neste projeto (merge de campos + diff antes/depois) |
| Campos omitidos | **preservados** (ao contrário do PUT, que reseta) |
| Publica evento | `UPDATED` (somente se houve mudança real) |

> Contraste com **PUT** (substituição total, campos omitidos resetam): ver [put.md](put.md).

---

## Implementação

### Controller — [`TodoController.patch`](../../todo-service/src/main/java/com/microservices/todo/controller/TodoController.java)

```java
@PatchMapping("/{id}")
public ResponseEntity<TodoResponseDTO> patch(@PathVariable String id, @RequestBody TodoUpdateDTO dto) {
    return ResponseEntity.ok(service.patch(id, dto));
}
```

Usa [`TodoUpdateDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoUpdateDTO.java) — todos os campos opcionais (sem validação de obrigatoriedade), porque PATCH pode mandar só um subconjunto.

### Service — [`TodoService.patch`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java)

```java
@Transactional
public TodoResponseDTO patch(String id, TodoUpdateDTO dto) {
    return applyChange(id, dto, mapper::patchEntity);   // estratégia = merge parcial
}
```

### O merge — [`TodoMapper.patchEntity`](../../todo-service/src/main/java/com/microservices/todo/mapper/TodoMapper.java)

```java
@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
@Mapping(target = "id", ignore = true)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
void patchEntity(TodoUpdateDTO dto, @MappingTarget Todo todo);
```

Código gerado (`TodoMapperImpl`): cada campo é setado **apenas se não-null** — é isso que preserva o resto:

```java
public void patchEntity(TodoUpdateDTO dto, Todo todo) {
    if (dto.title() != null)       todo.setTitle(dto.title());
    if (dto.description() != null) todo.setDescription(dto.description());
    if (dto.completed() != null)   todo.setCompleted(dto.completed());
    if (dto.priority() != null)    todo.setPriority(dto.priority());
}
```

---

## PUT vs PATCH neste projeto

A spec REST diferencia os dois — e **aqui eles são genuinamente diferentes**:

| | PUT (full replace) | PATCH (partial merge) |
|---|---|---|
| DTO | [`TodoReplaceDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoReplaceDTO.java) (`title` obrigatório) | [`TodoUpdateDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoUpdateDTO.java) (tudo opcional) |
| Mapper | `replaceEntity` (SET_TO_NULL) | `patchEntity` (IGNORE) |
| Campo omitido | **reseta** (default/null) | **preserva** |
| Idempotente | ✅ sempre | ✅ (merge de campos) |

### O princípio de design: compartilhe a orquestração, separe a estratégia

A pergunta natural é *"isso não é lógica duplicada?"*. Não — porque o que PUT e PATCH têm em comum (carregar, fazer diff, bumpar `updatedAt`, publicar `UPDATED` só em mudança real) é genuinamente igual, e o que difere (a regra de merge) é genuinamente diferente. A solução não é "duplicar tudo" nem "compartilhar tudo", e sim **compartilhar a orquestração e parametrizar a parte que muda**:

```java
public TodoResponseDTO update(String id, TodoReplaceDTO dto) {   // PUT
    return applyChange(id, dto, mapper::replaceEntity);
}

public TodoResponseDTO patch(String id, TodoUpdateDTO dto) {     // PATCH
    return applyChange(id, dto, mapper::patchEntity);
}

private <D> TodoResponseDTO applyChange(String id, D dto, BiConsumer<D, Todo> merge) {
    Todo todo = getOrThrow(id);
    TodoSnapshot before = TodoSnapshot.from(todo);
    merge.accept(dto, todo);                       // ← única coisa que difere
    TodoSnapshot after = TodoSnapshot.from(todo);
    if (!before.equals(after)) todo.setUpdatedAt(LocalDateTime.now());
    var response = mapper.toResponse(repository.save(todo));
    if (!before.equals(after)) outboxService.record(..., "UPDATED", ...);
    return response;
}
```

Isso é DRY **e** semanticamente correto: zero duplicação da orquestração, mas cada verbo mantém seu contrato REST. Compartilhar a *implementação inteira* (PUT delegando pro mesmo merge do PATCH) acoplaria dois contratos distintos sob um comportamento que estaria errado pro PUT.

---

## Como testar

### Atualizar só um campo (resto preservado)

```bash
curl -i -X PATCH http://localhost:8080/todos/<id> \
  -H "Content-Type: application/json" \
  -d '{"completed":true}'
```
→ `200`, só `completed` muda; `title`/`description`/`priority` **preservados**.

> Compare com o mesmo body via `PUT`: lá `title` seria obrigatório e os campos omitidos resetariam. Ver [put.md](put.md#como-testar).

### Provar idempotência (PATCH repetido)

Rode o **mesmo** PATCH duas vezes e compare o `updatedAt`: na segunda chamada nada muda → campo **não** é bumpado e nenhum evento `UPDATED` é publicado.

### Id inexistente

```bash
curl -i -X PATCH http://localhost:8080/todos/nao-existe \
  -H "Content-Type: application/json" -d '{"completed":true}'
```
→ `404`.

---

## Relacionado

- [put.md](put.md) — o verbo irmão (substituição total); compartilha a orquestração.
- [Idempotência §Nível 1](../conceitos/idempotencia.md#nível-1--idempotência-natural-por-verbo-http) — o raciocínio do diff antes/depois.
- [Outbox](../sqs/outbox.md) — publicação atômica do evento `UPDATED`.
