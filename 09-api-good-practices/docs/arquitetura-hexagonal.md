# Arquitetura Hexagonal (Ports & Adapters)

## O que é

A Arquitetura Hexagonal, criada por Alistair Cockburn, organiza o sistema em torno do **domínio de negócio**. A ideia central é que o domínio não depende de nada externo — ele não sabe se está falando com um banco de dados, uma API REST ou um cache. Quem depende de quem é sempre de fora para dentro.

O nome "hexagonal" vem do desenho original com um hexágono no centro representando o domínio, rodeado de adaptadores. Também é chamada de **Ports & Adapters** porque usa dois conceitos simples:

- **Port (porta):** uma interface Java que define um contrato
- **Adapter (adaptador):** uma implementação concreta desse contrato

---

## Estrutura de pacotes

```
com.javanauta.todo_app/
├── domain/                          ← núcleo puro, sem dependências externas
│   ├── model/                       ← entidades e value objects
│   │   ├── Todo.java
│   │   ├── User.java
│   │   ├── TodoFilter.java          ← critérios de busca (record)
│   │   └── TodoPage.java            ← resultado de paginação por cursor (record)
│   ├── exception/                   ← exceções de negócio
│   │   ├── TodoNotFoundException.java
│   │   ├── InvalidCredentialsException.java
│   │   └── UserAlreadyExistsException.java
│   └── port/
│       ├── in/                      ← driving ports (o que o domínio oferece)
│       │   ├── TodoUseCase.java
│       │   └── AuthUseCase.java
│       └── out/                     ← driven ports (o que o domínio precisa)
│           ├── TodoRepositoryPort.java
│           └── UserRepositoryPort.java
│
├── application/                     ← implementa os casos de uso
│   ├── todo/
│   │   └── TodoService.java         ← implements TodoUseCase
│   └── auth/
│       └── AuthService.java         ← implements AuthUseCase
│
├── infrastructure/                  ← adaptadores para tecnologias externas
│   ├── persistence/
│   │   ├── adapter/
│   │   │   ├── TodoRepositoryAdapter.java   ← implements TodoRepositoryPort
│   │   │   └── UserRepositoryAdapter.java   ← implements UserRepositoryPort
│   │   ├── repository/
│   │   │   ├── TodoJpaRepository.java       ← Spring Data JPA
│   │   │   └── UserJpaRepository.java
│   │   └── specification/
│   │       └── TodoSpecification.java       ← filtros dinâmicos com JPA Criteria
│   ├── security/
│   │   ├── JwtService.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── RateLimitingFilter.java
│   │   └── SecurityConfig.java
│   └── config/
│       ├── CacheConfig.java
│       └── OpenApiConfig.java
│
└── interfaces/                      ← adaptadores de entrada (REST)
    ├── rest/
    │   ├── TodoController.java
    │   └── AuthController.java
    ├── dto/
    │   ├── request/                 ← dados que entram pela API
    │   └── response/                ← dados que saem pela API
    ├── mapper/
    │   ├── TodoMapper.java          ← DTO ↔ domain model
    │   └── AuthMapper.java
    └── exception/
        └── GlobalExceptionHandler.java
```

---

## As quatro camadas

### 1. Domain

O coração da aplicação. Contém apenas Java puro — sem Spring, sem JPA, sem nenhum framework.

- **Models:** as entidades de negócio (`Todo`, `User`) e tipos auxiliares (`TodoFilter`, `TodoPage`)
- **Exceptions:** representam situações de erro do negócio, não da infraestrutura
- **Ports:** interfaces que definem contratos entre camadas

```
Domain não importa nada de fora do próprio pacote domain.
```

### 2. Application

Implementa os casos de uso definidos pelas driving ports. Orquestra o fluxo de negócio usando as driven ports, sem saber como elas são implementadas.

```java
// TodoService conhece apenas interfaces, nunca implementações concretas
@Service
@RequiredArgsConstructor
public class TodoService implements TodoUseCase {

    private final TodoRepositoryPort todoRepository; // porta, não JPA

    @Transactional
    public Todo create(User user, Todo todo) {
        todo.setUser(user);
        return todoRepository.save(todo);
    }
}
```

