# Package e Namespace em Java

Este documento explica o que é um package, por que ele existe, como ele se relaciona com o conceito de namespace e como o Spring se apoia nele. Os exemplos usam a estrutura real deste projeto.

---

## O problema que o namespace resolve

Imagine um projeto que usa duas bibliotecas diferentes. A biblioteca A tem uma classe chamada `Cliente`. A biblioteca B também tem uma classe chamada `Cliente`. Você precisa das duas no mesmo código.

Sem um mecanismo de separação, isso é um impasse: o compilador não teria como saber de qual `Cliente` você está falando.

**Namespace** é o nome genérico da solução para esse problema — um espaço de nomes que qualifica cada identificador, de forma que nomes iguais em espaços diferentes não colidam. É um conceito presente em várias linguagens: C++ e C# têm `namespace`, Python tem módulos, PHP tem `namespace`.

**Em Java, o namespace se chama `package`.**

---

## Package é Java, não Spring

Vale deixar isso claro logo de início, porque é uma confusão comum:

| Conceito | De onde vem |
|---|---|
| `package`, `import`, visibilidade | **Java** — palavra-chave da linguagem desde o Java 1.0 (1996), especificada no JLS capítulo 7 |
| Component scan a partir do package raiz | **Spring** — convenção construída em cima do package |

Tudo o que este documento explica até a seção do Spring funcionaria idêntico num projeto Java puro, sem uma única linha de framework.

---

## O nome real de uma classe

Este é o conceito central, e o que faz todo o resto encaixar.

O nome da sua classe **não é** `TodoController`. O nome real, completo, é:

```
com.javanauta.todo_app.controller.TodoController
```

Isso se chama **FQN** — *Fully Qualified Name*, ou nome plenamente qualificado. `TodoController` é apenas o *nome simples*, um apelido conveniente.

Para o compilador e para a JVM, o package faz parte da **identidade** da classe. Duas classes com o mesmo nome simples em packages diferentes são classes completamente distintas, sem nenhuma relação entre si.

### Declarando o package

A primeira linha com código de todo arquivo `.java` declara a qual package ele pertence:

```java
package com.javanauta.todo_app.controller;
```

Essa linha vem antes de qualquer `import` e antes da declaração da classe. Se for omitida, a classe cai no *default package* (package sem nome) — algo que nunca se deve fazer em projeto real, porque classes do default package não podem ser importadas por ninguém.

### Package e diretório precisam bater

O compilador exige que a estrutura de pastas espelhe o package:

```
src/main/java/com/javanauta/todo_app/controller/TodoController.java
              └────────────┬──────────────────┘
                package com.javanauta.todo_app.controller
```

Se o arquivo estiver na pasta errada, o build quebra. O ponto (`.`) no package corresponde à barra (`/`) no caminho.

---

## A convenção de domínio invertido

Repare no prefixo `com.javanauta`. Essa é a convenção universal em Java: usar o domínio da organização **ao contrário**.

```
javanauta.com   →   com.javanauta
google.com      →   com.google
apache.org      →   org.apache
```

A lógica é simples: domínios são globalmente únicos por natureza (só existe um dono de `javanauta.com`), então usá-los como prefixo garante que seu `Cliente` jamais colida com o `Cliente` de outra empresa no mundo.

Regras práticas de nomenclatura:

- Sempre em **minúsculas** (`todo_app`, nunca `TodoApp`)
- Sem hífen — por isso a pasta do projeto é `todo-app` mas o package é `todo_app`, já que `-` não é caractere válido em identificador Java
- Não use `java.*` nem `javax.*` como prefixo: são reservados e a JVM bloqueia o carregamento

---

## `import` é apenas um atalho de nome

Aqui mora o maior mal-entendido sobre packages. Vamos ao que o `import` realmente faz.

Sem `import`, você pode usar qualquer classe pública escrevendo o FQN inteiro:

```java
public class TodoController {
    private final com.javanauta.todo_app.service.TodoService todoService;
}
```

Isso compila e funciona perfeitamente. É só ilegível.

O `import` existe para você poder escrever o nome curto:

```java
import com.javanauta.todo_app.service.TodoService;

public class TodoController {
    private final TodoService todoService;   // agora o nome simples basta
}
```

