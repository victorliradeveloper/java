# Como rodar os testes

## Estrutura do build

O projeto **não tem um `pom.xml` na raiz** — cada serviço (`todo-service`,
`notification-service`, etc.) tem o seu próprio POM independente. Não existe um
POM agregador (parent/aggregator) que rode tudo de uma vez.

Consequência prática: **`mvn test` na raiz do projeto não funciona**. Sem um POM
no diretório, o Maven falha com:

```
The goal you specified requires a project to execute but there is no POM in this directory
```

Os arquivos `mvnw` / `mvnw.cmd` na raiz são só o wrapper do Maven — eles também
precisariam de um POM pra ter o que construir.

Para rodar os testes, **entre na pasta de cada serviço** e use o `mvn` global
(já instalado, 3.9.x).

---

## Rodar todos os testes de um serviço

```bash
cd todo-service
mvn test
```

```bash
cd notification-service
mvn test
```

---

## Rodar só os testes Pact (contract testing)

O fluxo do Pact tem **duas etapas, e a ordem importa**: o consumer **gera** o
contrato, o provider **verifica** contra esse arquivo. Veja [pact.md](pact.md)
para o detalhe do ciclo.

### 1. Consumer — gera o contrato

```bash
cd notification-service
mvn test -Dtest=TodoEventConsumerPactTest
```

Escreve/atualiza `pacts/notification-service-todo-service.json` na raiz do
projeto (definido pela anotação `@PactDirectory("../pacts")` no teste).

### 2. Provider — verifica o contrato

```bash
cd ../todo-service
mvn test -Dtest=TodoEventProviderPactTest
```

Lê o JSON gerado no passo 1 e confirma que o `TodoEvent` que o `todo-service`
publica bate com o contrato.

> ⚠️ **Rode o consumer antes do provider.** Se você mudar o contrato no teste
> consumer e rodar só o provider, ele valida contra a versão antiga do arquivo.

A pasta `pacts/` é **gerada automaticamente** pelo teste consumer — editar à mão
não adianta, ela é sobrescrita a cada execução.

---

## Rodar um único método de teste

```bash
mvn test -Dtest=TodoEventConsumerPactTest#consumesCreatedEvent
```

---

## Opcional: POM agregador na raiz

Se quiser poder rodar `mvn test` da raiz e disparar os testes de todos os
serviços de uma vez, crie um `pom.xml` agregador na raiz com
`<packaging>pom</packaging>` e um bloco `<modules>` listando cada serviço. O
Maven então respeita a ordem de dependência entre os módulos.

Isso ainda não existe no projeto — hoje cada serviço é construído de forma
independente.
