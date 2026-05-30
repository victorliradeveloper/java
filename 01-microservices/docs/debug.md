# Debug no IntelliJ

## Visão Geral

Este guia mostra como usar o **debugger do IntelliJ** neste projeto para
inspecionar o fluxo de uma request — desde o controller até o
`OutboxService`. Tem três caminhos, do mais simples ao mais "real":

1. **Debugar testes unitários** — não precisa subir Docker, Rabbit, banco.
   Bom pra aprender os atalhos.
2. **Debugar a aplicação rodando local** (`TodoServiceApplication.main()`)
   — Docker sobe só Postgres/Rabbit/Eureka, a app roda dentro do IntelliJ.
   Tem hot reload e é o fluxo de dia a dia.
3. **Remote debug do container Docker** — a JVM dentro do container expõe
   uma porta JDWP e o IntelliJ se anexa. Útil pra reproduzir bug que só
   aparece no ambiente containerizado.

A regra de ouro: **comece pelo 1, vá pro 2 no dia a dia, use o 3 só
quando precisar do ambiente real**.

---

## Atalhos essenciais

| Ação | Atalho |
|---|---|
| Step Over (próxima linha, sem entrar no método) | **F8** |
| Step Into (entra no método chamado) | **F7** |
| Step Out (sai do método atual) | **Shift+F8** |
| Resume (vai até o próximo breakpoint) | **F9** |
| Evaluate Expression (avalia qualquer expressão Java na pausa) | **Alt+F8** |
| Drop Frame ("volta no tempo" — desfaz a chamada do método atual) | barra de ferramentas do debugger |

---

## Onde colocar o primeiro breakpoint

O fluxo mais didático é o **POST /todos** — passa por controller,
idempotência, service, mapper, JPA e outbox. O ponto recomendado:

**Arquivo:** `todo-service/src/main/java/com/microservices/todo/service/TodoService.java`
**Linha:** `36` (a primeira do método `create`)

```java
@Transactional
public TodoResponseDTO create(TodoRequestDTO dto) {
    Todo todo = repository.save(mapper.toEntity(dto));   // ← breakpoint aqui
    TodoResponseDTO response = mapper.toResponse(todo);
    outboxService.record(
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_CREATED,
            response.id(),
            AGGREGATE_TYPE,
            "CREATED",
            TodoEvent.of(response.id(), response.title(), "CREATED")
    );
    return response;
}
```

### Tour guiado quando a execução pausar

1. **No instante da pausa** — painel **Variables** mostra `dto` (o que o
   Postman mandou) e `this.repository / this.mapper / this.outboxService`
   (beans injetados pelo Spring via `@RequiredArgsConstructor`).
   Já dá pra **ver** que o Lombok + `final` realmente injetam os beans —
   deixa de ser teoria.
2. **F7 em `mapper.toEntity(dto)`** — você cai dentro de `TodoMapperImpl`
   (gerado pelo MapStruct em `target/generated-sources/`). É o código
   real que o MapStruct cria. A entidade volta com `id = null` — quem
   gera o UUID é o banco/JPA, não o mapper.
3. **F8 no `repository.save(...)`** — antes do F8, `todo` nem existe.
   Depois do F8, `todo` aparece no Variables com `id` preenchido (UUID).
   **Você vê o banco gerando a chave.**
4. **F8 na linha de `mapper.toResponse(todo)`** — o `response` (record DTO)
   aparece pronto. Confirma que `response.id() == todo.getId()`.
5. **F7 em `outboxService.record(...)`** — entra no `OutboxService` e vê
   que **não publica nada no RabbitMQ ainda** — só insere uma linha na
   tabela `outbox_event`. É o padrão Transactional Outbox em ação.

### O experimento do `@Transactional`

Pare na linha 38 (`outboxService.record(...)`), abra um cliente SQL e
rode:

```sql
SELECT * FROM todo;
SELECT * FROM outbox_event;
```

**Nenhuma linha aparece.** Por quê? Porque o método está `@Transactional`
— o commit só acontece quando `create` retornar. Dê F9 (Resume), rode o
SELECT de novo e as duas linhas aparecem juntas. É a melhor forma de
"sentir" o que `@Transactional` realmente faz.

---

## Caminho 1 — Debugar testes (sem Docker)

Mais rápido pra praticar atalhos. O cursor precisa estar dentro de uma
classe de teste (ex.: `TodoControllerTest`).

1. Coloque um breakpoint em qualquer linha do teste, ex.: linha 73 de
   `TodoControllerTest.java`:
   ```java
   ResponseEntity<TodoResponseDTO> response = controller.create(idempotencyKey, request);
   ```
2. Clique direito no nome do teste → **Debug 'shouldCreate…'** (ou no
   ícone de bug ao lado do método).
3. Use **F7** pra entrar no `controller.create` — você "pula" do teste
   pro código de produção. Os mocks (`@Mock TodoService`,
   `@Mock IdempotencyService`) retornam o que foi configurado com
   `when(...).thenReturn(...)`.

> **Quando preferir isso**: aprender os atalhos, entender como mocks do
> Mockito interceptam chamadas, isolar lógica do controller sem subir
> nada externo.

---

