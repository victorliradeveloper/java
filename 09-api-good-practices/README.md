# Todo App API

API REST para gerenciamento de tarefas, construída com Spring Boot 3.5 e PostgreSQL.

## Tecnologias

- Java 21
- Spring Boot 3.5
- Spring Data JPA / Hibernate 6
- PostgreSQL 16
- Docker / Docker Compose

## Como executar

```bash
docker compose up --build
```

A API estará disponível em `http://localhost:8080`.

---

## Endpoints

Base URL: `/api/v1/todos`

### Criar tarefa

```
POST /api/v1/todos
```

**Body:**
```json
{
  "titulo": "Estudar Spring Boot",
  "descricao": "Revisar conceitos de JPA",
  "dataLimite": "2026-05-10T18:00:00"
}
```

**Resposta `201 Created`:**
```json
{
  "id": 1,
  "titulo": "Estudar Spring Boot",
  "descricao": "Revisar conceitos de JPA",
  "concluido": false,
  "dataCriacao": "2026-04-29T10:00:00",
  "dataLimite": "2026-05-10T18:00:00"
}
```

---

### Listar tarefas (offset pagination)

```
GET /api/v1/todos
```

**Filtros disponíveis:**

| Parâmetro      | Tipo     | Obrigatório | Descrição                                        |
|----------------|----------|-------------|--------------------------------------------------|
| `titulo`       | string   | não         | Busca parcial, case-insensitive                  |
| `concluido`    | boolean  | não         | Filtra por status (`true`/`false`)               |
| `dataLimiteDe` | datetime | não         | Tarefas com prazo a partir de (ISO 8601)         |
| `dataLimiteAte`| datetime | não         | Tarefas com prazo até (ISO 8601)                 |

**Paginação e ordenação:**

| Parâmetro | Tipo   | Obrigatório | Descrição                                           |
|-----------|--------|-------------|-----------------------------------------------------|
| `page`    | int    | não         | Número da página (padrão: `0`)                      |
| `size`    | int    | não         | Itens por página (padrão: `20`, máximo: `100`)      |
| `sort`    | string | não         | Campo e direção, ex: `titulo,asc` (padrão: `id,asc`)|

**Exemplos:**
```
GET /api/v1/todos?titulo=spring&concluido=false
GET /api/v1/todos?dataLimiteDe=2026-05-01T00:00:00&dataLimiteAte=2026-05-31T23:59:59
GET /api/v1/todos?concluido=false&sort=dataLimite,asc&page=0&size=10
```

**Resposta `200 OK`:**
```json
{
  "content": [
    {
      "id": 1,
      "titulo": "Estudar Spring Boot",
      "descricao": "Revisar conceitos de JPA",
      "concluido": false,
      "dataCriacao": "2026-04-29T10:00:00",
      "dataLimite": "2026-05-10T18:00:00"
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

### Listar tarefas (cursor pagination)

Indicado para grandes volumes de dados ou listagens em tempo real. Não sofre com o problema de drift do offset quando novos registros são inseridos.

```
GET /api/v1/todos/cursor?size=20
GET /api/v1/todos/cursor?cursor=20&size=20
```

| Parâmetro | Tipo | Obrigatório | Descrição                                              |
|-----------|------|-------------|--------------------------------------------------------|
| `cursor`  | Long | não         | ID do último item recebido (omitir para primeira página) |
| `size`    | int  | não         | Itens por página (padrão: 20)                          |

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

### Buscar tarefa por ID

```
GET /api/v1/todos/{id}
```

**Resposta `200 OK`:** objeto da tarefa  
**Resposta `404 Not Found`:** tarefa não encontrada

---

### Atualizar tarefa

```
PUT /api/v1/todos/{id}
```

**Body:** mesmo formato do `POST`  
**Resposta `200 OK`:** tarefa atualizada  
**Resposta `404 Not Found`:** tarefa não encontrada

---

### Concluir tarefa

```
PATCH /api/v1/todos/{id}/concluir
```

**Resposta `200 OK`:** tarefa com `concluido: true`  
**Resposta `404 Not Found`:** tarefa não encontrada

---

### Deletar tarefa

```
DELETE /api/v1/todos/{id}
```

**Resposta `204 No Content`:** tarefa removida  
**Resposta `404 Not Found`:** tarefa não encontrada

---

## Respostas de erro

Todos os erros seguem estrutura consistente:

| Status | Situação                        | Exemplo de body                               |
|--------|---------------------------------|-----------------------------------------------|
| `400`  | Dados inválidos na requisição   | `{"titulo": "Título é obrigatório"}`          |
| `404`  | Recurso não encontrado          | `{"erro": "Tarefa não encontrada com id: 1"}` |
| `500`  | Erro interno inesperado         | `{"erro": "Erro interno. Tente novamente mais tarde."}` |
