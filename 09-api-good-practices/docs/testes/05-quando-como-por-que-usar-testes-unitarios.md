# Testes Unitários em Java + Spring — Quando, Como e Por Quê

Este documento é um guia prático para decidir **quando** escrever testes unitários, **por que** eles valem o esforço e **como** escrevê-los em projetos Java + Spring Boot — usando as mesmas ferramentas adotadas neste projeto (JUnit 5, Mockito, AssertJ).

> Para os fundamentos das anotações e da API de mocks, veja [01-conceitos-basicos.md](./01-conceitos-basicos.md).

---

## 1. O que é um teste unitário

Um **teste unitário** verifica uma **única unidade de lógica** (geralmente um método de uma classe) de forma **isolada** das suas dependências externas (banco, HTTP, fila, sistema de arquivos).

```
┌──────────────────────────────┐
│   Classe sob teste (real)    │
│   ex.: TodoService           │
└──────────────┬───────────────┘
               │ depende de
               ▼
┌──────────────────────────────┐
│   Dependências (mockadas)    │
│   ex.: TodoRepository        │
└──────────────────────────────┘
```

Se o teste sobe o contexto do Spring, acessa banco real, abre porta HTTP ou consome uma fila, ele já **não é mais unitário** — passa a ser **integração**, **slice** ou **e2e**.

---

## 2. Por que usar

| Motivo | O que ganha na prática |
|---|---|
| **Feedback rápido** | Roda em milissegundos. Você muda código e sabe na hora se quebrou |
| **Documentação viva** | Cada `@Test` mostra como a classe deve ser usada e o que ela promete |
| **Refatoração segura** | Permite alterar a implementação sem medo, porque o comportamento está blindado |
| **Design melhor** | Código difícil de testar costuma ser código mal acoplado — testar pressiona o design |
| **Bugs caros caem cedo** | É exponencialmente mais barato corrigir um bug detectado no IDE do que em produção |
| **Confiança em CI** | PR só entra se o build verde — reduz regressão sem revisão manual extra |

> Regra prática: **se você só descobre o bug rodando a aplicação inteira, perdeu tempo**. O teste unitário existe para encurtar esse loop.

---

## 3. Quando usar

### ✅ Use teste unitário quando

- A classe tem **lógica de negócio** (regras, validações, cálculos, transformações).
- Existem **vários caminhos** (if/else, try/catch, switch) que precisam ser cobertos.
- Você quer cobrir **casos de exceção** (entrada inválida, recurso inexistente, conflito).
- A classe é **estável o suficiente** para o teste não virar fardo de manutenção.
- O código depende de **colaboradores que podem ser mockados** (interfaces, ports).

### ⚠️ Evite (ou prefira outro tipo)

| Cenário | Tipo de teste recomendado |
|---|---|
| Validar mapeamento JPA, queries, migrations | `@DataJpaTest` (slice de integração) |
| Validar deserialização JSON, validação `@Valid`, status HTTP | `@WebMvcTest` (slice de web) |
| Validar fluxo completo (controller → service → banco) | `@SpringBootTest` (integração) |
| Validar bibliotecas externas (Stripe, RabbitMQ, Redis) | Integração com Testcontainers |
| Getter/setter, DTO sem lógica, constantes | **Não teste** — só polui a suíte |

### Regra de bolso

> Se você precisa **`@Autowired`** ou subir o Spring para o teste funcionar, **não é unitário**. Use a anotação de slice correta.

---

## 4. Como usar

### 4.1 Estrutura padrão: AAA (Arrange-Act-Assert)

Todo teste deve ter três blocos claros:

```java
@Test
void deveRetornarTodoQuandoIdExiste() {
    // Arrange — monta o cenário
    Todo todo = Todo.builder().id(1L).titulo("Estudar").build();
    when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));

    // Act — executa a unidade testada
    TodoResponseDTO response = todoService.buscarPorId(1L);

    // Assert — verifica o resultado
    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getTitulo()).isEqualTo("Estudar");
}
```

### 4.2 Nomeie pelo comportamento, não pelo método

| ❌ Ruim | ✅ Bom |
|---|---|
| `testBuscar()` | `deveRetornarTodoQuandoIdExiste()` |
| `testBuscar2()` | `deveLancarExcecaoQuandoIdNaoExiste()` |
| `criarTeste()` | `deveSalvarTodoComStatusPendentePorPadrao()` |

O nome do método é a **especificação** do que a classe promete. Leia a lista de testes e você deve entender o contrato da classe.

### 4.3 Um teste = um comportamento

Não acumule cinco asserções de coisas diferentes no mesmo `@Test`. Se algo quebrar, você quer saber **exatamente** o quê.

```java
// ❌ Ruim — testa criação E listagem E exclusão num teste só
@Test
void testTodo() { ... }

// ✅ Bom — três testes independentes
@Test void deveCriarTodo()    { ... }
@Test void deveListarTodos()  { ... }
@Test void deveExcluirTodo()  { ... }
```

### 4.4 Isole as dependências com mocks

Em Spring, as classes de serviço quase sempre dependem de **repositórios**, **clientes HTTP**, **publishers de eventos**. Mocke todas:

