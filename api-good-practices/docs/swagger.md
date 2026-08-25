# Documentação com Swagger / OpenAPI

Este documento descreve como o Swagger é usado no projeto, o que documentar e em quais arquivos colocar as anotações.

## TL;DR

- Biblioteca: **springdoc-openapi** (não é o `springfox`, que está deprecado).
- Toda anotação Swagger fica na **interface `*Api.java`** em `interfaces/rest/docs/` — o controller `implements` essa interface e fica limpo.
- DTOs descrevem seus campos com `@Schema`.
- Configuração global (info + security scheme) em um `@Configuration` único.

## Como funciona

O springdoc lê em runtime:

1. **Anotações** (`@Operation`, `@ApiResponse`, `@Schema`, `@Parameter`, `@Tag`, `@SecurityRequirement`).
2. **Assinatura do método** Spring MVC — tipo do `@RequestBody`, `@PathVariable`, `@RequestParam`, return type.
3. **Bean Validation** (`@NotBlank`, `@Size`, `@Email`, `@Min`, ...) — vira `constraints` no schema do OpenAPI.

A partir disso ele gera:

- `GET /v3/api-docs` → JSON OpenAPI 3.
- `GET /swagger-ui.html` → UI interativa.

Nada precisa ser escrito à mão fora das anotações.

## O que documentar

### Por endpoint (na interface `*Api`)

| Anotação | Para quê |
|---|---|
| `@Tag(name, description)` | Agrupa endpoints na UI (uma por interface). |
| `@Operation(summary, description)` | O que o endpoint faz. |
| `@ApiResponse(responseCode, description)` | **Cada status que pode sair**: 200/201/204 de sucesso e todos os erros possíveis (400, 401, 403, 404, 409, 422, 429, 500). |
| `@Parameter(description)` | Path/query params quando o nome não é auto-explicativo. |
| `@SecurityRequirement(name = "bearerAuth")` | Marca endpoints que exigem token. |

Erros que se repetem em **toda** a interface (401, 403, 429, 500) podem ficar como `@ApiResponse` na própria interface, fora dos métodos — herdam para todos os endpoints.

### Por DTO

| Anotação | Para quê |
|---|---|
| `@Schema(description, example)` | Descrição e exemplo de cada campo. |
| `@Schema(requiredMode = REQUIRED)` | Marca como obrigatório (ou deixa o `@NotNull`/`@NotBlank` resolver). |
| `@Schema(accessMode = READ_ONLY)` | Campos que só aparecem em response (ex: `id`, `createdAt`). |

### Global (uma vez só)

- `@Bean OpenAPI` com `Info` (título, versão, contato, license) e `SecurityScheme` (Bearer JWT).
- `application.yml` → `springdoc.*` para customizar paths, packages-to-scan, sort, etc.

## Em quais arquivos

```
interfaces/
├── rest/
│   ├── TodoController.java          ← implements TodoApi, SEM anotação Swagger
│   └── docs/
│       └── TodoApi.java             ← TODA anotação Swagger fica aqui
├── dto/
│   ├── request/TodoRequestDTO.java  ← @Schema nos campos
│   └── response/TodoResponseDTO.java
shared/
└── config/
    └── OpenApiConfig.java           ← @Bean OpenAPI global
```

### Por que separar controller e interface `*Api`?

- Controller foca em orquestração (delegate para service, montar `ResponseEntity`).
- Interface concentra **contrato HTTP + documentação** no mesmo lugar.
- Reduz ruído visual: um controller cheio de `@ApiResponse` fica ilegível.
- Se outro cliente (ex: Feign) precisar do contrato, reaproveita a interface.

## Exemplo do projeto

Ver `TodoApi.java` em `todo/interfaces/rest/docs/`:

```java
@Tag(name = "Todos", description = "Todo management")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
@ApiResponse(responseCode = "500", description = "Unexpected internal server error",
        content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
public interface TodoApi {

    @Operation(summary = "Create a new todo")
    @ApiResponse(responseCode = "201", description = "Todo created successfully")
    @ApiResponse(responseCode = "409", description = "Duplicate or limit exceeded",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    @ApiResponse(responseCode = "422", description = "Due date is in the past",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    ResponseEntity<TodoResponseDTO> create(@RequestBody @Valid TodoRequestDTO request);
}
```

E o controller:

```java
@RestController
@RequestMapping("/api/todos")
public class TodoController implements TodoApi {
    // só implementação — zero anotação Swagger
}
```

## Boas práticas

- **Liste todos os status reais** que o endpoint pode retornar. Se o `@RestControllerAdvice` mapeia uma exception para 409, documente 409 — Swagger não infere isso sozinho.
- **Reutilize `ErrorResponseDTO`** como `content schema` dos erros — o cliente vê sempre o mesmo formato.
- **Não duplique** o que o Bean Validation já diz. `@NotBlank` + `@Size(max=200)` já vira constraint no schema; não precisa repetir em `@Schema(description = "...")`.
- **Não documente o óbvio**. `@Parameter(description = "Todo ID")` em cima de `@PathVariable Long id` é ruído — só descreva quando o significado não está no nome.
- **Mantenha sumários curtos** (`summary`). Detalhes longos vão em `description`.
