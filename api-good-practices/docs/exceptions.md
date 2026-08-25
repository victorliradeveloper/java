# Tratamento de Exceções

Este documento descreve o padrão de tratamento de exceções adotado neste projeto e o racional por trás de cada decisão.

## TL;DR

- **Service lança** exceções de domínio.
- **Domain define** as classes dessas exceções (`domain/exception/*`).
- **Controller ignora** — deixa a exceção subir limpa, sem `try/catch`.
- **`@RestControllerAdvice` traduz** a exceção para HTTP em um único lugar.
- **Spring Security** tem entry point e access denied handler customizados para padronizar 401/403 no mesmo formato.

## Onde o status HTTP é decidido

Pergunta comum: "as exceptions têm a mensagem, mas onde define 404, 422, 500?"

**Resposta: as exceptions de domínio NÃO carregam status code.** O status vive em 3 lugares diferentes do código, separados por intenção:

| Tipo de resposta | Onde o status é definido | Como |
|---|---|---|
| **Sucesso (200/201/204)** | No **Controller** | `ResponseEntity.ok(...)`, `ResponseEntity.status(HttpStatus.CREATED).body(...)`, `ResponseEntity.noContent().build()` |
| **Erro de domínio (404/409/422/...)** | No **`@RestControllerAdvice` da feature** (`TodoExceptionHandler`, `AuthExceptionHandler`) | `ResponseEntity.status(HttpStatus.X).body(ErrorResponseDTO.of(...))` |
| **Erro de framework (400/500)** | No **`GlobalExceptionHandler`** (shared) | Idem, para `MethodArgumentNotValidException`, `Exception` catch-all, etc. |
| **Erro de Spring Security (401/403)** | Em **`RestAuthenticationEntryPoint`** / **`RestAccessDeniedHandler`** | `response.setStatus(...)` direto no servlet response |

A exception em si só carrega **mensagem**:

```java
// TodoNotFoundException — não sabe nada de HTTP
public class TodoNotFoundException extends RuntimeException {
    public TodoNotFoundException(Long id) {
        super("Todo not found with id: " + id);
    }
}
```

O 404 só aparece quando o handler captura:

```java
// TodoExceptionHandler — AQUI o status é decidido
@ExceptionHandler(TodoNotFoundException.class)
public ResponseEntity<ErrorResponseDTO> handleNotFound(...) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)   // ← aqui
            .body(ErrorResponseDTO.of(HttpStatus.NOT_FOUND.value(), ex.getMessage(), ...));
}
```

**Vantagem**: a mesma exception poderia ser usada num CLI, batch, ou consumer de fila sem virar 404 — porque ela não tem HTTP grudado nela. Mudar de 404 para 410? Mexe 1 linha no handler, sem tocar exception, service ou controller.

**Anti-padrão a evitar**: anotar a exception com `@ResponseStatus(HttpStatus.NOT_FOUND)` ou estender `ResponseStatusException`. Spring respeita ambos, mas isso amarra a exception ao mundo HTTP — perde a separação de camadas.

## Em qual camada vive cada coisa

| Camada | Papel com exceções | Por quê |
|---|---|---|
| **Domain** | **Define** as exceções de negócio | A exceção é parte do vocabulário do domínio (ex: "todo não encontrado" existe antes de ter HTTP). Não depende de Spring nem de HTTP. |
| **Service (application)** | **Lança** as exceções | É onde a regra é violada. Validação de invariantes mora aqui. |
| **Controller (interfaces/rest)** | **Não trata** — apenas deixa subir | Controller só orquestra HTTP ↔ Service. Adicionar `try/catch` aqui duplicaria o mapeamento HTTP em cada endpoint. |
| **`@RestControllerAdvice`** | **Captura** e converte para HTTP | Único lugar que conhece status codes. Mudou de 404 para 410? Muda 1 linha. |
| **Spring Security handlers** | Convertem erros de autenticação/autorização no mesmo formato | Sem isso, 401/403 retornam JSON do Spring default, quebrando o contrato. |

## Anatomia no projeto

### 1. Exceções de domínio
Classes simples em `domain/exception/`, estendem `RuntimeException`. Mensagem é fixa ou parametrizada — **não conhecem HTTP**.