> **O `import` não copia código, não carrega nada e não concede permissão de acesso.** Ele apenas informa ao compilador: "quando eu escrever `TodoService`, me refiro àquele lá". É açúcar sintático, resolvido em tempo de compilação — o bytecode gerado contém sempre o FQN.

### Quando o import não é necessário

Três situações dispensam o `import`:

1. **Classes do mesmo package** — `TodoRequestDTO` e `TodoResponseDTO` estão ambos em `com.javanauta.todo_app.dto` e se enxergam diretamente
2. **`java.lang`** — importado implicitamente pelo compilador, por isso `String`, `Integer`, `Exception` e `Thread` funcionam do nada
3. **O FQN escrito por extenso**, como mostrado acima

Note que apenas `java.lang` é automático. `java.util.List` precisa de import explícito.

### O wildcard `*` não desce níveis

```java
import com.javanauta.todo_app.*;
```

Isso importa **somente** as classes que estão diretamente em `com.javanauta.todo_app` — neste projeto, apenas `TodoAppApplication`. Não traz nada de `controller`, `service` ou `dto`.

O `*` corresponde a "todas as classes deste package", nunca a "e de tudo que estiver abaixo".

### Resolvendo colisão de nomes

Voltando ao problema do início — duas classes `Cliente` no mesmo arquivo. Só é possível importar uma delas; a outra usa FQN:

```java
import com.empresa.financeiro.Cliente;   // este ganha o nome curto

public class RelatorioService {
    void gerar() {
        Cliente pagador = new Cliente();   // o do financeiro

        com.empresa.suporte.Cliente ticket =
            new com.empresa.suporte.Cliente();   // o outro, por extenso
    }
}
```

Java não tem apelido de import (`import ... as ...`) como Python ou Kotlin. O FQN por extenso é a única saída.

---

## Visibilidade: quem realmente controla o acesso

Se o `import` só resolve nomes, quem decide se você **pode** usar a classe? Os **modificadores de acesso**.

| Modificador | Mesma classe | Mesmo package | Subclasse em outro package | Qualquer lugar |
|---|:---:|:---:|:---:|:---:|
| `public` | ✅ | ✅ | ✅ | ✅ |
| `protected` | ✅ | ✅ | ✅ | ❌ |
| *(nenhum)* — package-private | ✅ | ✅ | ❌ | ❌ |
| `private` | ✅ | ❌ | ❌ | ❌ |

A linha do meio é a mais esquecida: **quando você não escreve modificador nenhum, o padrão não é `public`** — é *package-private*, visível apenas dentro do próprio package.

### Import não vence visibilidade

```java
// arquivo: com/javanauta/todo_app/service/TodoService.java
package com.javanauta.todo_app.service;

class TodoService {        // ← sem 'public': package-private
    // ...
}
```

```java
// arquivo: com/javanauta/todo_app/controller/TodoController.java
package com.javanauta.todo_app.controller;

import com.javanauta.todo_app.service.TodoService;   // import escrito corretamente

public class TodoController {
    private final TodoService todoService;           // ❌ NÃO COMPILA
}
```

Erro do compilador:

```
TodoService is not public in com.javanauta.todo_app.service;
cannot be accessed from outside package
```

O import está certo, o FQN está certo, o arquivo existe — e mesmo assim falha. É por isso que, neste projeto, `TodoService`, `TodoController` e as DTOs são todos declarados `public`: eles precisam atravessar fronteiras de package.

### Package-private é uma ferramenta de design

Não use `public` por reflexo. Deixar uma classe sem modificador é a forma de dizer *"isto é detalhe interno deste módulo"* e impedir mecanicamente que outros packages se acoplem a ela.

Um caso típico: uma classe auxiliar dentro de `service`, usada só pelo `TodoService`, não deveria ser `public` — assim o `controller` nunca consegue depender dela, e você pode reescrevê-la à vontade sem quebrar nada fora do package.

---

## Subpackage não é "filho" para efeito de acesso

Segunda pegadinha importante, e das que mais confundem.

```
com.javanauta.todo_app              ← package A
com.javanauta.todo_app.controller   ← package B
```

