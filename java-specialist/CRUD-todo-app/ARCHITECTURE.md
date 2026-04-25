 # Arquitetura e Comunicação entre Camadas

Este projeto segue uma arquitetura em camadas baseada nos princípios do Spring Boot, separando responsabilidades entre Controller, Service, Repository, Entity e DTOs.

---

## Visão Geral do Fluxo

```
Requisição HTTP
      ↓
  Controller          ← recebe JSON e valida via @Valid
      ↓
   Service            ← aplica regras de negócio, converte DTO ↔ Entity
      ↓
  Repository          ← executa operações no banco via JPA
      ↓
   Entity             ← representa a tabela no banco de dados
      ↓
  Repository          ← retorna a Entity salva/consultada
      ↓
   Service            ← converte Entity → ResponseDTO
      ↓
  Controller          ← retorna ResponseDTO como JSON
      ↓
Resposta HTTP
```

---

## Camadas e Responsabilidades

### Controller (`controller/TodoController.java`)

Ponto de entrada das requisições HTTP. Responsável por:

- Mapear rotas e métodos HTTP (`@GetMapping`, `@PostMapping`, etc.)
- Receber e validar o corpo da requisição com `@RequestBody @Valid`
- Delegar a lógica para o Service
- Retornar a resposta com o status HTTP correto

Não contém regras de negócio. Apenas orquestra entrada e saída.

```
POST   /todos         → create(TodoRequestDTO)   → 201 Created
GET    /todos         → findAll()                → 200 OK
GET    /todos/{id}    → findById(id)             → 200 OK
PUT    /todos/{id}    → update(id, TodoUpdateDTO)→ 200 OK
DELETE /todos/{id}    → delete(id)               → 204 No Content
```

**Dependência injetada:** `TodoService` via construtor (`@RequiredArgsConstructor` do Lombok).

---

### Service (`service/TodoService.java`)

Camada de negócio. Responsável por:

- Receber os DTOs de entrada vindos do Controller
- Converter DTOs em Entities antes de persistir
- Converter Entities em ResponseDTOs antes de retornar
- Lançar exceções de domínio (ex: `EntityNotFoundException`)
- Coordenar chamadas ao Repository

```java
// Exemplo de conversão DTO → Entity no create()
Todo todo = Todo.builder()
    .title(dto.title())
    .description(dto.description())
    .completed(false)
    .build();

// Exemplo de conversão Entity → ResponseDTO no toResponse()
return new TodoResponseDTO(
    todo.getId(),
    todo.getTitle(),
    todo.getDescription(),
    todo.isCompleted(),
    todo.getCreatedAt()
);
```

**Dependência injetada:** `TodoRepository` via construtor.

---

### Repository (`infrastructure/repository/TodoRepository.java`)

Camada de acesso a dados. Responsável por:

- Abstrair as operações de banco de dados via Spring Data JPA
- Estende `JpaRepository<Todo, String>`, herdando `save()`, `findById()`, `findAll()`, `delete()` etc.
- Sem lógica de negócio — apenas I/O com o banco

O Spring gera a implementação automaticamente em tempo de execução.

---

### Entity (`infrastructure/entity/Todo.java`)

Representa a tabela `todos` no banco de dados. Responsável por:

- Mapear campos Java para colunas SQL via anotações JPA
- Definir lifecycle hooks (`@PrePersist` para setar `createdAt`)
- Não carregar lógica de negócio — apenas estrutura de dados

```
id          → String (UUID gerado pelo banco)
title       → String (obrigatório)
description → String (opcional)
completed   → boolean (obrigatório)
createdAt   → LocalDateTime (definido automaticamente no insert, imutável)
```

A Entity **nunca é exposta diretamente** para fora da camada de Service — ela é convertida para um ResponseDTO antes.

---

### DTOs (`dto/`)

Objetos de transferência de dados. Separam o contrato da API do modelo do banco.

| DTO | Direção | Uso |
|---|---|---|
| `TodoRequestDTO` | Entrada | Corpo do POST (criar) — `title` obrigatório |
| `TodoUpdateDTO` | Entrada | Corpo do PUT (atualizar) — todos os campos opcionais |
| `TodoResponseDTO` | Saída | Retornado em todas as respostas bem-sucedidas |

Usar DTOs distintos evita que mudanças no banco afetem o contrato da API e vice-versa.

---

## Injeção de Dependência

O projeto usa **injeção por construtor**, gerada automaticamente pelo Lombok:

```java
@RequiredArgsConstructor  // gera o construtor com os campos `final`
public class TodoService {
    private final TodoRepository repository; // Spring injeta automaticamente
}
```

Isso é equivalente a usar `@Autowired` no construtor, mas com menos código. O Spring detecta o bean pelo tipo e o injeta.

---

## Configuração e Banco de Dados

- Banco: **H2 in-memory** (reiniciado a cada execução)
- Esquema: criado automaticamente pelo Hibernate (`ddl-auto=create-drop`)
- Porta: `8081`
- Console H2: `http://localhost:8081/h2-console` (para inspecionar os dados)

---

## Diagrama de Dependências

```
RegistrationApplication
         │
         └── TodoController
                  │
                  └── TodoService
                           │
                           ├── TodoRepository ──── Todo (Entity)
                           │
                           ├── TodoRequestDTO  (entrada)
                           ├── TodoUpdateDTO   (entrada)
                           └── TodoResponseDTO (saída)
```

Cada camada conhece apenas a camada imediatamente abaixo. O Controller não acessa o Repository diretamente, e a Entity nunca chega ao Controller.