### 3. Infrastructure

Adaptadores para tecnologias concretas. Implementa as driven ports para conectar o domínio ao mundo real.

```java
// TodoRepositoryAdapter traduz entre o domínio e o Spring Data JPA
@Repository
@RequiredArgsConstructor
public class TodoRepositoryAdapter implements TodoRepositoryPort {

    private final TodoJpaRepository jpaRepository;

    @Override
    public Page<Todo> findAll(TodoFilter filter, User user, Pageable pageable) {
        return jpaRepository.findAll(TodoSpecification.withFilters(filter, user), pageable);
    }
}
```

### 4. Interfaces

Adaptadores de entrada. Recebem chamadas externas (HTTP), convertem para o modelo de domínio via mappers, chamam o caso de uso e devolvem a resposta no formato esperado.

```java
// TodoController depende de TodoUseCase (porta), não de TodoService (implementação)
@RestController
@RequiredArgsConstructor
public class TodoController {

    private final TodoUseCase todoUseCase;   // porta driving
    private final TodoMapper todoMapper;

    @PostMapping
    public ResponseEntity<TodoResponseDTO> create(@RequestBody @Valid TodoRequestDTO request) {
        Todo saved = todoUseCase.create(getAuthenticatedUser(), todoMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(todoMapper.toResponse(saved));
    }
}
```

---

## Ports em detalhe

### Driving Ports (in) — o que o domínio expõe

Define os casos de uso disponíveis para o mundo externo. Quem chama são os adaptadores de entrada (controllers).

```java
public interface TodoUseCase {
    Todo create(User user, Todo todo);
    Page<Todo> findAll(User user, TodoFilter filter, Pageable pageable);
    TodoPage listWithCursor(User user, Long cursor, int size);
    Todo getById(User user, Long id);
    Todo update(User user, Long id, Todo updates);
    Todo complete(User user, Long id);
    void delete(User user, Long id);
}
```

### Driven Ports (out) — o que o domínio precisa

Define o que o domínio exige da infraestrutura. Quem implementa são os adaptadores de saída (repositórios, serviços externos).

```java
public interface TodoRepositoryPort {
    Todo save(Todo todo);
    Optional<Todo> findById(Long id);
    Page<Todo> findAll(TodoFilter filter, User user, Pageable pageable);
    List<Todo> findWithCursor(User user, Long cursor, Pageable pageable);
    void delete(Todo todo);
}
```

---

## Fluxo de uma requisição

```
HTTP Request
    │
    ▼
TodoController          (interfaces/rest)
    │ converte DTO → domain via TodoMapper
    │
    ▼
TodoUseCase ──────────► TodoService      (application/todo)
  (porta)                   │ usa somente TodoRepositoryPort
                            │
                            ▼
                    TodoRepositoryPort   (domain/port/out)
                            │
                            ▼
                    TodoRepositoryAdapter  (infrastructure/persistence)
                            │ usa Spring Data JPA internamente
                            │
                            ▼
                    TodoJpaRepository + PostgreSQL
```

O controller nunca chama `TodoService` diretamente. Ele chama `TodoUseCase`. O Spring injeta `TodoService` porque ele implementa a interface — mas o controller não sabe disso.

---

## Benefícios neste projeto

| Problema | Solução hexagonal |
|---|---|
| Controller dependia de `TodoRepository` (JPA) diretamente | Controller depende de `TodoUseCase`, zero conhecimento de persistência |
| Trocar banco de dados exigia mudar o serviço | Basta criar outro adapter que implemente `TodoRepositoryPort` |
| Testar o serviço exigia mockar o Spring Data | Mock de `TodoRepositoryPort` — interface simples, sem Specification |
| DTOs vazando para dentro do domínio | Mappers na camada `interfaces` isolam o domínio dos contratos HTTP |
| Lógica de negócio misturada com infraestrutura | Domínio puro, sem anotações de framework |

---

## Regra de dependência

```
interfaces → application → domain ← infrastructure
```

As setas representam "depende de". O domínio não aponta para ninguém — todos apontam para ele. Essa é a regra fundamental: **dependências sempre em direção ao centro.**
