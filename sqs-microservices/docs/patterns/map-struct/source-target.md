# MapStruct — `source` e `target`

Conceitos centrais que aparecem em **toda** anotação `@Mapping`. Confusos
porque nem sempre o source é o DTO e o target é a Entity — depende da
direção do método.

---

## Definição

Voltando à analogia da secretária copiando entre dois papéis:

- **Source** = papel **de onde** ela copia (entrada).
- **Target** = papel **pra onde** ela copia (saída).

Não tem nada a ver com "DTO ou Entity". São só **direções**.

---

## Regra real (como o MapStruct descobre quem é quem)

| Quem é | Como ele descobre |
|---|---|
| **Source** | Parâmetro de entrada do método (que **não** tem `@MappingTarget`) |
| **Target** | Tipo de retorno do método **OU** parâmetro marcado com `@MappingTarget` |

---

## Os 3 métodos deste projeto

```java
Todo toEntity(TodoRequestDTO dto);
//   ↑                  ↑
//   target (Entity)    source (DTO)
```

```java
TodoResponseDTO toResponse(Todo todo);
//   ↑                       ↑
//   target (DTO)            source (Entity)
```

```java
void updateEntity(TodoUpdateDTO dto, @MappingTarget Todo todo);
//                       ↑                            ↑
//                       source (DTO)                 target (Entity)
```

Resumo:

| Método | Source | Target | Direção |
|---|---|---|---|
| `toEntity` | DTO | Entity | DTO → Entity |
| `toResponse` | **Entity** | **DTO** | Entity → DTO |
| `updateEntity` | DTO | Entity | DTO → Entity (in-place) |

Note como `toResponse` **inverte**: aqui a Entity vira source. Por isso a
regra "DTO é sempre source" está errada — depende do método.

---

## Quando aparecem explícitos no código

Por default, o MapStruct casa campos **por nome**. Se `source.title` existe
e `target.title` existe, ele liga automaticamente. Você só precisa anotar
`@Mapping(source = "...", target = "...")` quando:

### 1. Nomes diferentes

```java
@Mapping(source = "nomeUsuario", target = "username")
Entity toEntity(DTO dto);
```

### 2. Campo aninhado

```java
@Mapping(source = "endereco.cidade", target = "city")
```

### 3. Múltiplos parâmetros source (precisa qualificar)

```java
void merge(TodoUpdateDTO dto, AuditInfo audit, @MappingTarget Todo todo);

@Mapping(source = "dto.title",     target = "title")
@Mapping(source = "audit.updater", target = "updatedBy")
```

### 4. Junto de transformação (`qualifiedByName`, `expression`)

```java
@Mapping(source = "title", target = "title", qualifiedByName = "trimAndCapitalize")
```

---

## Quando NÃO declarar (regra prática)

> **Não declare o óbvio.** Anote apenas o que o MapStruct não consegue
> deduzir sozinho.

Por que evitar redundância:

| Argumento | Detalhe |
|---|---|
| Ruído visual | Adiciona linhas sem informação nova |
| Atrapalha refactor | Se renomear o campo, a string no `@Mapping` não acompanha |
| Quebra a convenção | MapStruct é declarativo apenas onde a convenção não cobre |

No projeto, o `updateEntity` tem `source/target` explícitos
**propositalmente**, com comentário avisando que é redundante — mantido
ali como exemplo didático.

---

## Referência cruzada

- Anotações com semântica especial: [`ignore` e `constant`](./mapStruct.md)
- Update in-place: [`update-pattern`](./update-pattern.md)
- Quando ser explícito mesmo redundando: [`quando-ser-explicito`](./quando-ser-explicito.md)
