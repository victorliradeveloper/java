# Arquitetura Hexagonal — Todo App

## Visão Geral (Diagrama de Camadas)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         INTERFACES (Adapters de Entrada)                │
│                                                                         │
│   ┌───────────────────┐   ┌───────────────────┐   ┌─────────────────┐  │
│   │  TodoController   │   │  AuthController   │   │GlobalException  │  │
│   │  /api/v1/todos    │   │  /api/v1/auth     │   │Handler          │  │
│   └────────┬──────────┘   └────────┬──────────┘   └─────────────────┘  │
│            │  usa                  │  usa                               │
│            │  TodoMapper           │  AuthMapper                        │
└────────────┼──────────────────────┼────────────────────────────────────┘
             │                      │
             ▼                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                            DOMAIN / PORTS (IN)                         │
│                                                                        │
│        ┌──────────────────────┐   ┌──────────────────────┐            │
│        │    <<interface>>     │   │    <<interface>>     │            │
│        │     TodoUseCase      │   │     AuthUseCase      │            │
│        │                      │   │                      │            │
│        │ + create()           │   │ + register()         │            │
│        │ + findById()         │   │ + authenticate()     │            │
│        │ + findAll()          │   └──────────────────────┘            │
│        │ + update()           │                                        │
│        │ + partialUpdate()    │                                        │
│        │ + delete()           │                                        │
│        │ + findWithCursor()   │                                        │
│        └──────────┬───────────┘                                        │
└───────────────────┼────────────────────────────────────────────────────┘
                    │ implementado por
                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         APPLICATION (Casos de Uso)                     │
│                                                                        │
│   ┌─────────────────────────────┐   ┌────────────────────────────┐    │
│   │        TodoService          │   │        AuthService         │    │
│   │  implements TodoUseCase     │   │  implements AuthUseCase    │    │
│   │                             │   │                            │    │
│   │ @Cacheable / @CacheEvict   │   │ BCrypt password encoding   │    │
│   │                             │   │                            │    │
│   │ usa: TodoRepositoryPort     │   │ usa: UserRepositoryPort    │    │
│   └──────────────┬──────────────┘   └─────────────┬──────────────┘   │
└──────────────────┼───────────────────────────────┬─┘
                   │ usa (port out)                │ usa (port out)
                   ▼                               ▼
┌────────────────────────────────────────────────────────────────────────┐
│                            DOMAIN / PORTS (OUT)                        │
│                                                                        │
│   ┌──────────────────────────┐   ┌──────────────────────────────┐     │
│   │      <<interface>>       │   │        <<interface>>         │     │
│   │   TodoRepositoryPort     │   │     UserRepositoryPort       │     │
│   │                          │   │                              │     │
│   │ + save()                 │   │ + save()                     │     │
│   │ + findById()             │   │ + findByEmail()              │     │
│   │ + findAll()              │   │ + existsByEmail()            │     │
│   │ + delete()               │   └──────────────────────────────┘     │
│   │ + findWithCursor()       │                                         │
│   └──────────────┬───────────┘                                         │
└──────────────────┼──────────────────────────────────────────────────── ┘
                   │ implementado por
                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      INFRASTRUCTURE (Adapters de Saída)                │
