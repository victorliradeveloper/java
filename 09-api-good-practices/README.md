# Todo App API

API REST para gerenciamento de tarefas, construída com Spring Boot 3.5 e PostgreSQL.

## Tecnologias

- Java 21
- Spring Boot 3.5
- Spring Data JPA / Hibernate 6
- Spring Security + JWT (Auth0 java-jwt)
- PostgreSQL 16
- Docker / Docker Compose

## Como executar

```bash
docker compose up --build
```

A API estará disponível em `http://localhost:8080`.

---

## Autenticação

As rotas de tarefas são protegidas por JWT. Faça o registro ou login para obter o token e envie-o no header de cada requisição:

```
Authorization: Bearer <token>
```

---

## Endpoints

### Autenticação

#### Registrar usuário

```
POST /api/v1/auth/register
```

**Body:**
```json
{
  "name": "Victor",
  "email": "victor@email.com",
  "password": "123456"
}
```

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `name` | string | sim | Nome do usuário |
| `email` | string | sim | Email válido |
| `password` | string | sim | Mínimo 6 caracteres |

**Resposta `201 Created`:**
```json
{
  "name": "Victor",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

#### Login

```
POST /api/v1/auth/login
```

**Body:**
```json
{
  "email": "victor@email.com",
  "password": "123456"
}
```

**Resposta `200 OK`:**
```json
{
  "name": "Victor",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

### Tarefas

> Todas as rotas abaixo requerem `Authorization: Bearer <token>`.

Base URL: `/api/v1/todos`

#### Criar tarefa

```
POST /api/v1/todos
```

**Body:**
```json
{
  "title": "Estudar Spring Boot",
  "description": "Revisar conceitos de JPA",
  "dueDate": "2026-05-10T18:00:00"
}
```

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `title` | string | sim | Título da tarefa |
| `description` | string | não | Descrição detalhada |
| `dueDate` | datetime | não | Prazo no formato ISO 8601 |

**Resposta `201 Created`:**
```json
{
  "id": 1,
  "title": "Estudar Spring Boot",
  "description": "Revisar conceitos de JPA",
  "completed": false,
  "createdAt": "2026-04-29T10:00:00",
  "dueDate": "2026-05-10T18:00:00"
}
```

---

#### Listar tarefas (offset pagination)

```
GET /api/v1/todos
```

**Filtros disponíveis:**

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `title` | string | não | Busca parcial, case-insensitive |
| `completed` | boolean | não | Filtra por status (`true`/`false`) |
| `dueDateFrom` | datetime | não | Tarefas com prazo a partir de (ISO 8601) |
| `dueDateTo` | datetime | não | Tarefas com prazo até (ISO 8601) |

**Paginação e ordenação:**

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `page` | int | não | Número da página (padrão: `0`) |
| `size` | int | não | Itens por página (padrão: `20`, máximo: `100`) |
| `sort` | string | não | Campo e direção, ex: `title,asc` (padrão: `id,asc`) |

**Exemplos:**
```
GET /api/v1/todos?title=spring&completed=false
GET /api/v1/todos?dueDateFrom=2026-05-01T00:00:00&dueDateTo=2026-05-31T23:59:59
GET /api/v1/todos?completed=false&sort=dueDate,asc&page=0&size=10
```

**Resposta `200 OK`:**
```json
{
  "content": [
    {
      "id": 1,
      "title": "Estudar Spring Boot",
      "description": "Revisar conceitos de JPA",
      "completed": false,
      "createdAt": "2026-04-29T10:00:00",
      "dueDate": "2026-05-10T18:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3,
  "last": false
}
```

---

#### Listar tarefas (cursor pagination)

Indicado para grandes volumes de dados ou listagens em tempo real. Não sofre com o problema de drift do offset quando novos registros são inseridos.

```
GET /api/v1/todos/cursor?size=20
GET /api/v1/todos/cursor?cursor=20&size=20
```

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `cursor` | Long | não | ID do último item recebido (omitir para primeira página) |
| `size` | int | não | Itens por página (padrão: 20) |

**Resposta `200 OK`:**
```json
{
  "content": [...],
  "nextCursor": 40,
  "hasNext": true
}
```

> Para navegar: use o `nextCursor` retornado como parâmetro `cursor` na próxima requisição. Quando `hasNext` for `false`, não há mais páginas.

---

#### Buscar tarefa por ID

```
GET /api/v1/todos/{id}
```

**Resposta `200 OK`:** objeto da tarefa  
**Resposta `404 Not Found`:** tarefa não encontrada

---

#### Atualizar tarefa

```
PUT /api/v1/todos/{id}
```

**Body:** mesmo formato do `POST`  
**Resposta `200 OK`:** tarefa atualizada  
**Resposta `404 Not Found`:** tarefa não encontrada

---

#### Concluir tarefa

```
PATCH /api/v1/todos/{id}/complete
```

**Resposta `200 OK`:** tarefa com `completed: true`  
**Resposta `404 Not Found`:** tarefa não encontrada

---

#### Deletar tarefa

```
DELETE /api/v1/todos/{id}
```

**Resposta `204 No Content`:** tarefa removida  
**Resposta `404 Not Found`:** tarefa não encontrada

---

## Respostas de erro

Todos os erros seguem estrutura consistente:

| Status | Situação | Exemplo de body |
|--------|----------|-----------------|
| `400` | Dados inválidos na requisição | `{"title": "Title is required"}` |
| `401` | Credenciais inválidas ou token ausente | `{"error": "Invalid email or password"}` |
| `404` | Recurso não encontrado | `{"error": "Todo not found with id: 1"}` |
| `409` | Email já cadastrado | `{"error": "User already exists with email: victor@email.com"}` |
| `500` | Erro interno inesperado | `{"error": "Internal server error. Please try again later."}` |
