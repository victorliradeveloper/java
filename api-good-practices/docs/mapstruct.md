# MapStruct

Este documento explica como usar MapStruct neste projeto. O foco é **como escrever um mapper** — não como configurar o build.

## TL;DR

- MapStruct é um **gerador de código**: você escreve uma **interface** anotada com `@Mapper`, ele gera a implementação Java em tempo de compilação.
- A implementação gerada é igualzinha ao que você escreveria à mão (`dto.setX(entity.getX())` linha por linha).
- Mapeamento por **nome de campo** — se os nomes batem, você não escreve nada.
- Quando precisa intervir, usa `@Mapping` com `ignore`, `source`, `expression`, etc.

## A ideia central

Você descreve **o quê** quer mapear (interface) — não **como** (implementação). MapStruct olha os tipos, encontra os campos compatíveis por nome, e escreve a tradução.

Conceitualmente:

```java
// Você escreve isto:
TodoResponseDTO toResponse(Todo todo);

// MapStruct gera isto:
public TodoResponseDTO toResponse(Todo todo) {
    if (todo == null) return null;
    return new TodoResponseDTO(
        todo.getId(),
        todo.getTitle(),
        todo.getDescription(),
        todo.isCompleted(),
        todo.getCreatedAt(),
        todo.getDueDate()
    );
}
```

Você nunca chama o impl explicitamente. Spring injeta o `TodoMapper` no controller via construtor e você chama `todoMapper.toResponse(todo)` — quem responde é a impl gerada.

## Anatomia de um mapper

A forma mais básica — todos os nomes de campos batem entre source e target:

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TodoMapper {

    TodoResponseDTO toResponse(Todo todo);
}
```

Pronto. Se `Todo.title` e `TodoResponseDTO.title` existem, MapStruct conecta. Mesmo para `description`, `createdAt`, etc.

### As duas configurações importantes

| Configuração | O que faz |
|---|---|
| `componentModel = "spring"` | A impl gerada vira `@Component`, Spring injeta automaticamente |
| `unmappedTargetPolicy = ReportingPolicy.ERROR` | Build **falha** se algum campo do target não foi mapeado e não foi explicitamente ignorado |

`ReportingPolicy.ERROR` é o ajuste de produção. Sem ele, esquecer de mapear um campo gera **bug silencioso** (campo vira null em runtime). Com ele, o `mvn compile` reclama antes de subir.

## Lado a lado — interface vs código gerado

Pegando o `TodoMapper` real do projeto:

### Você escreve (`todo/interfaces/mapper/TodoMapper.java`)

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TodoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "completed", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    Todo toEntity(TodoRequestDTO request);

    TodoResponseDTO toResponse(Todo todo);

    List<TodoResponseDTO> toResponseList(List<Todo> todos);
}
```

### MapStruct gera

```java
@Component                                          // veio do componentModel = "spring"
public class TodoMapperImpl implements TodoMapper {

    public Todo toEntity(TodoRequestDTO request) {
        if (request == null) return null;
        Todo.TodoBuilder todo = Todo.builder();
        todo.title( request.title() );              // canonical accessor do record
        todo.description( request.description() );
        todo.dueDate( request.dueDate() );
        // id, completed, createdAt, user NÃO aparecem — @Mapping(ignore=true) os omitiu
        return todo.build();
    }

    public TodoResponseDTO toResponse(Todo todo) {
        if (todo == null) return null;
        return new TodoResponseDTO(                 // canonical constructor do record
            todo.getId(),
            todo.getTitle(),
            todo.getDescription(),
            todo.isCompleted(),
            todo.getCreatedAt(),
            todo.getDueDate()
        );
    }

    public List<TodoResponseDTO> toResponseList(List<Todo> todos) {
        if (todos == null) return null;
        List<TodoResponseDTO> list = new ArrayList<>(todos.size());
        for (Todo todo : todos) list.add(toResponse(todo));
        return list;
    }
}
```

É exatamente o que você escreveria à mão. **Zero reflection em runtime.**

## Quando os nomes batem: faz sozinho

`toResponse` no exemplo acima não tem **nenhuma** anotação `@Mapping`. Não precisou — todos os campos do `TodoResponseDTO` existem com o mesmo nome no `Todo`:

| `TodoResponseDTO` (record) | `Todo` (entity) |
|---|---|
| `id` | `id` |
| `title` | `title` |
| `description` | `description` |
| `completed` | `completed` |
| `createdAt` | `createdAt` |
| `dueDate` | `dueDate` |

MapStruct vê o pareamento, gera. Mapper de **uma linha**.

## Quando precisa intervir: `@Mapping`

`@Mapping` é como você fala "para este campo do target, faça X". Os 5 casos que aparecem em projetos reais:

### 1. Ignorar campo do target (mais comum)

Use quando o target tem um campo que o source não tem, **e é intencional**. Sem essa anotação, `ReportingPolicy.ERROR` faria o build falhar.

```java
@Mapping(target = "id", ignore = true)          // banco gera via @GeneratedValue
@Mapping(target = "user", ignore = true)        // service seta depois
@Mapping(target = "createdAt", ignore = true)   // @PrePersist da entity seta
Todo toEntity(TodoRequestDTO request);
```

No `TodoMapper.toEntity`, 4 campos são ignorados — todos preenchidos depois pela entidade/service, não vêm do DTO de request.

### 2. Renomear (`source`)

Use quando o nome difere entre source e target.

```java
@Mapping(target = "fullName", source = "name")
UserResponseDTO toResponse(User user);
```

### 3. Pegar de campo aninhado

Use quando o target precisa de um campo dentro de um objeto do source.

```java
@Mapping(target = "name", source = "user.name")
AuthResponseDTO toResponse(User user, String token);
```