```java
// domain/exception/TodoNotFoundException.java
public class TodoNotFoundException extends RuntimeException {
    public TodoNotFoundException(Long id) {
        super("Todo not found with id: " + id);
    }
}
```

Outras: `InvalidCredentialsException`, `UserAlreadyExistsException`.

**Boa prática de segurança**: `InvalidCredentialsException` usa mensagem genérica (`"Invalid email or password"`) — não diferencia "email não existe" de "senha errada", evitando enumeração de usuários.

### 2. Service lança a exceção
A regra de negócio é violada, o service lança. Sem `try/catch`, sem retornar `null`, sem `Optional` vazio subindo.

```java
// application/todo/TodoService.java
public Todo getById(User user, Long id) {
    return todoRepository.findById(id)
            .filter(todo -> todo.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new TodoNotFoundException(id));
}
```

```java
// application/auth/AuthService.java
public User register(User user) {
    if (userRepository.existsByEmail(user.getEmail())) {
        throw new UserAlreadyExistsException(user.getEmail());
    }
    // ...
}
```

> **Nota de design** — `TodoNotFoundException` também é lançada quando o todo existe mas pertence a outro usuário. Isso é intencional: retornar 404 em vez de 403 evita vazar "este recurso existe, mas não é seu" (mitigação de IDOR).

### 3. Controller deixa subir
**Zero `try/catch`**. A exceção atravessa o controller intacta.

```java
// interfaces/rest/TodoController.java
@GetMapping("/{id}")
public ResponseEntity<TodoResponseDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(todoMapper.toResponse(todoUseCase.getById(getAuthenticatedUser(), id)));
}
```

### 4. `GlobalExceptionHandler` traduz para HTTP
Único ponto que conhece status codes e formato de resposta de erro.

Localização: `interfaces/exception/GlobalExceptionHandler.java`.

| Exceção capturada | Status | Quando |
|---|---|---|
| `TodoNotFoundException` | 404 | Recurso não existe (ou não é do usuário) |
| `InvalidCredentialsException` | 401 | Login inválido |
| `UserAlreadyExistsException` | 409 | Email já cadastrado |
| `PastDueDateException` | 422 | `dueDate` no passado em create/update |
| `CompletedTodoCannotBeModifiedException` | 409 | Tentativa de editar todo já completed |
| `TodoLimitExceededException` | 409 | Usuário atingiu o limite (`app.todo.max-per-user`, default 100) |
| `InvalidCursorException` | 400 | Cursor negativo em `listWithCursor` |
| `MethodArgumentNotValidException` | 400 | `@Valid` falhou no body — retorna `fieldErrors` |
| `HttpMessageNotReadableException` | 400 | Body malformado ou ausente |
| `MethodArgumentTypeMismatchException` | 400 | Path/query param com tipo errado (ex: `GET /todos/abc`) |
| `ConstraintViolationException` | 400 | Validação em `@RequestParam`/`@PathVariable` |
| `NoResourceFoundException` | 404 | Rota inexistente |
| `DataIntegrityViolationException` | 409 | UK/FK violation no banco (ex: race condition) |
| `Exception` (catch-all) | 500 | Qualquer coisa não prevista — loga stack, devolve mensagem genérica |

### 5. Spring Security: entry point + access denied
Spring Security não passa pelo `@RestControllerAdvice` — ele tem o próprio fluxo de erro. Para manter o contrato, dois handlers customizados em `infrastructure/security/`:

| Handler | Status | Quando |
|---|---|---|
| `RestAuthenticationEntryPoint` | 401 | Token JWT ausente, inválido ou expirado |
| `RestAccessDeniedHandler` | 403 | Autenticado, mas sem permissão |

Plugados em `SecurityConfig`:
```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint(authenticationEntryPoint)
    .accessDeniedHandler(accessDeniedHandler))
```

## Formato de resposta de erro

Único DTO: `ErrorResponseDTO` (record, `@JsonInclude(NON_NULL)`).

```java
public record ErrorResponseDTO(
    int status,
    String error,
    Map<String, String> fieldErrors,
    Instant timestamp,
    String path
) {}
```

Exemplo de 404:
```json
{
  "status": 404,
  "error": "Todo not found with id: 42",
  "timestamp": "2026-06-04T17:30:00Z",
  "path": "/api/v1/todos/42"
}
```

