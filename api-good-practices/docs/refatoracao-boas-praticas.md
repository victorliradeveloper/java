# Refatoração — Boas Práticas Aplicadas

Este documento descreve as violações de boas práticas identificadas no projeto e as correções realizadas.

---

## 1. Reorganização dos DTOs

**Problema:** Todos os DTOs estavam na raiz do pacote `dto`, sem distinção entre os que representam entradas (requests) e saídas (responses).

**Solução:** Criadas as subpastas `dto/request` e `dto/response`.

| Pacote | Classes |
|--------|---------|
| `dto.request` | `TodoRequestDTO`, `TodoFilterDTO`, `LoginRequestDTO`, `RegisterRequestDTO` |
| `dto.response` | `TodoResponseDTO`, `PagedResponseDTO`, `CursorPageResponseDTO`, `AuthResponseDTO`, `ErrorResponseDTO` |

Todos os imports nos controllers, services, filters e testes foram atualizados.

---

## 2. Camada de Mappers

**Problema:** `TodoService` e `AuthService` continham métodos privados responsáveis por converter entidades em DTOs e vice-versa (`toResponse`, `toPagedResponse`, construção manual de `User` e `Todo`). Isso viola o Single Responsibility Principle — o service deve orquestrar regras de negócio, não transformar dados.

**Solução:** Criada a camada `mapper/` com dois componentes:

- **`TodoMapper`** — converte `TodoRequestDTO → Todo`, `Todo → TodoResponseDTO`, `Page<Todo> → PagedResponseDTO`, lista para `CursorPageResponseDTO` e atualiza campos de um `Todo` existente via `updateEntity`.
- **`AuthMapper`** — converte `RegisterRequestDTO → User` e `User + token → AuthResponseDTO`.

Os services passaram a delegar toda transformação de dados para os mappers.

---

## 3. `JwtService.validateToken()` retornava `null`

**Problema:** O método retornava `null` em caso de token inválido, forçando verificações de nulo em quem chamava e escondendo o motivo da falha.

**Solução:** O retorno foi alterado para `Optional<DecodedJWT>`. Isso torna explícito que o resultado pode estar ausente e permite encadear operações com `ifPresent` e `map`.

```java
// antes
String email = jwtService.validateToken(token);
if (email != null) { ... }

// depois
jwtService.validateToken(token).ifPresent(decoded -> { ... });
```

---

## 4. `JwtAuthenticationFilter` batia no banco a cada requisição

**Problema:** Para cada requisição autenticada, o filtro fazia `userRepository.findByEmail(email)` — uma query ao banco desnecessária, pois o JWT já carrega `userId`, `email` e `name` nas suas claims.

**Solução:** O `UserRepository` foi removido do filtro. O principal é agora construído diretamente a partir das claims do `DecodedJWT`, sem tocar no banco.

```java
private User buildPrincipal(DecodedJWT decoded) {
    return User.builder()
            .id(decoded.getClaim("userId").asLong())
            .email(decoded.getSubject())
            .name(decoded.getClaim("name").asString())
            .build();
}
```

---

## 5. `show-sql=true` em produção

**Problema:** `spring.jpa.show-sql=true` estava no `application.properties` principal, fazendo com que todas as queries SQL fossem logadas em qualquer ambiente, incluindo produção. Isso expõe a estrutura interna do banco nos logs.

**Solução:** A propriedade foi removida do `application.properties` e adicionada apenas ao `application-dev.properties`.

---

## 6. `CacheConfig` com validação de tipo permissiva demais

**Problema:** `allowIfBaseType(Object.class)` permitia que qualquer classe Java fosse desserializada pelo Jackson no Redis, abrindo potencial para ataques de desserialização.

**Solução:** Restrito a `allowIfSubType("com.javanauta.todo_app.")`, aceitando apenas classes do próprio projeto.

---

## 7. `@Transactional` ausente nas operações de escrita

**Problema:** Os métodos `create`, `update`, `complete` e `delete` do `TodoService` não possuíam `@Transactional`. Em caso de falha após o `save` mas antes do retorno, não haveria rollback automático.

**Solução:**
- Operações de escrita anotadas com `@Transactional`.
- Operações de leitura (`findAll`, `getById`, `listWithCursor`) anotadas com `@Transactional(readOnly = true)`, o que melhora performance ao sinalizar ao banco que não haverá modificações.

---

## 8. `Todo` usava `GenerationType.AUTO`

**Problema:** `GenerationType.AUTO` no PostgreSQL utiliza uma sequência compartilhada (`hibernate_sequence`), o que pode gerar conflitos de ID quando há múltiplas entidades ou múltiplas instâncias da aplicação.

**Solução:** Alterado para `GenerationType.IDENTITY`, que usa a coluna `SERIAL`/`BIGSERIAL` do próprio PostgreSQL — comportamento correto e previsível.

---

## 9. `JwtService` anotado com `@Service`

**Problema:** `@Service` é semanticamente reservado para classes que contêm lógica de negócio. `JwtService` é um utilitário técnico de criptografia e geração de tokens.

**Solução:** Anotação alterada para `@Component`.

---

## 10. `UserRepository` sem `@Repository`

**Problema:** `TodoRepository` possuía `@Repository`, mas `UserRepository` não — inconsistência entre os dois repositórios do projeto.

**Solução:** Anotação `@Repository` adicionada ao `UserRepository`.

---

## 11. Import quebrado no `RateLimitingFilter`

**Problema:** Após a reorganização dos DTOs, o `RateLimitingFilter` ficou com o import antigo `com.javanauta.todo_app.dto.ErrorResponseDTO`, que não existia mais — erro de compilação.

**Solução:** Import corrigido para `com.javanauta.todo_app.dto.response.ErrorResponseDTO`.
