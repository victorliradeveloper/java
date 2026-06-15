# DELETE

Remove um recurso. **Idempotente por contrato**: o segundo DELETE do mesmo recurso deve retornar sucesso (`204`), não `404`. Neste projeto isso é garantido com uma *guard clause* (`if (todo == null) return;`).

| | |
|---|---|
| Rota | `DELETE /todos/{id}` |
| Status sucesso | `204 No Content` |
| Idempotência | Natural — guard clause, segundo DELETE também retorna 204 |
| Publica evento | `DELETED` (somente se o recurso existia) |

---

## Implementação

### Controller — [`TodoController.delete`](../../todo-service/src/main/java/com/microservices/todo/controller/TodoController.java)

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable String id) {
    service.delete(id);
    return ResponseEntity.noContent().build();   // 204 sempre
}
```

### Service — [`TodoService.delete`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java)

```java
@Transactional
public void delete(String id) {
    var todo = repository.findById(id).orElse(null);
    if (todo == null) {
        return;   // guard clause — DELETE idempotente: id ausente = no-op silencioso
    }
    repository.delete(todo);
    outboxService.record(..., "DELETED", TodoEvent.of(...));
}
```

Pontos:
- **A guard clause `if (todo == null) return;`** é o coração da idempotência aqui. Se o id **não existe**, o método sai sem fazer nada — **não lança `TodoNotFoundException`**. O controller retorna `204` do mesmo jeito.
  - Contraste com GET/PUT/PATCH, que usam `getOrThrow` (→ `404`). DELETE **não** usa, de propósito: um cliente que retentou após timeout não pode receber um `404` confuso ("será que apaguei ou não?").
- O evento `DELETED` só é publicado **se o recurso existia** — o early-return garante que `delete` e `outboxService.record` só rodam quando há valor. Segundo DELETE não gera evento duplicado.
- `delete` + `record` no mesmo `@Transactional` → commit atômico (outbox).

> **Estilo:** a versão anterior usava `findById(id).ifPresent(todo -> { ... })`. Trocamos pela guard clause (early-return) por legibilidade — o comportamento é idêntico. `ifPresent` continua idiomático no Java 21; a escolha aqui é preferência, não correção.

---

## Por que `204` e não `404` no segundo DELETE

```
1ª chamada: DELETE /todos/X  → existe → apaga + evento DELETED → 204
2ª chamada: DELETE /todos/X  → não existe → guard clause retorna → 204 (sem evento)
```

O estado final é idêntico em ambas: "X não existe". Esse é o contrato de idempotência do DELETE. Retornar `404` na segunda chamada quebraria retry seguro — o cliente não saberia distinguir "nunca existiu" de "já apaguei".

---

## Como testar

### Apagar

```bash
curl -i -X DELETE http://localhost:8080/todos/<id>
```
→ `204 No Content` (sem body).

### Provar idempotência (segundo DELETE)

```bash
curl -i -X DELETE http://localhost:8080/todos/<mesmo-id>
```
→ `204` de novo (não `404`).

### Id que nunca existiu

```bash
curl -i -X DELETE http://localhost:8080/todos/nunca-existiu
```
→ `204` (idempotente — silencioso).

---

## Relacionado

- [Idempotência §Nível 1](../conceitos/idempotencia.md#nível-1--idempotência-natural-por-verbo-http) — DELETE retorna 204 (não 404) no segundo request.
- [Outbox](../sqs/outbox.md) — publicação atômica do evento `DELETED`.
- [put.md](put.md) — o outro verbo idempotente por natureza.
