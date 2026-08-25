# TodoServiceTest

**Arquivo:** `src/test/java/com/javanauta/todo_app/service/TodoServiceTest.java`

---

## O que são testes unitários?

Este arquivo contém **testes unitários** do `TodoService`. A ideia é testar o service de forma **isolada** — sem banco de dados, sem Spring, sem rede. Só a lógica pura da classe.

Para isso, o `TodoRepository` é substituído por um **mock** (um dublê que finge ser o repositório real).

```
Teste unitário — o que roda:

┌─────────────────────┐
│   TodoService       │  ← real, com a lógica de verdade
│   (classe testada)  │
└──────────┬──────────┘
           │ depende de
           ▼
┌─────────────────────┐
│   TodoRepository    │  ← MOCK (falso, controlado pelo teste)
│   (mock)            │     não acessa banco de dados
└─────────────────────┘
```

---

## Configuração da classe

```java
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;  // falso

    @InjectMocks
    private TodoService todoService;        // real

    private Todo todo;
    private TodoRequestDTO request;
```

### `@ExtendWith(MockitoExtension.class)`
Ativa o Mockito para esta classe de testes. É ele quem cria os mocks e os injeta automaticamente.

### `@Mock`
O `todoRepository` é um mock — um objeto falso criado pelo Mockito. Ele implementa a interface `TodoRepository`, mas não faz nada por padrão. Você controla exatamente o que ele retorna em cada teste.

### `@InjectMocks`
O `todoService` é uma instância **real** do `TodoService`. O Mockito detecta que o service precisa de um `TodoRepository` e injeta o mock criado com `@Mock`.

---

## `@BeforeEach` — preparando o cenário

```java
@BeforeEach
void setUp() {
    todo = Todo.builder()
            .id(1L)
            .titulo("Estudar Java")
            .descricao("Revisar streams e lambdas")
            .concluido(false)
            .dataCriacao(LocalDateTime.now())
            .dataLimite(LocalDateTime.now().plusDays(3))
            .build();

    request = new TodoRequestDTO("Estudar Java", "Revisar streams e lambdas", LocalDateTime.now().plusDays(3));
}
```

Este método roda **antes de cada `@Test`**. Ele recria `todo` e `request` do zero para garantir que um teste não contamina o outro.

---

## Os testes em detalhe

### Grupo 1 — `criar()`

#### `criar_deveRetornarTodoCriado`

```java
@Test
void criar_deveRetornarTodoCriado() {
    when(todoRepository.save(any(Todo.class))).thenReturn(todo);

    TodoResponseDTO response = todoService.criar(request);

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getTitulo()).isEqualTo("Estudar Java");
    assertThat(response.getDescricao()).isEqualTo("Revisar streams e lambdas");
    assertThat(response.isConcluido()).isFalse();
    verify(todoRepository, times(1)).save(any(Todo.class));
}
```

**O que verifica:**
- O service chama `repository.save()` exatamente uma vez
- O DTO retornado tem os dados corretos mapeados do `Todo`
- O campo `concluido` começa como `false`

**Linha por linha:**
```
when(todoRepository.save(any(Todo.class))).thenReturn(todo)
→ "quando save() for chamado com qualquer Todo, retorne o objeto 'todo'"

TodoResponseDTO response = todoService.criar(request)
→ chama o método real do service

assertThat(response.getId()).isEqualTo(1L)
→ verifica que o id foi mapeado corretamente

verify(todoRepository, times(1)).save(any(Todo.class))
→ confirma que save() foi chamado exatamente 1 vez
```

---

### Grupo 2 — `listarTodos()`

#### `listarTodos_deveRetornarListaComTodosOsItens`

```java
@Test
void listarTodos_deveRetornarListaComTodosOsItens() {
    Todo outro = Todo.builder().id(2L).titulo("Outro").concluido(true).dataCriacao(LocalDateTime.now()).build();
    when(todoRepository.findAll()).thenReturn(List.of(todo, outro));

    List<TodoResponseDTO> resultado = todoService.listarTodos();

    assertThat(resultado).hasSize(2);
    assertThat(resultado.get(0).getId()).isEqualTo(1L);
    assertThat(resultado.get(1).getId()).isEqualTo(2L);
}
```

O mock retorna uma lista com dois itens. O teste verifica que o service mapeou os dois corretamente.

#### `listarTodos_quandoVazio_deveRetornarListaVazia`

```java
@Test
void listarTodos_quandoVazio_deveRetornarListaVazia() {
    when(todoRepository.findAll()).thenReturn(List.of());

    List<TodoResponseDTO> resultado = todoService.listarTodos();

    assertThat(resultado).isEmpty();
}
```

Testa o **caso extremo**: quando não há nenhum item. O resultado deve ser uma lista vazia (não `null`, não exceção).

---

### Grupo 3 — `listarPorStatus()`

Dois testes verificam o filtro de status:

```java
// filtra só os concluídos
when(todoRepository.findByConcluido(true)).thenReturn(List.of(concluido));
assertThat(resultado.get(0).isConcluido()).isTrue();

// filtra só os pendentes
when(todoRepository.findByConcluido(false)).thenReturn(List.of(todo));
assertThat(resultado.get(0).isConcluido()).isFalse();
```