## Caminho 2 — Debugar a aplicação local (recomendado no dia a dia)

A app roda no IntelliJ; só a infra fica no Docker.

1. Suba apenas a infra:
   ```bash
   docker-compose up -d postgres rabbitmq eureka-server
   ```
2. No IntelliJ, abra `TodoServiceApplication.java`.
3. Clique no ícone de bug ao lado do método `main` → **Debug
   'TodoServiceApplication.main()'**.
4. Coloque o breakpoint em `TodoService.java:36`.
5. Mande o POST pelo Postman:
   ```
   POST http://localhost:8081/todos
   Content-Type: application/json

   { "title": "estudar debug", "description": "intellij" }
   ```
6. O IntelliJ pausa na linha. Use o tour guiado da seção anterior.

> **Vantagens**: hot reload (se configurar DevTools), breakpoints
> respondem rápido, dá pra rodar testes lado a lado.
> **Limitação**: você está rodando fora do container — se o bug depende
> de variável de ambiente do compose ou de DNS interno do Docker, use o
> Caminho 3.

---

## Caminho 3 — Remote debug do container Docker

A JVM dentro do container precisa ouvir numa porta de debug
(**JDWP** — Java Debug Wire Protocol) e o IntelliJ se anexa nela.

### Configuração

No `docker-compose.yml`, no serviço `todo-service`, adicione:

```yaml
  todo-service:
    build: ./todo-service
    environment:
      # ... outras envs ...
      JAVA_TOOL_OPTIONS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    ports:
      - "8081:8081"
      - "5005:5005"   # porta do debugger
```

O que cada flag faz:

| Flag | Significado |
|---|---|
| `server=y` | A JVM **espera** o debugger conectar (é o servidor JDWP) |
| `suspend=n` | A app sobe normal, sem travar esperando o debugger. Use `suspend=y` se quiser debugar o **startup** |
| `address=*:5005` | Escuta em todas as interfaces dentro do container, na porta 5005 |

Por que usar `JAVA_TOOL_OPTIONS` em vez de editar o `Dockerfile`: a JVM
lê essa env automaticamente, então a imagem fica intacta — você liga e
desliga o debug só editando o compose.

### Run Configuration no IntelliJ

`Run` → `Edit Configurations…` → `+` → **Remote JVM Debug**

- **Name:** `todo-service @ docker`
- **Host:** `localhost`
- **Port:** `5005`
- **Use module classpath:** `todo-service`

### Fluxo de uso

1. `docker-compose up --build` (como sempre).
2. No IntelliJ, selecione `todo-service @ docker` e clique em **Debug**
   (Shift+F9).
3. Coloque o breakpoint em `TodoService.java:36`.
4. Mande o POST pelo Postman → IntelliJ pausa.

### Limitação importante

O container roda o `.jar` que foi construído no `mvn package` durante o
`docker build`. **Se você mudar o código fonte, o container não vê** —
precisa rebuildar. Pra hot reload de verdade no Docker é outro setup
(volume mount + Spring DevTools). Pra debug normal, isso aqui basta.

---

## Truques avançados de breakpoint

Clique com **botão direito num breakpoint** pra abrir as opções:

| Opção | O que faz | Quando usar |
|---|---|---|
| **Condition** | Só pausa se a expressão for `true`. Ex.: `dto.title().contains("erro")` | Pulando ruído quando o método é chamado muitas vezes |
| **Suspend: No** + **Evaluate and log** | Logga uma expressão e segue sem pausar | Debug sem editar o código (sem `log.info` espalhado) |
| **Hit count** | Só pausa na N-ésima passagem | Pegar exatamente a iteração que dá problema num loop |
| **Watch** (botão direito numa variável → Add to Watches) | Fixa a variável num painel separado | Acompanhar uma variável específica entre vários frames |
| **Drop Frame** | Desfaz a chamada do método atual e volta pra antes dele | "Re-executar" um trecho sem reiniciar a request |

### Evaluate Expression (Alt+F8)

Na pausa, abra com **Alt+F8** e teste qualquer expressão Java:

```java
dto.title().toUpperCase()
repository.count()
mapper.toEntity(dto).getId() == null
```

Não muda o código, não loga nada — só roda a expressão no contexto
atual da pausa. É a feature mais útil pro dia a dia.

---

## O que esse tour te ensina, no fim

1. **Spring DI na prática** — você vê os beans dentro de `this`.
2. **MapStruct sem caixa-preta** — entra no código gerado e confere o
   que ele realmente faz (veja também [patterns/mapstruct.md](patterns/mapstruct.md)).
3. **Geração de ID** — `null` antes do save, UUID depois.
4. **Transactional Outbox em ação** — `OutboxService` insere na tabela
   em vez de publicar direto. O publisher externo lê depois.
5. **`@Transactional` de verdade** — o commit só acontece no return; até
   lá, `SELECT` externo não enxerga nada.

---

## Referências

- [patterns/mapstruct.md](patterns/mapstruct.md) — código gerado que aparece no Step Into
- [rabbitmq/publisher.md](rabbitmq/publisher.md) — pra onde o evento do outbox vai depois
- [testes/junit.md](testes/junit.md) — escrever testes que dá pra debugar (Caminho 1)