```java
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock                                  // dependência falsa
    private TodoRepository todoRepository;

    @InjectMocks                           // classe real recebe os mocks
    private TodoService todoService;
}
```

> `@Mock` para POJOs/componentes. `@MockBean` apenas quando o **Spring** precisa do mock no contexto (ex.: `@WebMvcTest`).

### 4.5 Cubra os caminhos, não a métrica

Cobertura alta com testes ruins é pior do que cobertura baixa com testes bons. Garanta que cada teste cobre:

1. **Caminho feliz** — entrada válida → resultado esperado.
2. **Caminho de erro** — entrada inválida → exceção correta.
3. **Casos de borda** — null, vazio, limite numérico, coleção com 1 elemento.

### 4.6 Use AssertJ, não `assertEquals`

```java
// ❌ Antigo (JUnit 4 style)
assertEquals(expected, actual);

// ✅ AssertJ — encadeado, mensagem de erro muito mais clara
assertThat(actual)
    .isNotNull()
    .isEqualTo(expected)
    .extracting(TodoResponseDTO::getTitulo)
    .isEqualTo("Estudar");
```

### 4.7 Verifique interações quando elas fazem parte do contrato

```java
todoService.excluir(1L);

verify(todoRepository, times(1)).deleteById(1L);
verify(eventPublisher, never()).publish(any()); // garante que não emitiu evento
```

> Cuidado: não verifique tudo. Verifique só o que é **comportamento observável** — senão o teste vira espelho da implementação e quebra a cada refatoração.

---

## 5. Onde o teste unitário se encaixa: pirâmide de testes

```
                ┌─────────────────┐
                │   E2E / UI      │   poucos, lentos, caros
                └─────────────────┘
            ┌─────────────────────────┐
            │     Integração          │   alguns, médios
            │  (@SpringBootTest,      │
            │   Testcontainers)       │
            └─────────────────────────┘
        ┌─────────────────────────────────┐
        │       Slices (@WebMvcTest,      │   bastante, rápidos
        │        @DataJpaTest)            │
        └─────────────────────────────────┘
    ┌─────────────────────────────────────────┐
    │            Testes Unitários              │   muitos, rapidíssimos
    │      (JUnit 5 + Mockito + AssertJ)       │
    └─────────────────────────────────────────┘
```

A base deve ser **larga**: a maior parte da suíte são testes unitários. Conforme sobe, ficam mais lentos, mais frágeis e mais caros de manter.

---

## 6. Boas práticas (checklist)

- [ ] Cada teste tem um **único motivo para falhar**.
- [ ] Nomes descrevem **comportamento**, não método.
- [ ] AAA é visível no corpo do teste (comentários ou linhas em branco).
- [ ] Dependências externas estão **mockadas** — sem banco, sem HTTP.
- [ ] Sem `Thread.sleep()`, sem dependência de ordem entre testes.
- [ ] `@BeforeEach` monta dados comuns; cada teste começa do zero.
- [ ] Testes rodam em **paralelo** sem quebrar (sem estado estático compartilhado).
- [ ] Cobertos: caminho feliz + erros + borda.
- [ ] Tempo total da suíte unitária mantém-se em segundos.

---

## 7. Erros comuns a evitar

| Antipadrão | Problema | Correção |
|---|---|---|
| Subir `@SpringBootTest` para testar um service simples | Lento, frágil, não é unitário | Use `@ExtendWith(MockitoExtension.class)` |
| Mockar a própria classe sob teste | Teste não testa nada real | Mocke só **dependências** |
| `verify` em tudo | Teste quebra a cada refatoração | Verifique apenas o que é contrato |
| Reusar variáveis entre testes via campo mutável sem `@BeforeEach` | Acoplamento, ordem importa | Reset no `@BeforeEach` |
| Asserts genéricos (`assertNotNull(x)`) | Não pega regressões | Assertar valores concretos |
| Testar getters/setters/DTOs | Ruído, zero valor | Não teste |
| Lógica condicional dentro do teste (`if`, `for`) | Teste pode esconder o próprio bug | Linearize ou use `@ParameterizedTest` |

---

## 8. Exemplos deste projeto

| Arquivo | O que cobre | Tipo |
|---|---|---|
| [`TodoAppApplicationTests`](./02-TodoAppApplicationTests.md) | Smoke test: contexto Spring sobe | Integração mínima |
| [`TodoServiceTest`](./03-TodoServiceTest.md) | Regras de negócio do service com repositório mockado | **Unitário** |
| [`TodoControllerTest`](./04-TodoControllerTest.md) | Camada HTTP com MockMvc e service mockado | Slice de web |

O `TodoServiceTest` é o exemplo canônico de teste unitário no projeto: não sobe Spring, mocka o repositório, e cobre criação, busca, atualização, exclusão e cenários de exceção.

---

## 9. TL;DR

- **Por quê:** feedback rápido, refatoração segura, design melhor, regressão controlada.
- **Quando:** sempre que houver lógica em uma classe com dependências mockáveis.
- **Quando não:** mapeamento JPA, camada web, integrações externas — use slices ou integração.
- **Como:** JUnit 5 + Mockito + AssertJ, padrão AAA, um comportamento por teste, nome descritivo, dependências mockadas.