Cada teste usa um argumento diferente no mock para simular os dois filtros possíveis.

---

### Grupo 4 — `buscarPorId()`

#### Caminho feliz (id existe)

```java
@Test
void buscarPorId_quandoExiste_deveRetornarTodo() {
    when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

    TodoResponseDTO response = todoService.buscarPorId(1L);

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getTitulo()).isEqualTo("Estudar Java");
}
```

O mock retorna `Optional.of(todo)` — simula que o item foi encontrado no banco.

#### Caminho triste (id não existe)

```java
@Test
void buscarPorId_quandoNaoExiste_deveLancarTodoNotFoundException() {
    when(todoRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.buscarPorId(99L))
            .isInstanceOf(TodoNotFoundException.class)
            .hasMessageContaining("99");
}
```

O mock retorna `Optional.empty()` — simula que o item **não existe**. O teste verifica que o service lança a exceção certa com a mensagem correta.

```
assertThatThrownBy(() -> todoService.buscarPorId(99L))
→ "espero que esta chamada lance uma exceção"

.isInstanceOf(TodoNotFoundException.class)
→ "e que seja do tipo TodoNotFoundException"

.hasMessageContaining("99")
→ "e que a mensagem mencione o id 99"
```

---

### Grupo 5 — `atualizar()`

#### Quando existe

```java
@Test
void atualizar_quandoExiste_deveRetornarTodoAtualizado() {
    TodoRequestDTO novoRequest = new TodoRequestDTO("Novo título", "Nova descrição", null);
    when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
    when(todoRepository.save(any(Todo.class))).thenReturn(todo);

    TodoResponseDTO response = todoService.atualizar(1L, novoRequest);

    assertThat(response).isNotNull();
    verify(todoRepository).save(todo);
}
```

Nota que dois mocks são necessários aqui: `findById` (para encontrar o item) e `save` (para salvar as alterações).

#### Quando não existe

```java
@Test
void atualizar_quandoNaoExiste_deveLancarTodoNotFoundException() {
    when(todoRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.atualizar(99L, request))
            .isInstanceOf(TodoNotFoundException.class)
            .hasMessageContaining("99");

    verify(todoRepository, never()).save(any());
}
```

`verify(todoRepository, never()).save(any())` — verifica que `save()` **nunca** foi chamado, porque não faz sentido salvar algo que não existe.

---

### Grupo 6 — `concluir()`

```java
@Test
void concluir_quandoExiste_deveMarcarlComoConcluido() {
    when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
    when(todoRepository.save(todo)).thenAnswer(inv -> {
        Todo t = inv.getArgument(0);
        t.setConcluido(true);
        return t;
    });

    TodoResponseDTO response = todoService.concluir(1L);

    assertThat(response.isConcluido()).isTrue();
}
```

`thenAnswer` é um recurso avançado do Mockito. Em vez de retornar um valor fixo, ele executa uma função. Aqui simula o comportamento real do banco: modifica o objeto recebido e o retorna, permitindo verificar que `concluido` foi realmente marcado como `true`.

---

### Grupo 7 — `deletar()`

#### Quando existe

```java
@Test
void deletar_quandoExiste_deveDeletarSemErro() {
    when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

    todoService.deletar(1L);

    verify(todoRepository).deleteById(1L);
}
```

Como `deletar()` não retorna valor, a única verificação possível é confirmar via `verify` que `deleteById()` foi chamado.

#### Quando não existe

```java
@Test
void deletar_quandoNaoExiste_deveLancarTodoNotFoundException() {
    when(todoRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> todoService.deletar(99L))
            .isInstanceOf(TodoNotFoundException.class);

    verify(todoRepository, never()).deleteById(any());
}
```

Garante que `deleteById()` nunca é chamado se o item não existe.

---

## Padrão de nomenclatura dos testes

Os métodos seguem o padrão: `metodo_cenario_comportamentoEsperado`

```
criar_deveRetornarTodoCriado
│      │
│      └── comportamento esperado
└── método testado

buscarPorId_quandoNaoExiste_deveLancarTodoNotFoundException
│            │                │
│            │                └── comportamento esperado
│            └── cenário (condição)
└── método testado
```

Esse padrão torna os erros de teste autoexplicativos — o nome já descreve o que estava sendo testado quando falhou.

---

## Resumo das anotações e técnicas

| Recurso | Para que serve |
|---|---|
| `@ExtendWith(MockitoExtension.class)` | Ativa o Mockito no JUnit 5 |
| `@Mock` | Cria um objeto falso da classe/interface |
| `@InjectMocks` | Cria o objeto real e injeta os mocks |
| `@BeforeEach` | Roda antes de cada teste para preparar o cenário |
| `@Test` | Marca o método como um caso de teste |
| `when().thenReturn()` | Define o que o mock retorna |
| `when().thenAnswer()` | Define comportamento dinâmico no mock |
| `verify()` | Confirma que um método foi (ou não) chamado |
| `assertThat()` | Verifica o valor retornado |
| `assertThatThrownBy()` | Verifica que uma exceção foi lançada |