│                                                                        │
│   ┌──────────────────────────────────────────────────────────────┐    │
│   │                      Persistence                             │    │
│   │                                                              │    │
│   │  ┌──────────────────────┐   ┌──────────────────────────┐   │    │
│   │  │ TodoRepositoryAdapter│   │ UserRepositoryAdapter    │   │    │
│   │  │ implements           │   │ implements               │   │    │
│   │  │ TodoRepositoryPort   │   │ UserRepositoryPort       │   │    │
│   │  └──────────┬───────────┘   └─────────────┬────────────┘   │    │
│   │             │ usa                          │ usa            │    │
│   │  ┌──────────▼───────────┐   ┌─────────────▼────────────┐   │    │
│   │  │  TodoJpaRepository   │   │  UserJpaRepository       │   │    │
│   │  │ extends JpaRepository│   │ extends JpaRepository    │   │    │
│   │  └──────────────────────┘   └──────────────────────────┘   │    │
│   │                                                              │    │
│   │  ┌──────────────────────┐                                   │    │
│   │  │  TodoSpecification   │ (filtros dinâmicos JPA Criteria)  │    │
│   │  └──────────────────────┘                                   │    │
│   └──────────────────────────────────────────────────────────────┘    │
│                                                                        │
│   ┌──────────────────────────────────────────────────────────────┐    │
│   │                       Security                               │    │
│   │                                                              │    │
│   │  JwtService  │  JwtAuthenticationFilter  │  SecurityConfig  │    │
│   │              │  RateLimitingFilter        │                  │    │
│   └──────────────────────────────────────────────────────────────┘    │
│                                                                        │
│   ┌──────────────────────────────────────────────────────────────┐    │
│   │                        Config                                │    │
│   │                                                              │    │
│   │         CacheConfig (Redis)  │  OpenApiConfig (Swagger)     │    │
│   └──────────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │     PostgreSQL       │  (prod)
        │          H2          │  (test)
        └──────────────────────┘
```

---

## Diagrama de Classes por Camada

```
DOMAIN
──────────────────────────────────────────────────────────────────

 <<entity>>                      <<entity>>
 Todo                            User
 ─────────────────               ─────────────────
 - id: Long                      - id: Long
 - title: String                 - name: String
 - description: String           - email: String
 - completed: Boolean            - password: String
 - dueDate: LocalDate
 - createdAt: LocalDateTime
 - updatedAt: LocalDateTime
 - userId: Long

 <<record>>                      <<record>>
 TodoFilter                      TodoPage
 ─────────────────               ─────────────────
 - title: String                 - todos: List<Todo>
 - completed: Boolean            - nextCursor: Long
 - dueDateFrom: LocalDate        - hasNext: Boolean
 - dueDateTo: LocalDate

 <<exceptions>>
 TodoNotFoundException
 InvalidCredentialsException
 UserAlreadyExistsException

──────────────────────────────────────────────────────────────────
PORTS (IN)
──────────────────────────────────────────────────────────────────

 <<interface>>                    <<interface>>
 TodoUseCase                      AuthUseCase
 ─────────────────────            ─────────────────────
 + create(Todo): Todo             + register(User): User
 + findById(Long): Todo           + authenticate(String,
 + findAll(TodoFilter,            |   String): String (JWT)
 |   Pageable): Page<Todo>
 + update(Long, Todo): Todo
 + partialUpdate(Long, Todo): Todo
 + delete(Long): void
 + findWithCursor(TodoFilter,
 |   Long, int): TodoPage

──────────────────────────────────────────────────────────────────
PORTS (OUT)
──────────────────────────────────────────────────────────────────

 <<interface>>                    <<interface>>
 TodoRepositoryPort               UserRepositoryPort
 ─────────────────────            ─────────────────────
 + save(Todo): Todo               + save(User): User
 + findById(Long): Optional<Todo> + findByEmail(String):
 + findAll(Spec, Pageable):       |   Optional<User>
 |   Page<Todo>                   + existsByEmail(String):
 + delete(Todo): void             |   Boolean
 + findWithCursor(Spec,
 |   Long, int): List<Todo>

──────────────────────────────────────────────────────────────────
APPLICATION
──────────────────────────────────────────────────────────────────

 TodoService                      AuthService
 implements TodoUseCase           implements AuthUseCase
 ─────────────────────────────    ────────────────────────────
 - repository: TodoRepositoryPort - repository: UserRepositoryPort
 - cacheManager                   - passwordEncoder: BCrypt
 ─────────────────────────────    ────────────────────────────
 + create()   @CacheEvict         + register()
 + findById() @Cacheable          + authenticate()
 + findAll()  @Cacheable
 + update()   @CacheEvict
 + partialUpdate() @CacheEvict
 + delete()   @CacheEvict
 + findWithCursor()

──────────────────────────────────────────────────────────────────
INTERFACES
──────────────────────────────────────────────────────────────────

 TodoController                   AuthController
 @RestController                  @RestController
 /api/v1/todos                    /api/v1/auth
 ──────────────────────           ──────────────────────
 - useCase: TodoUseCase           - useCase: AuthUseCase
 - mapper: TodoMapper             - mapper: AuthMapper
 ──────────────────────           ──────────────────────
 POST   /                         POST /register
 GET    /                         POST /login
 GET    /{id}
 PUT    /{id}
 PATCH  /{id}
 DELETE /{id}
 GET    /cursor
