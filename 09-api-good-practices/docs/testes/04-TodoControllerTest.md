# TodoControllerTest

**Arquivo:** `src/test/java/com/javanauta/todo_app/controller/TodoControllerTest.java`

---

## O que são testes de controller?

Os testes de controller verificam a **camada HTTP** da aplicação: se as rotas estão corretas, se os status codes são os certos, se o JSON retornado tem os campos esperados, e se as validações de entrada funcionam.

Eles **não testam a lógica de negócio** (isso é responsabilidade do `TodoServiceTest`). O service é substituído por um mock.

```
Requisição HTTP simulada
        ↓
┌─────────────────────┐
│   TodoController    │  ← real, processa a requisição
│   (classe testada)  │
└──────────┬──────────┘
           │ chama
           ▼
┌─────────────────────┐
│   TodoService       │  ← MOCK (retorna dados controlados)
│   (mock)            │
└─────────────────────┘
        ↓
Resposta HTTP verificada (status, JSON)
```

---

## Configuração da classe

```java
@WebMvcTest(TodoController.class)
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TodoService todoService;
```

### `@WebMvcTest(TodoController.class)`

Sobe **apenas a camada web** do Spring, sem banco de dados, sem service real. É mais rápido que `@SpringBootTest` e focado em testar controllers.

O argumento `TodoController.class` diz: "quero testar especificamente este controller".

```
@SpringBootTest  →  sobe TUDO (pesado)
@WebMvcTest      →  sobe só a camada web (leve e focado)
```

### `@Autowired MockMvc`

`MockMvc` é o objeto central dos testes de controller. Ele simula requisições HTTP sem precisar subir um servidor real na porta 8080.

```java
mockMvc.perform(get("/todo"))      // simula GET /todo
       .andExpect(status().isOk()) // verifica resposta
```

### `@Autowired ObjectMapper`

Converte objetos Java em JSON (serialização) e JSON em objetos Java (desserialização). Usado para transformar o `request` em JSON no corpo da requisição.

```java
objectMapper.writeValueAsString(request)
// → '{"titulo":"Estudar Java","descricao":"Revisar streams",...}'
```

### `@MockBean`

Versão do `@Mock` para o contexto Spring. Registra o `TodoService` como um mock gerenciado pelo Spring, para que possa ser injetado no controller automaticamente.

> **Diferença de `@Mock`:** `@Mock` é Mockito puro (sem Spring). `@MockBean` registra o mock no contexto Spring, necessário quando o Spring faz a injeção de dependência.

---

## `@BeforeEach` — preparando o cenário

```java
@BeforeEach
void setUp() {
    LocalDateTime agora = LocalDateTime.now();

    response = TodoResponseDTO.builder()
            .id(1L)
            .titulo("Estudar Java")
            .descricao("Revisar streams")
            .concluido(false)
            .dataCriacao(agora)
            .dataLimite(agora.plusDays(3))
            .build();

    request = new TodoRequestDTO("Estudar Java", "Revisar streams", agora.plusDays(3));
}
```

Prepara um `response` (o que o mock do service vai retornar) e um `request` (o que vai no corpo da requisição HTTP), reutilizados por vários testes.

---

## Anatomia de um teste MockMvc

```java
mockMvc.perform(                              // 1. executa uma requisição
    post("/todo")                             //    método HTTP + rota
        .contentType(MediaType.APPLICATION_JSON)  // cabeçalho Content-Type
        .content(objectMapper.writeValueAsString(request))  // corpo JSON
)
.andExpect(status().isCreated())              // 2. verifica o status HTTP
.andExpect(jsonPath("$.id").value(1L))        // 3. verifica o JSON retornado
.andExpect(jsonPath("$.titulo").value("Estudar Java"));
```

### Como funciona `jsonPath`?

`jsonPath` usa a linguagem JSONPath para navegar no JSON retornado:

```
JSON retornado:
{
  "id": 1,
  "titulo": "Estudar Java",
  "concluido": false
}

jsonPath("$.id")       → acessa o campo "id" na raiz ($)
jsonPath("$.titulo")   → acessa o campo "titulo"
jsonPath("$.length()") → quantidade de itens (em arrays)
jsonPath("$[0].titulo") → primeiro item de um array, campo "titulo"
```

---

## Os testes em detalhe

### POST `/todo` — criar

#### Criação com dados válidos → 201 Created

```java
@Test
void criar_comDadosValidos_deveRetornar201() throws Exception {
    when(todoService.criar(any(TodoRequestDTO.class))).thenReturn(response);

    mockMvc.perform(post("/todo")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.titulo").value("Estudar Java"))
            .andExpect(jsonPath("$.concluido").value(false));
}
```

O mock retorna `response` quando `criar()` for chamado. O teste verifica que o controller:
1. Retornou status `201 Created`
2. Colocou os dados corretos no JSON da resposta

#### Criação sem título → 400 Bad Request

```java
@Test
void criar_semTitulo_deveRetornar400() throws Exception {
    TodoRequestDTO requestInvalido = new TodoRequestDTO("", "descrição", null);

    mockMvc.perform(post("/todo")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestInvalido)))
            .andExpect(status().isBadRequest());

    verify(todoService, never()).criar(any());
}
```

Testa a **validação Bean Validation** (`@NotBlank` no DTO). O Spring rejeita a requisição antes de chamar o service, por isso `verify(todoService, never()).criar(any())` — o service não deve ser chamado.

---

### GET `/todo` — listar

#### Sem filtro → todos os itens

