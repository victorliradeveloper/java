# Conceitos Básicos de Testes em Java

Este documento explica os fundamentos que aparecem em todos os testes deste projeto antes de entrar nos detalhes de cada arquivo.

---

## Por que testar?

Um teste automatizado executa um pedaço do código e verifica se o resultado é o esperado. Se alguém mudar o código e quebrar algo, o teste falha e avisa imediatamente — sem precisar subir o servidor e clicar manualmente.

---

## As três bibliotecas usadas aqui

| Biblioteca | Para que serve |
|---|---|
| **JUnit 5** | Estrutura base: define o que é um teste, como rodá-lo, ciclo de vida |
| **Mockito** | Cria "dublês" de objetos reais (mocks) para isolar a classe testada |
| **AssertJ** | Escreve as verificações (assertions) de forma legível em português técnico |

---

## Anatomia de um teste: o padrão AAA

Todo bom teste segue três etapas, chamadas de **Arrange → Act → Assert**:

```
Arrange  →  monta o cenário (dados, mocks)
Act      →  chama o método que está sendo testado
Assert   →  verifica se o resultado é o esperado
```

Exemplo real do projeto:

```java
@Test
void criar_deveRetornarTodoCriado() {
    // Arrange — diz ao mock o que retornar quando save() for chamado
    when(todoRepository.save(any(Todo.class))).thenReturn(todo);

    // Act — chama o método real do service
    TodoResponseDTO response = todoService.criar(request);

    // Assert — verifica os valores retornados
    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getTitulo()).isEqualTo("Estudar Java");
}
```

---

## Anotações do JUnit 5

### `@Test`
Marca um método como caso de teste. O JUnit vai executá-lo automaticamente.

```java
@Test
void meuTeste() {
    // tudo aqui é executado como um teste
}
```

> Regra: o método deve ser `void`, sem parâmetros (na maioria dos casos), e com nome descritivo.

---

### `@BeforeEach`
Executa **antes de cada teste** da classe. Usado para montar dados comuns que todos os testes precisam, evitando repetição.

```java
@BeforeEach
void setUp() {
    todo = Todo.builder().id(1L).titulo("Estudar Java").build();
    // este objeto "todo" fica disponível em todos os @Test abaixo
}
```

```
Antes de cada @Test:
  setUp() ← @BeforeEach
  
@Test 1 → setUp() roda → teste roda
@Test 2 → setUp() roda de novo → teste roda
@Test 3 → setUp() roda de novo → teste roda
```

> Isso garante que cada teste começa com um estado limpo, sem influência dos outros.

---

### `@ExtendWith(MockitoExtension.class)`
Ativa o Mockito no JUnit 5. Sem isso, as anotações `@Mock` e `@InjectMocks` não funcionam.

```java
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {
    // agora o Mockito gerencia os mocks desta classe
}
```

---

## Anotações do Mockito

### `@Mock`
Cria um objeto falso (mock) de uma classe ou interface. Ele **não executa código real** — você diz o que ele deve retornar.

```java
@Mock
private TodoRepository todoRepository;
// todoRepository agora é um mock — não acessa o banco de dados real
```

---

### `@InjectMocks`
Cria uma instância **real** da classe e injeta automaticamente todos os `@Mock` dentro dela.

```java
@InjectMocks
private TodoService todoService;
// todoService é real, mas usa o todoRepository mock internamente
```

```
@InjectMocks         @Mock
TodoService    ←───  TodoRepository (falso)
  (real)
```

---

### `@MockBean`
Versão Spring do `@Mock`. Usado nos testes de controller (`@WebMvcTest`) porque o Spring precisa gerenciar o bean no contexto da aplicação.

```java
@MockBean
private TodoService todoService;
// o Spring registra este mock como se fosse o bean real no contexto
```

---

## Métodos principais do Mockito

### `when(...).thenReturn(...)`
Define o comportamento do mock: *"quando este método for chamado, retorne isso"*.

```java
when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
//   ^ mock           ^ chamada    ^ o que retornar
```

### `when(...).thenThrow(...)`
Faz o mock lançar uma exceção.

```java
when(todoRepository.findById(99L)).thenThrow(new TodoNotFoundException(99L));
```

### `verify(...)`
Verifica se um método do mock foi chamado (e quantas vezes).

```java
verify(todoRepository, times(1)).save(any(Todo.class)); // chamado exatamente 1 vez
verify(todoRepository, never()).deleteById(any());      // nunca chamado
```

### `any()`
Corresponde a qualquer argumento do tipo especificado. Útil quando o valor exato não importa.

```java
when(todoRepository.save(any(Todo.class))).thenReturn(todo);
//                       ^ qualquer objeto Todo serve
```

### `eq(...)`
Corresponde a um valor exato. Usado quando o argumento precisa ser específico.

```java
when(todoService.atualizar(eq(1L), any(TodoRequestDTO.class))).thenReturn(response);
//                         ^ exatamente o id 1
```

---

## AssertJ — como fazer as verificações

O AssertJ usa uma sintaxe encadeada que lê quase como inglês:

```java
assertThat(valor)          // começa sempre aqui
    .isEqualTo(esperado)   // igualdade
    .isNotNull()           // não é nulo
    .isTrue() / .isFalse() // booleanos
    .hasSize(2)            // tamanho de coleções
    .isEmpty()             // coleção vazia
    .isInstanceOf(Tipo.class) // é instância de
    .hasMessageContaining("texto") // mensagem contém texto
```

Para verificar exceções:

```java
assertThatThrownBy(() -> todoService.buscarPorId(99L))
    .isInstanceOf(TodoNotFoundException.class)
    .hasMessageContaining("99");
// verifica que a chamada lança a exceção certa com a mensagem certa
```

---

## Resumo visual

```
Classe de teste
│
├── @ExtendWith(MockitoExtension.class)  → liga o Mockito
│
├── @Mock TodoRepository                 → objeto falso (sem banco)
├── @InjectMocks TodoService             → objeto real com mocks injetados
│
├── @BeforeEach setUp()                  → prepara dados antes de cada teste
│
├── @Test teste1()    ─┐
├── @Test teste2()     ├── cada um roda independente
└── @Test teste3()    ─┘
```

---

## Próximos passos

- [TodoAppApplicationTests](./02-TodoAppApplicationTests.md) — o teste mais simples do projeto
- [TodoServiceTest](./03-TodoServiceTest.md) — testes unitários da camada de serviço
- [TodoControllerTest](./04-TodoControllerTest.md) — testes da camada HTTP com MockMvc