```

---

## Fluxo de uma Requisição (Sequência)

```
Cliente HTTP
    │
    │  POST /api/v1/todos  +  JWT token
    ▼
JwtAuthenticationFilter          ← valida token, seta SecurityContext
    │
    ▼
RateLimitingFilter               ← limita requisições por IP
    │
    ▼
TodoController
    │  1. TodoMapper.toEntity(requestDTO) → Todo (domain)
    │  2. todoUseCase.create(todo)
    ▼
TodoService                      ← implementa TodoUseCase
    │  3. todoRepositoryPort.save(todo)
    ▼
TodoRepositoryAdapter            ← implementa TodoRepositoryPort
    │  4. todoJpaRepository.save(todo)
    ▼
PostgreSQL                       ← persistência real
    │
    ◄──────────────────────────── retorna Todo salvo
    │
TodoRepositoryAdapter
    │  retorna Todo
TodoService
    │  retorna Todo          @CacheEvict dispara invalidação
TodoController
    │  5. TodoMapper.toResponse(todo) → TodoResponseDTO
    │  6. HTTP 201 Created + body
    ▼
Cliente HTTP
```

---

## Regra de Dependência

```
Direção das dependências (setas = "depende de"):

  Interfaces ──────────────────────────────► Domain (ports/in, models)
       │
       └──► Application ───────────────────► Domain (ports/out, models)
                 │
       Infrastructure ◄──────────────────── Domain (ports/out, models)
       Infrastructure ──────────────────────► (sem dependência para cima)

Resumo: tudo aponta para dentro (Domain).
O Domain não importa NADA de fora de si mesmo.
```

---

## Mapa de Pacotes

```
com.javanauta.todo_app/
│
├── domain/
│   ├── model/          Todo, User, TodoFilter, TodoPage
│   ├── exception/      TodoNotFoundException, InvalidCredentialsException
│   │                   UserAlreadyExistsException
│   └── port/
│       ├── in/         TodoUseCase, AuthUseCase
│       └── out/        TodoRepositoryPort, UserRepositoryPort
│
├── application/
│   ├── todo/           TodoService
│   └── auth/           AuthService
│
├── infrastructure/
│   ├── persistence/
│   │   ├── adapter/    TodoRepositoryAdapter, UserRepositoryAdapter
│   │   ├── repository/ TodoJpaRepository, UserJpaRepository
│   │   └── specification/ TodoSpecification
│   ├── security/       JwtService, JwtAuthenticationFilter
│   │                   RateLimitingFilter, SecurityConfig
│   └── config/         CacheConfig, OpenApiConfig
│
└── interfaces/
    ├── rest/           TodoController, AuthController
    ├── dto/
    │   ├── request/    TodoRequestDTO, TodoFilterDTO
    │   │               LoginRequestDTO, RegisterRequestDTO
    │   └── response/   TodoResponseDTO, AuthResponseDTO
    │                   ErrorResponseDTO, PagedResponseDTO
    │                   CursorPageResponseDTO
    ├── mapper/         TodoMapper, AuthMapper
    └── exception/      GlobalExceptionHandler
```

---

## Stack de Tecnologias

| Camada         | Tecnologia                              |
|----------------|-----------------------------------------|
| Linguagem      | Java 21                                 |
| Framework      | Spring Boot 3.5                         |
| Persistência   | Spring Data JPA / Hibernate 6           |
| Banco (prod)   | PostgreSQL 16                           |
| Banco (test)   | H2 (in-memory)                          |
| Migrações      | Flyway                                  |
| Autenticação   | Spring Security + JWT (Auth0 java-jwt)  |
| Cache          | Redis                                   |
| Documentação   | OpenAPI / Swagger v3                    |
| Testes         | JUnit 5, Mockito, AssertJ, MockMvc      |
| Build          | Maven                                   |
| Container      | Docker / Docker Compose                 |