Exemplo de 400 com validação:
```json
{
  "status": 400,
  "fieldErrors": {
    "title": "Title is required",
    "password": "Password must be at least 6 characters"
  },
  "timestamp": "2026-06-04T17:30:00Z",
  "path": "/api/v1/auth/register"
}
```

## Logging

Convenção dentro dos handlers:

- **`log.warn`** para 4xx esperados (cliente errou) — não polui o log com stack.
- **`log.error` com stack completo** para o catch-all `Exception.class` — você quer rastrear bugs.
- **Mensagem para o cliente é genérica em 500** (`"Internal server error. Please try again later."`) — nunca vaza stack, mensagem original ou detalhes internos.

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponseDTO> handleGeneric(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponseDTO.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Internal server error. Please try again later.", request.getRequestURI()));
}
```

## Anti-padrões a evitar

### ❌ `try/catch` no controller
```java
// NÃO
@GetMapping("/{id}")
public ResponseEntity<?> getById(@PathVariable Long id) {
    try {
        return ResponseEntity.ok(service.getById(id));
    } catch (TodoNotFoundException e) {
        return ResponseEntity.notFound().build();
    } catch (Exception e) {
        return ResponseEntity.internalServerError().build();
    }
}
```
**Problemas**: poluição, lógica HTTP espalhada, formato de erro inconsistente entre endpoints, fácil esquecer um `catch`.

### ❌ Vazar stack trace ou mensagem interna
Cliente nunca deve receber `NullPointerException at line 42 of ...`. O catch-all genérico cuida disso.

### ❌ Retornar `null`/`Optional.empty()` do service para sinalizar "não encontrado"
Force o caller a tratar via exceção — é mais difícil ignorar.

### ❌ Exceção genérica `RuntimeException("algo deu errado")`
Não dá pra mapear para HTTP específico, não dá pra tratar diferente no advice. Crie uma classe de domínio.

### ❌ Lançar exceção HTTP-específica no service (`ResponseStatusException`)
Acopla domínio ao framework web. O service não deveria saber que existe HTTP.

## Adicionando uma nova exceção (checklist)

1. **Domain**: criar classe em `domain/exception/` estendendo `RuntimeException` com construtor parametrizado e mensagem fixa.
2. **Service**: lançar onde a regra é violada. Sem `try/catch`.
3. **Controller**: nada a fazer — só não engolir.
4. **`GlobalExceptionHandler`**: adicionar `@ExceptionHandler` com status HTTP correto e log no nível apropriado (`warn` para 4xx, `error` para 5xx).
5. **Teste**: cobrir o caminho que dispara a exceção e o status HTTP retornado.

## Referências internas

Estrutura **package-by-feature** — cada bounded context tem o próprio advice:

- `shared/exception/GlobalExceptionHandler.java` — handlers de framework (validation, type mismatch, no resource, integrity, catch-all 500). `@Order(LOWEST_PRECEDENCE)`.
- `auth/interfaces/exception/AuthExceptionHandler.java` — `InvalidCredentialsException`, `UserAlreadyExistsException`. `@Order(HIGHEST_PRECEDENCE)`.
- `todo/interfaces/exception/TodoExceptionHandler.java` — `TodoNotFoundException`, `PastDueDateException`, etc. `@Order(HIGHEST_PRECEDENCE)`.
- `shared/web/dto/ErrorResponseDTO.java` — formato de resposta unificado.
- `auth/domain/exception/` — exceções do contexto de auth.
- `todo/domain/exception/` — exceções do contexto de todo.
- `auth/infrastructure/security/RestAuthenticationEntryPoint.java` — handler 401.
- `auth/infrastructure/security/RestAccessDeniedHandler.java` — handler 403.
- `auth/infrastructure/security/SecurityConfig.java` — wiring dos dois acima.

### Por que `@Order` nos advices

Com múltiplos `@RestControllerAdvice`, Spring itera pelos advices na ordem do `@Order` e usa o PRIMEIRO que tem handler aplicável. Se o `GlobalExceptionHandler` (com catch-all `Exception.class`) rodar antes dos feature handlers, ele vence sempre — qualquer `TodoNotFoundException` viraria 500. Por isso:
- Feature handlers: `@Order(Ordered.HIGHEST_PRECEDENCE)` — tentam primeiro.
- Global: `@Order(Ordered.LOWEST_PRECEDENCE)` — fallback final.
