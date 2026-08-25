# Visão Geral do Projeto

## O que é

Uma API REST de gerenciamento de tarefas (Todo App) construída com **Spring Boot 3.5** e **Java 21**, focada em boas práticas de desenvolvimento: autenticação JWT, migrations com Flyway, paginação, filtros dinâmicos e testes automatizados.

O projeto foi desenvolvido de forma incremental como material de estudo. Cada funcionalidade resolveu um problema real encontrado durante o desenvolvimento e está documentada na pasta `docs/`.

---

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5 |
| Banco de dados | PostgreSQL 16 |
| Banco de testes | H2 (in-memory) |
| Migrations | Flyway |
| Autenticação | Spring Security + JWT (Auth0 java-jwt) |
| Testes | JUnit 5 + Mockito + AssertJ |
| Container | Docker + Docker Compose |

---

## Funcionalidades implementadas

### Autenticação
- `POST /api/v1/auth/register` — cadastro de usuário com senha criptografada (BCrypt)
- `POST /api/v1/auth/login` — login com retorno de token JWT
- Todas as rotas de Todo exigem token Bearer no header `Authorization`

### Gerenciamento de tarefas (Todo)
- `POST /api/v1/todos` — criar tarefa
- `GET /api/v1/todos` — listar com filtros e paginação por offset
- `GET /api/v1/todos/cursor` — listar com paginação por cursor
- `GET /api/v1/todos/{id}` — buscar por ID
- `PUT /api/v1/todos/{id}` — atualizar tarefa
- `PATCH /api/v1/todos/{id}/complete` — marcar como concluída
- `DELETE /api/v1/todos/{id}` — remover tarefa

Cada usuário só vê e manipula as próprias tarefas — isolamento garantido pela relação `@ManyToOne` entre `Todo` e `User`.

---

## Estrutura do projeto

```
src/main/java/com/javanauta/todo_app/
├── controller/
│   ├── AuthController.java       — endpoints de autenticação
│   └── TodoController.java       — endpoints de tarefas
├── service/
│   ├── AuthService.java          — lógica de register/login
│   └── TodoService.java          — lógica de negócio das tarefas
├── model/
│   ├── User.java                 — entidade de usuário
│   └── Todo.java                 — entidade de tarefa
├── dto/
│   ├── LoginRequestDTO.java
│   ├── RegisterRequestDTO.java
│   ├── AuthResponseDTO.java
│   ├── TodoRequestDTO.java
│   ├── TodoResponseDTO.java
│   ├── TodoFilterDTO.java
│   ├── PagedResponseDTO.java     — wrapper para paginação por offset
│   └── CursorPageResponseDTO.java — wrapper para paginação por cursor
├── repository/
│   ├── UserRepository.java
│   └── TodoRepository.java
├── specification/
│   └── TodoSpecification.java    — filtros dinâmicos com JPA Specification
├── security/
│   ├── JwtService.java           — gera e valida tokens JWT
│   ├── JwtAuthenticationFilter.java — intercepta requisições e autentica
│   └── SecurityConfig.java       — configuração do Spring Security
└── exception/
    ├── GlobalExceptionHandler.java
    ├── TodoNotFoundException.java
    ├── InvalidCredentialsException.java
    └── UserAlreadyExistsException.java

src/main/resources/
├── application.properties        — configurações comuns (prod)
├── application-dev.properties    — sobrescreve localização do Flyway no dev
└── db/
    ├── migration/
    │   └── V1__create_tables.sql — schema completo (todos os ambientes)
    └── dev/
        └── V2__seed_data.sql     — dados de exemplo (só no profile dev)
```

---

## Banco de dados

O schema é gerenciado exclusivamente pelo **Flyway**. O Hibernate usa `ddl-auto=validate` — ele só confere se as tabelas batem com as entidades, sem criar ou alterar nada.

### Tabelas

**`users`**
| Coluna | Tipo | Observação |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| name | VARCHAR(100) | obrigatório |
| email | VARCHAR(255) | único, obrigatório |
| password | VARCHAR(255) | hash BCrypt |

**`todo`**
| Coluna | Tipo | Observação |
|--------|------|-----------|
| id | BIGINT | PK, via sequência `todo_seq` |
| title | VARCHAR(255) | obrigatório |
| description | TEXT | opcional |
| completed | BOOLEAN | default false |
| created_at | TIMESTAMP | obrigatório |
| due_date | TIMESTAMP | opcional |
| user_id | BIGINT | FK para `users` |

### Ambientes

| Ambiente | Migrations executadas |
|----------|-----------------------|
| Produção | V1 (schema) |
| Dev (Docker) | V1 (schema) + V2 (seed) |
| Testes | Flyway desativado, H2 com create-drop |

---

## Autenticação

O fluxo de autenticação segue o padrão **stateless com JWT**:

```
1. Cliente faz POST /auth/register ou /auth/login
2. API retorna { name, token }
3. Cliente inclui "Authorization: Bearer <token>" em todas as requisições
4. JwtAuthenticationFilter valida o token e popula o SecurityContext
5. Controller obtém o usuário autenticado via SecurityContextHolder
```

O token expira em **2 horas** (configurável via `jwt.expiration-hours`).

---

## Como rodar

```bash
# Subir banco e aplicação com dados de exemplo
docker compose up --build

# Recriar tudo do zero (apaga o volume do banco)
docker compose down -v && docker compose up --build
```

Usuário de exemplo disponível após subida com profile `dev`:
- Email: `admin@email.com`
- Senha: `admin123`

---

## Documentação adicional

- [`docs/flyway-migration.md`](flyway-migration.md) — como o Flyway foi configurado e por que substituiu o `ddl-auto=update`
- [`docs/hibernate-ddl-update-not-null.md`](hibernate-ddl-update-not-null.md) — erro de coluna `NOT NULL` que motivou a adoção do Flyway
- [`docs/testes/`](testes/) — conceitos e exemplos dos testes automatizados
- [`README.md`](../README.md) — referência completa de todas as rotas da API