À primeira vista, B parece estar "dentro" de A. **Para o compilador, não está.** São dois packages independentes, sem qualquer relação de hierarquia ou privilégio.

Consequências concretas:

- Uma classe package-private em `todo_app` **não** é visível em `todo_app.controller`
- Uma classe package-private em `todo_app.controller` **não** é visível em `todo_app`
- `import com.javanauta.todo_app.*` não alcança os subpackages

A estrutura com pontos é organização de **nomes** e de **pastas**. Não é herança nem escopo aninhado. O aninhamento visual é uma conveniência humana, não uma regra da linguagem.

---

## Runtime: package + classloader

Um detalhe que raramente aparece, mas explica erros bizarros em servidores de aplicação e sistemas de plugin.

Em tempo de execução, a identidade de uma classe não é só o FQN — é o par **(classloader, FQN)**. A JVM chama isso de *runtime package*.

Isso significa que duas classes com exatamente o mesmo FQN, carregadas por classloaders diferentes, são classes **distintas** para a JVM. É a origem daquele erro que parece impossível:

```
java.lang.ClassCastException: com.exemplo.Cliente cannot be cast to com.exemplo.Cliente
```

Não é bug da JVM — são de fato dois `Cliente` diferentes, vindos de classloaders diferentes.

---

## O que o Spring constrói em cima disso

O Spring não altera nenhuma regra acima. Ele adiciona uma camada de **convenção**, tratando a *localização* da classe como configuração.

Neste projeto, a classe principal está na raiz:

```java
// src/main/java/com/javanauta/todo_app/TodoAppApplication.java
package com.javanauta.todo_app;

@SpringBootApplication
public class TodoAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(TodoAppApplication.class, args);
    }
}
```

A anotação `@SpringBootApplication` embute um `@ComponentScan` que, por padrão, varre **o package da classe anotada e todos os seus subpackages**:

```
com.javanauta.todo_app        ← @SpringBootApplication: ponto de partida do scan
├── controller                ← varrido → @RestController vira bean
├── service                   ← varrido → @Service vira bean
├── repository                ← varrido → repositório Spring Data detectado
├── model                     ← varrido → @Entity mapeada
├── dto                       ← varrido (sem anotações, nada é registrado)
└── exception                 ← varrido → @RestControllerAdvice registrado
```

O mesmo vale para `@EntityScan` (entidades JPA) e para o scan de repositórios do Spring Data.

### Por que a classe principal fica na raiz

Se você movesse `TodoAppApplication` para `com.javanauta.todo_app.config`, o scan passaria a começar em `config` — e não encontraria nenhum controller, service ou repository. A aplicação compilaria sem erro e subiria quebrada, com falha de bean não encontrado.

Do ponto de vista do Java, nada de errado. Do ponto de vista do Spring, o mapa foi destruído.

> **Regra prática:** a classe `@SpringBootApplication` fica no package raiz, acima de todos os demais.

### A divisão de responsabilidades

| | Java | Spring |
|---|---|---|
| **Usa o package para** | Namespace e controle de acesso | Descoberta de componentes |
| **Quando é resolvido** | Compilação | Startup da aplicação |
| **Se estiver errado** | Não compila | Compila e falha ao subir |

São mecanismos independentes que por acaso leem a mesma estrutura de diretórios.

---

## Exemplo completo

Um exemplo autocontido, fora do Spring, que exercita todos os conceitos: FQN, colisão de nomes, import como atalho e visibilidade package-private.

### Estrutura de arquivos

```
src/
└── com/
    └── loja/
        ├── Principal.java
        ├── vendas/
        │   ├── Pedido.java
        │   └── CalculadoraDesconto.java
        └── estoque/
            └── Pedido.java
```

Note que existem **dois** `Pedido.java`. Nomes simples idênticos, packages diferentes — legítimo e sem conflito.

### `com/loja/vendas/Pedido.java`

```java
package com.loja.vendas;

public class Pedido {                       // public: precisa ser visto de fora
    private final String cliente;
    private final double valor;

    public Pedido(String cliente, double valor) {
        this.cliente = cliente;
        this.valor = valor;
    }

    public double valorFinal() {
        // CalculadoraDesconto está no MESMO package:
        // sem import, e funciona mesmo sendo package-private
        return CalculadoraDesconto.aplicar(valor);
    }

    public String getCliente() {
        return cliente;
    }
}
```

