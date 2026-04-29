# TodoAppApplicationTests

**Arquivo:** `src/test/java/com/javanauta/todo_app/TodoAppApplicationTests.java`

---

## O código completo

```java
@SpringBootTest
class TodoAppApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

---

## O que este teste faz?

Apesar de parecer vazio, este teste faz algo importante: **verifica se a aplicação Spring consegue inicializar sem erros**.

Quando o JUnit executa `contextLoads()`, a anotação `@SpringBootTest` instrui o Spring a subir o contexto completo da aplicação — carregando todos os beans, configurações, conexões, etc. Se qualquer coisa estiver errada (um bean mal configurado, uma dependência faltando, uma propriedade inválida), a inicialização falha e o teste falha junto.

```
JUnit chama contextLoads()
        ↓
@SpringBootTest sobe o contexto Spring completo
        ↓
Todos os beans são criados e injetados
        ↓
Se tudo ok → teste PASSA (verde)
Se algo falhar → teste FALHA (vermelho)
```

---

## Anotação: `@SpringBootTest`

Esta é a anotação mais "pesada" de toda a suíte de testes. Ela:

1. Encontra a classe `@SpringBootApplication` do projeto
2. Sobe o contexto Spring completo (como se fosse iniciar o servidor de verdade)
3. Disponibiliza todos os beans para injeção com `@Autowired`

```java
@SpringBootTest
// equivale a dizer: "sobe TUDO antes de rodar este teste"
```

**Comparação com outras abordagens:**

| Anotação | O que sobe | Velocidade |
|---|---|---|
| `@SpringBootTest` | Contexto completo | Lento (segundos) |
| `@WebMvcTest` | Só a camada web | Médio |
| `@ExtendWith(MockitoExtension.class)` | Nada do Spring | Rápido |

---

## Por que o método está vazio?

O teste não precisa de nenhuma assertion porque **o ato de rodar sem lançar exceção já é o teste**. Se o contexto não carregar, o Spring lança uma exceção antes mesmo do método ser chamado — e o JUnit registra isso como falha.

---

## Quando este teste falha?

- Uma classe anotada com `@Service`, `@Repository` ou `@Component` tem um erro de injeção de dependência
- Uma propriedade obrigatória do `application.properties` está faltando
- Um bean duplicado ou conflitante foi definido
- A conexão com o banco de dados está mal configurada

---

## Resumo

```
@SpringBootTest    → sobe o contexto completo da aplicação
@Test contextLoads → valida que a aplicação inicializa sem erros
```

É o teste de "smoke test" da aplicação — se ele passa, pelo menos a aplicação consegue ligar.
