# JUnit

## Visão Geral

**JUnit** é o framework padrão de testes automatizados em Java. Permite escrever testes unitários — pequenos pedaços de código que validam, de forma reproduzível, se uma unidade da aplicação (uma classe, um método) se comporta como esperado.

No projeto a versão usada é a **JUnit 5** (também chamada de *Jupiter*), trazida automaticamente pelo `spring-boot-starter-test` no `pom.xml` de cada microserviço:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Esse starter já inclui JUnit 5, AssertJ, Mockito e utilitários de teste do Spring — não precisa declarar nada além disso.

---

## Por que escrever testes?

1. **Feedback rápido** — saber em segundos se uma mudança quebrou algo, sem precisar subir a aplicação inteira.
2. **Documentação executável** — um teste descreve o comportamento esperado de forma que não pode ficar desatualizada (se ficar, falha).
3. **Refatoração segura** — dá pra mexer no código com a confiança de que a suíte vai gritar se o comportamento mudou.
4. **Design melhor** — código difícil de testar geralmente é código com acoplamento ruim. O teste expõe o problema cedo.

---

## Anatomia de um teste

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TodoEventTest {

    @Test
    void deveCriarEventoComOccurredAtPreenchido() {
        TodoEvent event = TodoEvent.of("123", "minha tarefa", "CREATED");

        assertEquals("123", event.todoId());
        assertEquals("CREATED", event.action());
        assertNotNull(event.occurredAt());
    }
}
```

Três peças aparecem em todo teste:

- **`@Test`** — marca o método como executável pela engine do JUnit. Sem isso, o método é ignorado.
- **Arrange / Act / Assert** — padrão clássico: preparar o cenário, executar a ação, verificar o resultado. No exemplo acima os três passos estão na ordem.
- **Asserts** — `assertEquals`, `assertNotNull`, `assertTrue`, `assertThrows`. São o ponto onde o teste decide se passou ou falhou.

---

## Anotações principais

| Anotação | Para que serve |
|---|---|
| `@Test` | Marca o método como um teste |
| `@BeforeEach` | Roda antes de **cada** teste (setup) |
| `@AfterEach` | Roda depois de **cada** teste (cleanup) |
| `@BeforeAll` | Roda **uma vez** antes de todos os testes da classe |
| `@AfterAll` | Roda **uma vez** depois de todos os testes da classe |
| `@DisplayName("...")` | Nome amigável que aparece no relatório |
| `@Disabled` | Pula o teste (sem deletar) |
| `@ParameterizedTest` | Roda o mesmo teste com várias entradas |

---

## Asserts mais usados

```java
assertEquals(esperado, real);
assertNotEquals(naoEsperado, real);
assertTrue(condicao);
assertFalse(condicao);
assertNull(valor);
assertNotNull(valor);
assertThrows(IllegalArgumentException.class, () -> service.criar(null));
```

Quando o assert falha, o JUnit interrompe aquele método e marca o teste como vermelho — os outros testes da classe seguem rodando normalmente.

---

## Como rodar

Pelo Maven (em qualquer microserviço):

```powershell
.\mvnw test
```

Pra rodar uma classe específica:

```powershell
.\mvnw test -Dtest=TodoEventTest
```

Pra rodar um único método:

```powershell
.\mvnw test -Dtest=TodoEventTest#deveCriarEventoComOccurredAtPreenchido
```

Os testes ficam em `src/test/java`, espelhando o pacote do código de produção em `src/main/java`. O Maven os descobre automaticamente.

---

## Boas práticas

- **Um teste, uma asserção lógica** — se quebrar, fica óbvio o que falhou.
- **Nome descritivo** — `deveRetornar404QuandoTodoNaoExiste` vale mais que `testGet`.
- **Independentes entre si** — nenhum teste pode depender da ordem ou de estado deixado por outro.
- **Rápidos** — teste unitário deve rodar em milissegundos. Se precisa de banco, fila ou rede, é teste de integração (outro assunto, outras anotações: `@SpringBootTest`, `@DataJpaTest`, Testcontainers).