Aqui o método tem **2 parâmetros**. MapStruct entende que `name` vem de `user.name` e (por nome) `token` vem direto do parâmetro `token`. É exatamente como o `AuthMapper` está escrito no projeto.

### 4. Constante ou expressão

Use quando o valor é fixo ou computado.

```java
@Mapping(target = "status", constant = "ACTIVE")
@Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
```

### 5. Mapeamento condicional / customizado

Use um método auxiliar dentro do próprio mapper:

```java
@Mapping(target = "displayName", source = "user", qualifiedByName = "buildDisplayName")
UserSummaryDTO toSummary(User user);

@Named("buildDisplayName")
default String buildDisplayName(User user) {
    return user.getName() + " (" + user.getEmail() + ")";
}
```

## Casos avançados: `default` methods

Quando o mapeamento não pode ser inferido — tipicamente **generics** ou **lógica condicional** — você escreve um `default` method na interface. Ele vira parte da impl normalmente (Java herda default methods).

Exemplo do projeto: converter `Page<Todo>` (Spring Data) em `PagedResponseDTO<TodoResponseDTO>`. MapStruct não sabe lidar com generics deste jeito, então escrevemos:

```java
default PagedResponseDTO<TodoResponseDTO> toPagedResponse(Page<Todo> page) {
    return new PagedResponseDTO<>(
            toResponseList(page.getContent()),   // reusa o método toResponseList que MapStruct gerou
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isLast()
    );
}
```

Note que **dentro do default** você chama os outros métodos do mapper (`toResponseList`) — você está dentro da interface, herda tudo.

## Coleções e listas: feito grátis

Se você declarar `toResponseList(List<Todo>) → List<TodoResponseDTO>` no mapper, MapStruct escreve o `for`-loop automaticamente — usando o `toResponse` que ele já gerou.

```java
List<TodoResponseDTO> toResponseList(List<Todo> todos);
```

Vira:
```java
public List<TodoResponseDTO> toResponseList(List<Todo> todos) {
    if (todos == null) return null;
    List<TodoResponseDTO> list = new ArrayList<>(todos.size());
    for (Todo todo : todos) list.add(toResponse(todo));
    return list;
}
```

Sem você precisar escrever 5 linhas de `stream().map().toList()` toda vez.

## Como usar — injeção no controller

A interface é injetada via construtor como qualquer bean do Spring (o impl é `@Component`):

```java
@RestController
@RequiredArgsConstructor
public class TodoController {

    private final TodoUseCase todoUseCase;
    private final TodoMapper todoMapper;          // injeção do impl gerado

    @PostMapping
    public ResponseEntity<TodoResponseDTO> create(@RequestBody @Valid TodoRequestDTO request) {
        Todo entity = todoMapper.toEntity(request);
        Todo saved = todoUseCase.create(getUser(), entity);
        return ResponseEntity.status(CREATED).body(todoMapper.toResponse(saved));
    }
}
```

Você nunca vê `TodoMapperImpl` no seu código — só a interface. Trocar de mapper (ou trocar MapStruct por outra coisa) sem mexer no controller.

## Convive bem com Lombok e `record`

**Lombok**: MapStruct enxerga `@Getter`/`@Setter`/`@Builder` normalmente. No `Todo` (entity com Lombok), o impl gerado usa `todo.getTitle()`, `Todo.builder().title()...build()`.

**Records**: MapStruct detecta automaticamente e usa o **canonical accessor** (`request.title()` em vez de `request.getTitle()`) e o **canonical constructor** (`new TodoResponseDTO(...)` em vez de builder).

Você não precisa mudar nada do mapper quando troca uma classe Lombok por record — só recompilar.

## Onde olhar o código real

| Pergunta | Onde |
|---|---|
| "O que estou pedindo pro MapStruct fazer?" | `auth/interfaces/mapper/AuthMapper.java`, `todo/interfaces/mapper/TodoMapper.java` |
| "O que ele realmente gerou?" | `target/generated-sources/annotations/.../*MapperImpl.java` |

Sempre que duvidar do mapeamento, abra o `*Impl.java` gerado. É o "show your work" — você vê linha por linha o código que vai rodar.

## Checklist — criar um novo mapper

1. Criar **interface** `XMapper` em `<feature>/interfaces/mapper/`.
2. Anotar com `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)`.
3. Declarar os **métodos** com source e target — sem corpo.
4. Para campos **não-mapeados intencionalmente** no target, adicionar `@Mapping(target = "X", ignore = true)`.
5. Para nomes diferentes ou campos aninhados, usar `@Mapping(target = "Y", source = "x.y")`.
6. Para generics ou lógica não-trivial, escrever `default` method.
7. **Compilar** (`mvn compile`). Se algo divergir, o build falha com mensagem clara apontando o campo.
8. **Conferir** o impl gerado em `target/generated-sources/annotations/...` se quiser ver o resultado.
9. **Injetar** via construtor onde for usar (`private final XMapper xMapper;`).

## Anti-padrões a evitar

### ❌ Escrever o `Impl` manualmente
Editar `target/generated-sources/.../XMapperImpl.java` — é regerado a cada `mvn compile`, suas mudanças somem. Se precisa lógica custom, faça `default` method na interface.

### ❌ `unmappedTargetPolicy` no default (`WARN`)
Esquecer de mapear campo → null silencioso em runtime. Sempre `ERROR`.

### ❌ Chamar o mapper estaticamente
`Mappers.getMapper(TodoMapper.class)` ignora o Spring, perde injeção em outros beans. Use o `componentModel = "spring"` + injeção via construtor.

### ❌ Lógica de negócio dentro do mapper
Mapper é puramente translação tipo-para-tipo. Validação, regras, side effects ficam no service. Mapper não deve saber que existe banco, transação, ou usuário logado.