### `com/loja/vendas/CalculadoraDesconto.java`

```java
package com.loja.vendas;

// SEM 'public' → package-private: detalhe interno de 'vendas'.
// Nenhuma classe fora deste package consegue tocar nela.
class CalculadoraDesconto {

    static double aplicar(double valor) {
        return valor > 100 ? valor * 0.9 : valor;
    }
}
```

### `com/loja/estoque/Pedido.java`

```java
package com.loja.estoque;

public class Pedido {                       // mesmo nome simples, outro package
    private final String sku;
    private final int quantidade;

    public Pedido(String sku, int quantidade) {
        this.sku = sku;
        this.quantidade = quantidade;
    }

    public String resumo() {
        return quantidade + "x " + sku;
    }
}
```

### `com/loja/Principal.java`

```java
package com.loja;

import com.loja.vendas.Pedido;      // só UM dos dois pode ganhar o nome curto

public class Principal {

    public static void main(String[] args) {

        // 1) Nome curto — habilitado pelo import acima
        Pedido venda = new Pedido("Maria", 200.0);
        System.out.println(venda.getCliente() + " paga " + venda.valorFinal());
        // → Maria paga 180.0

        // 2) O outro Pedido: FQN por extenso, porque o nome curto já foi ocupado
        com.loja.estoque.Pedido reserva = new com.loja.estoque.Pedido("ABC-123", 5);
        System.out.println(reserva.resumo());
        // → 5x ABC-123

        // 3) ❌ NÃO COMPILA — CalculadoraDesconto é package-private em 'vendas'.
        //    Nem adianta escrever o import: visibilidade não se resolve com import.
        //
        // double x = com.loja.vendas.CalculadoraDesconto.aplicar(200.0);
        //
        //    Erro: CalculadoraDesconto is not public in com.loja.vendas;
        //          cannot be accessed from outside package

        // 4) String funciona sem import: vem de java.lang, implícito
        String nome = "Java";
        System.out.println(nome);
    }
}
```

### Compilando e executando

```powershell
javac -d out (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object FullName)
java -cp out com.loja.Principal
```

Saída:

```
Maria paga 180.0
5x ABC-123
Java
```

### O que cada ponto demonstra

| Ponto | Conceito |
|---|---|
| `Pedido` em `vendas` e em `estoque` | Package como namespace: nomes iguais convivem sem colidir |
| `import com.loja.vendas.Pedido` | Import é atalho de nome — dá o nome curto a uma classe só |
| `com.loja.estoque.Pedido` por extenso | FQN é o nome verdadeiro; sempre funciona, com ou sem import |
| `CalculadoraDesconto` usada em `Pedido` | Mesmo package: sem import e sem `public` |
| A linha 3 comentada | **Import não concede acesso** — quem decide é o modificador |
| `String` sem import | `java.lang` é implícito |

---

## Erros comuns e o que significam

| Mensagem do compilador | Causa provável |
|---|---|
| `cannot find symbol: class X` | Faltou o `import`, ou o nome está errado |
| `X is not public in Y; cannot be accessed from outside package` | Import correto, mas a classe é package-private |
| `class X is public, should be declared in a file named X.java` | Nome do arquivo diferente do nome da classe pública |
| `declared package Y does not match expected package Z` | O arquivo está numa pasta que não corresponde ao seu `package` |
| `package Y does not exist` | Pasta ausente, erro de digitação ou dependência faltando no `pom.xml` |

---

## Resumo

1. **Namespace** é o conceito; em Java, ele se chama **package**
2. O nome real de uma classe é o **FQN** — o package faz parte da identidade dela
3. **`import` resolve nomes, não permissões**
4. Quem controla acesso é o **modificador de visibilidade**; o padrão é package-private, não `public`
5. **Subpackage não herda nada** do package acima — são independentes
6. `java.lang` é implícito; o wildcard `*` não desce níveis
7. O **Spring** apenas se apoia nesse mecanismo, tratando a localização como metadado de descoberta
