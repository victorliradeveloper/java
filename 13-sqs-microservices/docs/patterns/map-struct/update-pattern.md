# MapStruct — Padrão de update in-place (`@MappingTarget`)

Como funciona o `updateEntity` do projeto: receber um DTO de update e
modificar uma entity **já existente**, com semântica PATCH.

---

## O método

```java
@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
@Mapping(target = "id", ignore = true)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
void updateEntity(TodoUpdateDTO dto, @MappingTarget Todo todo);
```

Três detalhes diferenciam esse método de um mapper "normal":

1. Retorno `void` (não retorna nada).
2. **Dois parâmetros**.
3. `@MappingTarget` em um deles.

---

## `@MappingTarget` — modificar em vez de criar

Sem `@MappingTarget`, o MapStruct sempre **cria um objeto novo** como
target:

```java
Todo toEntity(TodoRequestDTO dto);
// Gera: Todo result = new Todo(); ... return result;
```

Com `@MappingTarget`, ele recebe um objeto **já existente** e modifica:

```java
void updateEntity(TodoUpdateDTO dto, @MappingTarget Todo todo);
// Gera: todo.setX(dto.getX()); ... (não cria nada)
```

Voltando à analogia da secretária: em vez de pegar um papel B em branco e
preencher do zero, ela está olhando um papel B **já preenchido** e
atualizando apenas alguns campos.

---

## `NullValuePropertyMappingStrategy.IGNORE` — semântica PATCH

Considere o DTO de update do projeto:

```java
public record TodoUpdateDTO(
    String title,
    String description,
    Boolean completed
) {}
```

Cliente pode mandar parcial:

```json
{ "title": "novo titulo" }
```

Os outros 2 campos chegam como `null` no DTO.

### Comportamento default (sem `NullValuePropertyMappingStrategy.IGNORE`)

```
todo.setTitle("novo titulo");
todo.setDescription(null);     ← apaga o que tinha!
todo.setCompleted(null);       ← também apaga
```

PUT/PATCH com campo omitido **apagaria** o valor existente. Bug clássico.

### Com `NullValuePropertyMappingStrategy.IGNORE`

```
todo.setTitle("novo titulo");
// description: ignorado, mantém o que estava
// completed: ignorado, mantém o que estava
```

Agora `null` no DTO significa **"não quero alterar esse campo"**, não
**"quero apagar"**. Essa é a semântica PATCH.

---

## Por que `id`, `createdAt`, `updatedAt` ainda têm `ignore = true`

Mesmo com `NullValuePropertyMappingStrategy.IGNORE`, esses campos
**não existem** no `TodoUpdateDTO` — nem como campos null, **simplesmente
não estão lá**. O MapStruct daria warning de "unmapped target".

`ignore = true` é o que silencia o warning dizendo "esses campos não são
problema seu, o service preenche".

---

## Diferença entre os dois mecanismos de ignorar

| Mecanismo | Quando aplica | Para quê |
|---|---|---|
| `@Mapping(target = "x", ignore = true)` | Campo **não existe** no source | Silenciar warning de unmapped |
| `NullValuePropertyMappingStrategy.IGNORE` | Campo **existe** mas chegou null | Semântica PATCH (não sobrescreve) |

---

## Quando o `updateEntity` é chamado

`TodoService.update`:

```java
@Transactional
public TodoResponseDTO update(String id, TodoUpdateDTO dto) {
    Todo todo = getOrThrow(id);
    TodoSnapshot before = TodoSnapshot.from(todo);

    mapper.updateEntity(dto, todo);   // ← muta o todo in-place

    TodoSnapshot after = TodoSnapshot.from(todo);

    if (!before.equals(after)) {
        todo.setUpdatedAt(LocalDateTime.now());
        // ... grava outbox event UPDATED
    }
    return mapper.toResponse(repository.save(todo));
}
```

Note o snapshot **antes** e **depois** da chamada: o service compara pra
decidir se a operação foi um no-op (PUT idempotente). Se nada mudou, não
bumpa `updatedAt` e não publica evento.

Esse padrão só funciona porque `updateEntity` muta in-place — o
`before` continua intacto pra comparação.

---

## Referência cruzada

- Conceito de target/source: [`source-target`](./source-target.md)
- `ignore` vs `constant`: [`mapStruct`](./mapStruct.md)
