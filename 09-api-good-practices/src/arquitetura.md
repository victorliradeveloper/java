# Arquitetura do Projeto: Todo App API

Este projeto usa **Arquitetura Hexagonal (Ports & Adapters)** com 4 camadas principais.

A ideia central é: **o domínio (regras de negócio) não depende de nada externo** — nem banco de dados, nem framework web.

---

## As 4 Camadas

```
interfaces/     ← recebe HTTP
application/    ← executa regras de negócio
domain/         ← define contratos (ports)
infrastructure/ ← implementa acesso a dados, segurança
```

---

## Como as Camadas se Comunicam

### 1. `interfaces/` — Camada de entrada HTTP

O **Controller** recebe a requisição REST. Ele:
- Recebe um DTO (ex: `TodoRequestDTO`)
- Chama o **Mapper** para converter DTO → Entity
- Chama o **UseCase** (uma interface, não a implementação direta)

```java
// TodoController.java
controller → todoMapper.toEntity(dto) → todoUseCase.create(entity)
```

### 2. `domain/port/in/` — O contrato do que pode ser feito

`TodoUseCase` é uma **interface** que define operações como `create()`, `getById()`, `delete()`.

O controller só conhece essa interface — não sabe quem implementa.

### 3. `application/` — Quem executa a lógica

`TodoService` implementa `TodoUseCase`. Aqui fica a lógica de negócio (validações, cache `@Cacheable`, etc.).

Para persistir dados, o Service não chama o JPA direto — ele chama uma **porta de saída** (`TodoRepositoryPort`), que também é uma interface.

### 4. `domain/port/out/` — Contrato de acesso a dados

`TodoRepositoryPort` define métodos como `save()`, `findById()`.

O domínio define **o que precisa**, sem saber como o banco funciona.

### 5. `infrastructure/persistence/adapter/` — Quem implementa o acesso ao banco

`TodoRepositoryAdapter` implementa `TodoRepositoryPort`. Ela traduz as chamadas do domínio para chamadas Spring Data JPA:

```java
// TodoRepositoryAdapter.java
@Override
public Todo save(Todo todo) {
    return todoJpaRepository.save(todo); // Spring Data JPA
}
```

---

## Fluxo Completo de uma Requisição

```
POST /api/v1/todos
       ↓
  JwtAuthenticationFilter    (valida o token JWT)
       ↓
  TodoController             (recebe TodoRequestDTO)
       ↓
  TodoMapper                 (converte DTO → entity Todo)
       ↓
  TodoUseCase (interface)    ← controller depende só dessa interface
       ↓
  TodoService (implements)   (lógica de negócio, cache)
       ↓
  TodoRepositoryPort (interface) ← service depende só dessa interface
       ↓
  TodoRepositoryAdapter (implements)
       ↓
  TodoJpaRepository          (Spring Data JPA)
       ↓
  PostgreSQL
       ↓
  [resposta sobe o mesmo caminho]
       ↓
  TodoMapper                 (converte entity → TodoResponseDTO)
       ↓
  HTTP 201 Created
```

---

## Direção das Dependências

```
interfaces → application → domain ← infrastructure
```

O `domain/` e `application/` **nunca importam** classes de `infrastructure/` ou `interfaces/`.

Isso permite:
- Trocar PostgreSQL por MongoDB sem mudar a lógica de negócio
- Testar o `TodoService` sem banco real (mock do `TodoRepositoryPort`)
- Trocar REST por GraphQL sem tocar nos services

---

## Resumo das Dependências

| Camada           | Depende de                   | É usada por      |
|------------------|------------------------------|------------------|
| `interfaces/`    | `application/` (via UseCase) | Ninguém          |
| `application/`   | `domain/port/out/`           | `interfaces/`    |
| `domain/`        | Nada externo                 | Todos            |
| `infrastructure/`| `domain/`                    | `application/`   |

---

## Tecnologias

| Tecnologia       | Uso                          |
|------------------|------------------------------|
| Spring Boot 3.5  | Framework principal          |
| Spring Data JPA  | Acesso ao banco              |
| Spring Security  | Autenticação/autorização     |
| PostgreSQL 16    | Banco de dados relacional    |
| Redis 7          | Cache de leitura             |
| JWT (Auth0)      | Tokens de autenticação       |
| Lombok           | Geração de código boilerplate|
| Docker Compose   | Ambiente de desenvolvimento  |
| OpenAPI/Swagger  | Documentação da API          |

---

## Estrutura de Pacotes

```
com.javanauta.todo_app/
├── application/
│   ├── auth/AuthService.java          # Implementa AuthUseCase
│   └── todo/TodoService.java          # Implementa TodoUseCase
├── domain/
│   ├── exception/                     # Exceções de negócio
│   ├── model/                         # Entidades e records
│   └── port/
│       ├── in/                        # Contratos de entrada (UseCases)
│       └── out/                       # Contratos de saída (Repositories)
├── infrastructure/
│   ├── config/                        # Redis, OpenAPI
│   ├── persistence/
│   │   ├── adapter/                   # Implementam os ports de saída
│   │   ├── repository/                # Spring Data JPA
│   │   └── specification/             # Queries dinâmicas
│   └── security/                      # JWT, filtros, rate limiting
└── interfaces/
    ├── dto/                           # Request e Response DTOs
    ├── exception/                     # GlobalExceptionHandler
    ├── mapper/                        # Conversão DTO ↔ Entity
    └── rest/                          # Controllers REST
```