```java
@Test
void listar_semFiltro_deveRetornarTodosOsItens() throws Exception {
    when(todoService.listarTodos()).thenReturn(List.of(response));

    mockMvc.perform(get("/todo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].titulo").value("Estudar Java"));
}
```

`jsonPath("$.length()").value(1)` — verifica que o array JSON tem exatamente 1 elemento.

#### Com filtro `?concluido=true`

```java
@Test
void listar_comFiltroConcluido_deveRetornarItensFiltrados() throws Exception {
    when(todoService.listarPorStatus(true)).thenReturn(List.of(concluido));

    mockMvc.perform(get("/todo").param("concluido", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].concluido").value(true));
}
```

`.param("concluido", "true")` adiciona o query parameter à URL: `GET /todo?concluido=true`.

---

### GET `/todo/{id}` — buscar por ID

#### ID existe → 200 OK

```java
@Test
void buscarPorId_quandoExiste_deveRetornar200() throws Exception {
    when(todoService.buscarPorId(1L)).thenReturn(response);

    mockMvc.perform(get("/todo/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L));
}
```

#### ID não existe → 404 Not Found

```java
@Test
void buscarPorId_quandoNaoExiste_deveRetornar404() throws Exception {
    when(todoService.buscarPorId(99L)).thenThrow(new TodoNotFoundException(99L));

    mockMvc.perform(get("/todo/99"))
            .andExpect(status().isNotFound());
}
```

O mock lança `TodoNotFoundException`. O `GlobalExceptionHandler` captura essa exceção e retorna 404. O teste verifica que esse mapeamento funciona corretamente.

---

### PUT `/todo/{id}` — atualizar

```java
@Test
void atualizar_comDadosValidos_deveRetornar200() throws Exception {
    when(todoService.atualizar(eq(1L), any(TodoRequestDTO.class))).thenReturn(response);

    mockMvc.perform(put("/todo/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titulo").value("Estudar Java"));
}
```

`eq(1L)` — o primeiro argumento do mock deve ser exatamente o Long `1`. Necessário porque o Mockito exige consistência: se um argumento usa um matcher (`any()`), todos os outros também devem usar matchers.

---

### PATCH `/todo/{id}/concluir`

```java
@Test
void concluir_quandoExiste_deveRetornar200() throws Exception {
    TodoResponseDTO concluido = TodoResponseDTO.builder()
            .id(1L).titulo("Estudar Java").concluido(true).dataCriacao(LocalDateTime.now()).build();
    when(todoService.concluir(1L)).thenReturn(concluido);

    mockMvc.perform(patch("/todo/1/concluir"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.concluido").value(true));
}
```

`patch()` simula uma requisição HTTP PATCH. Verifica que o campo `concluido` é `true` na resposta.

---

### DELETE `/todo/{id}`

#### Quando existe → 204 No Content

```java
@Test
void deletar_quandoExiste_deveRetornar204() throws Exception {
    doNothing().when(todoService).deletar(1L);

    mockMvc.perform(delete("/todo/1"))
            .andExpect(status().isNoContent());
}
```

`doNothing().when(todoService).deletar(1L)` — para métodos `void`, usa-se `doNothing()` em vez de `when().thenReturn()`. Aqui é o padrão explícito, mas `doNothing()` já é o comportamento default para métodos void em mocks.

#### Quando não existe → 404 Not Found

```java
@Test
void deletar_quandoNaoExiste_deveRetornar404() throws Exception {
    doThrow(new TodoNotFoundException(99L)).when(todoService).deletar(99L);

    mockMvc.perform(delete("/todo/99"))
            .andExpect(status().isNotFound());
}
```

`doThrow()` é necessário para métodos `void` que precisam lançar exceção — `when().thenThrow()` não funciona com `void`.

---

## Tabela de status HTTP verificados

| Método | Cenário | Status |
|---|---|---|
| POST /todo | Dados válidos | 201 Created |
| POST /todo | Título vazio | 400 Bad Request |
| GET /todo | Qualquer | 200 OK |
| GET /todo/{id} | ID existe | 200 OK |
| GET /todo/{id} | ID não existe | 404 Not Found |
| PUT /todo/{id} | ID existe | 200 OK |
| PUT /todo/{id} | ID não existe | 404 Not Found |
| PATCH /todo/{id}/concluir | ID existe | 200 OK |
| PATCH /todo/{id}/concluir | ID não existe | 404 Not Found |
| DELETE /todo/{id} | ID existe | 204 No Content |
| DELETE /todo/{id} | ID não existe | 404 Not Found |

---

## Resumo das anotações e técnicas

| Recurso | Para que serve |
|---|---|
| `@WebMvcTest` | Sobe só a camada web do Spring |
| `@Autowired MockMvc` | Ferramenta para simular requisições HTTP |
| `@Autowired ObjectMapper` | Serializa/desserializa JSON |
| `@MockBean` | Mock registrado no contexto Spring |
| `@BeforeEach` | Prepara dados antes de cada teste |
| `mockMvc.perform()` | Executa uma requisição HTTP simulada |
| `.param()` | Adiciona query parameters à URL |
| `status().isOk()` | Verifica status 200 |
| `status().isCreated()` | Verifica status 201 |
| `status().isNoContent()` | Verifica status 204 |
| `status().isBadRequest()` | Verifica status 400 |
| `status().isNotFound()` | Verifica status 404 |
| `jsonPath("$.campo")` | Navega e verifica campos no JSON |
| `doNothing().when()` | Define comportamento void no mock |
| `doThrow().when()` | Faz método void lançar exceção |
