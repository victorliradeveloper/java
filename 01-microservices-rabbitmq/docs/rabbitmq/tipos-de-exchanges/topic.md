# Topic Exchange

## Visão Geral

A **topic exchange** roteia mensagens comparando a **routing key da
mensagem** contra um **padrão de routing key** declarado em cada binding,
usando dois curingas: `*` (uma palavra) e `#` (zero ou mais palavras).
A routing key vira uma **string hierárquica**, com palavras separadas
por ponto (`todo.created`, `user.login.failed`, `pt.sp.osasco.metric`).

```
                          ┌──────────────────────────┐
publish(rk="todo.created")│   exchange (topic)       │
            │             │                          │
            └────────────►│  binding "todo.created" ✓│──► todo.created.queue
                          │  binding "todo.updated" ✗│
                          │  binding "todo.*"       ✓│──► (outra fila qualquer)
                          │  binding "todo.#"       ✓│──► todo.audit.queue
                          │  binding "user.*"       ✗│
                          └──────────────────────────┘
```

Topic é o **tipo de exchange mais flexível** sem precisar de schema de
headers complicado: um consumer com `todo.#` recebe tudo do domínio,
outro com `todo.created` filtra só um tipo, e os dois coexistem na
mesma exchange.

> **O projeto usa topic em todas as três exchanges**:
> [`todo.exchange`](../../../todo-service/src/main/java/com/microservices/todo/config/RabbitMQConfig.java)
> (eventos),
> [`todo.dlx`](../../../notification-service/src/main/java/com/microservices/notification/config/RabbitMQConfig.java)
> (DLX do notification),
> [`todo.audit.dlx`](../../../audit-service/src/main/java/com/microservices/audit/config/RabbitMQConfig.java)
> (DLX dedicada do audit). Justificativa em [`../exchange.md`](../exchange.md).

---

## A routing key como hierarquia

Em topic, a routing key **não é uma string opaca** — o broker a interpreta
como **uma sequência de palavras separadas por ponto**. Convenção típica:

```
dominio.acao
dominio.sub-dominio.acao
regiao.cidade.servico.acao
```

No projeto:

```
todo.created
todo.updated
todo.deleted
```

Estrutura `dominio.acao`. Dois níveis. A escolha do separador (`.`) e da
ordem (mais geral à esquerda → mais específico à direita) é convenção
que casa com os curingas. Se você invertesse (`created.todo`), `todo.*`
não capturaria mais "todos os eventos do todo" — capturaria "todas as
ações chamadas todo".

**Regras práticas de design de routing key em topic**:

1. **Geral → específico, da esquerda pra direita.** `dominio.acao` é
   melhor que `acao.dominio`.
2. **Use poucos níveis no começo.** É mais fácil adicionar nível depois
   (`todo.created` → `todo.priority.created`) que remover.
3. **Não passe identificadores únicos.** `todo.created.486c3c4d-...` não
   tem valor de routing (ninguém vai bindar pra esse UUID); pollui a
   tabela de bindings e o log do broker. Identificadores vão no body.
4. **Mantenha vocabulário fechado.** Documentar as ações válidas
   (`created`/`updated`/`deleted`) evita derrapar pra "created2",
   "createdNew" e variantes que quebram bindings silenciosamente.

---

## Os curingas

Topic tem **dois**, e só dois. Cada um casa em **uma posição** do padrão.

### `*` — exatamente uma palavra

Casa com **uma única palavra** (não zero, não duas). É o curinga "estrito".

| Padrão | `todo.created` | `todo` | `todo.foo.bar` |
|---|---|---|---|
| `todo.*` | ✓ | ✗ | ✗ |
| `*.created` | ✓ | ✗ | ✗ |
| `*.*` | ✓ | ✗ | ✗ |

`todo.*` significa: "exatamente duas palavras, a primeira é `todo`, a
segunda é qualquer uma". Não casa com `todo` (uma palavra só) nem com
`todo.foo.bar` (três palavras).

### `#` — zero ou mais palavras

O curinga "frouxo". Casa com qualquer número de palavras (incluindo zero).

| Padrão | `todo.created` | `todo` | `todo.foo.bar` | `user.created` |
|---|---|---|---|---|
| `todo.#` | ✓ | ✓ | ✓ | ✗ |
| `#.created` | ✓ | ✗ | ✗ | ✓ |
| `#` | ✓ | ✓ | ✓ | ✓ |

`#` sozinho captura **tudo** — equivale ao broadcast da fanout, mas
implementado em cima de topic. Útil quando alguém quer "auditar tudo
que passa nessa exchange" sem ter que listar bindings.

> O projeto usa `todo.#` na
> [`todo.audit.queue`](../../../audit-service/src/main/java/com/microservices/audit/config/RabbitMQConfig.java)
> e `#` no binding da `todo.audit.dlq`. Ver
> [`../filas.md`](../filas.md) pra tabela completa de bindings.

### Combinações úteis

| Padrão | Significa |
|---|---|
| `*.*.error` | exatamente três palavras, última é `error` |
| `app.#.error` | começa com `app`, qualquer caminho até `error` no fim |
| `#.created` | termina em `created`, qualquer prefixo |
| `*.created.#` | segunda palavra é `created`, pode ter qualquer cauda |

> Curingas funcionam **só na rk do binding** — nunca na rk da mensagem
> publicada. Publicar com `rk="todo.#"` não dispara nada: o broker trata
> isso como literal "todo.#", que não casa com nenhum padrão a menos que
> exista um binding com rk **literal** `todo.\#` (escape — coisa que
> nunca se vê em projeto real).

---

## Como o projeto usa topic

### Exchange 1: `todo.exchange` — eventos de domínio

Declarada no
[`todo-service/RabbitMQConfig`](../../../todo-service/src/main/java/com/microservices/todo/config/RabbitMQConfig.java)
(publisher) e também nos consumers (notification e audit) — idempotente.

Bindings em `todo.exchange`:

| Padrão | Fila | Consumer | O que recebe |
|---|---|---|---|
| `todo.created` | `todo.created.queue` | notification | só rk exata `todo.created` |
| `todo.updated` | `todo.updated.queue` | notification | só rk exata `todo.updated` |
| `todo.deleted` | `todo.deleted.queue` | notification | só rk exata `todo.deleted` |
| `todo.#` | `todo.audit.queue` | audit | **tudo** que começa com `todo.` |

O efeito de publicar `rk="todo.created"`:

```
publish(rk="todo.created")
   ├─► todo.created.queue (match exato)
   ├─► todo.updated.queue ✗
   ├─► todo.deleted.queue ✗
   └─► todo.audit.queue   (match wildcard todo.#)
```

Resultado: **2 cópias por publish**. Uma vai pro notification (renderiza
email "Todo criado"), outra pro audit (grava no `todo_audit_log`).

A genialidade do topic aqui é que **os dois consumers convivem na mesma
exchange sem precisar conhecer um ao outro**. O notification não sabe
que o audit existe. O publisher não sabe quem está escutando. Adicionar
um terceiro consumer (`metrics-service` com `todo.#`?) é declarar fila
+ binding e subir — zero alteração nos outros serviços.

### Exchange 2: `todo.dlx` — DLX do notification

Topic também. Recebe mensagens que esgotaram retry nas filas do
notification. Bindings:

| Padrão | DLQ | O que recebe |
|---|---|---|
| `todo.created` | `todo.created.dlq` | failures da `todo.created.queue` |
| `todo.updated` | `todo.updated.dlq` | failures da `todo.updated.queue` |
| `todo.deleted` | `todo.deleted.dlq` | failures da `todo.deleted.queue` |

A rk original da mensagem é **preservada** pela DLX via
`x-dead-letter-routing-key` no `QueueBuilder` da fila principal. Por isso
o binding na DLQ pode ser exato — sabemos que a rk preservada vai ser
exatamente uma das três. Ver [`../dlq.md`](../dlq.md) pra detalhes.

Por que topic e não direct? Coerência com a exchange principal e
flexibilidade pra evolução. Se amanhã aparecer `todo.archived`, basta
declarar fila + binding na DLX também — direct exigiria a mesma coisa,
então topic não custa nada e abre a porta pra curinga (`todo.*.dlq`?)
se algum dia fizer sentido.

### Exchange 3: `todo.audit.dlx` — DLX dedicada do audit

Topic com binding catch-all (`#`):

| Padrão | DLQ |
|---|---|
| `#` | `todo.audit.dlq` |

Catch-all porque a `todo.audit.queue` também é catch-all (`todo.#`) —
qualquer rk que chegue na fila principal precisa caber na DLQ se falhar.
Topic + `#` resolve sem precisar enumerar.

---

## Comparação rápida com os outros tipos

| Aspecto | topic | direct | fanout |
|---|---|---|---|
| Match | padrão com `*` (1 palavra) e `#` (N palavras) | rk exata | nada (broadcast) |
| Custo de routing | O(N bindings) — match de padrão | O(1) — hash lookup | O(1) por fila |
| Filtragem no broker | sim, granular | sim, exata | não |
| Adicionar consumer "que quer tudo" | binding `dominio.#` | binding por rk | binding qualquer |
| Adicionar consumer "que quer um pedaço" | binding com `*` ou `#` | listar todas as rks | filtrar no código |
| Suporta hierarquia natural | **sim** | não (rk é opaca) | não |

Topic é a escolha "default sensata" pra eventos de domínio: você não
paga muito por flexibilidade, e ela paga caro quando o sistema cresce.

---

## Quando usar topic

Use topic quando:

1. **A routing key tem hierarquia natural** (`dominio.acao`,
   `regiao.tipo.id`). Se a rk é uma palavra só, direct serve melhor.
2. **Existem múltiplos perfis de inscrição.** Um consumer quer tudo,
   outro quer fatia específica. Topic deixa cada um declarar o seu
   padrão.
3. **Você espera evolução.** Novos tipos de evento aparecerão; consumers
   com curinga absorvem sem mudança de código.
4. **Bindings precisam ser legíveis na config.** `todo.#` documenta
   "esse consumer quer tudo do domínio Todo" de forma que direct (com
   3 bindings explícitos) não consegue.

**Não use topic quando:**

- A rk é **plana** (`error`, `info`) e não vai ganhar hierarquia →
  direct é mais explícito.
- Todo consumer quer toda mensagem → fanout é mais simples (não há
  pattern matching pra fazer).
- O critério de roteamento depende de **múltiplos campos**
  independentes (severidade + região + cliente) → headers exchange.
- A rk vai conter dados de alta cardinalidade (UUIDs, timestamps) →
  isso não é roteamento, é pollution.

---

## Declaração no Spring AMQP

Exemplo retirado do
[`todo-service/RabbitMQConfig.java`](../../../todo-service/src/main/java/com/microservices/todo/config/RabbitMQConfig.java)
(adaptado pra ficar autocontido):

```java
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE        = "todo.exchange";
    public static final String QUEUE_CREATED   = "todo.created.queue";
    public static final String QUEUE_UPDATED   = "todo.updated.queue";
    public static final String QUEUE_DELETED   = "todo.deleted.queue";
    public static final String ROUTING_CREATED = "todo.created";
    public static final String ROUTING_UPDATED = "todo.updated";
    public static final String ROUTING_DELETED = "todo.deleted";

    @Bean
    public TopicExchange todoExchange() {
        return new TopicExchange(EXCHANGE);     // durable=true, autoDelete=false
    }

    @Bean
    public Queue createdQueue() { return new Queue(QUEUE_CREATED, true); }
    @Bean
    public Queue updatedQueue() { return new Queue(QUEUE_UPDATED, true); }
    @Bean
    public Queue deletedQueue() { return new Queue(QUEUE_DELETED, true); }

    @Bean
    public Binding bindCreated(Queue createdQueue, TopicExchange todoExchange) {
        return BindingBuilder.bind(createdQueue).to(todoExchange).with(ROUTING_CREATED);
    }
    // ... bindUpdated, bindDeleted analogos
}
```

E no audit, o uso de curinga:

```java
@Bean
public Binding bindAudit(Queue auditQueue, TopicExchange todoExchange) {
    return BindingBuilder.bind(auditQueue).to(todoExchange).with("todo.#");
    //                                                            ^^^^^^^
    //                                                            o curinga vai aqui,
    //                                                            no binding, nunca na publish
}
```

Detalhes importantes:

- **`.to(topicExchange).with(pattern)`** — pra topic, o builder exige
  rk/padrão. Não passar rk dá erro de compilação.
- **`new TopicExchange(name)`** — defaults são `durable=true` e
  `autoDelete=false`. Pra dev/staging onde você quer que a exchange
  suma quando o último binding sair, use o construtor de 3 args.
- **Idempotência**: declarar a mesma `TopicExchange` em dois serviços
  diferentes é ok — broker ignora se já existe com mesmo tipo. **Tipo
  diferente** (alguém declarou como direct antes) faz o startup falhar
  com `PRECONDITION_FAILED`.

Publish em topic:

```java
rabbitTemplate.convertAndSend("todo.exchange", "todo.created", payload);
//                            ^exchange        ^rk literal    ^payload
```

A rk publicada **nunca** contém curinga. Curinga só faz sentido no
padrão do binding, do lado consumer.

---

## Custo de routing e performance

Diferente de direct (hash lookup O(1)), topic faz **pattern matching**
em cada binding. O algoritmo do RabbitMQ internamente otimiza usando uma
**trie** das routing keys — então o custo prático é mais próximo de
O(log N) que O(N), mas formalmente é dominado pela quantidade de
bindings.

Cenários:

- **Poucos bindings (<100)**: diferença imperceptível. Direct vs topic
  é debate teórico.
- **Milhares de bindings** (típico em sistemas multi-tenant onde cada
  cliente tem uma rk dedicada): a trie do RabbitMQ aguenta bem, mas o
  custo aparece. Migrar pra direct ou consolidar bindings vira pauta.
- **Bindings com `#` no meio** (`a.#.b`): mais caros que sem (`a.b.#`).
  O matcher tem que explorar combinações.

Regra prática: **só se preocupe quando o número de bindings entrar na
casa dos milhares**. Pra projeto comum (8 bindings como o nosso), zero
overhead percebido.

---

## Pitfalls comuns

### 1. Curinga na routing key publicada

```java
// ERRADO — broker trata como string literal "todo.#"
rabbitTemplate.convertAndSend("todo.exchange", "todo.#", payload);
```

O publisher **sempre** publica com rk literal. Curinga é detalhe do
consumer (no binding). Esse erro tipicamente aparece quando alguém
pensou "vou broadcast pra todas as ações" — mas isso é responsabilidade
do **binding** do consumer, não da publish.

### 2. Esquecer que `*` exige exatamente uma palavra

```
binding: todo.*
publish: todo                   → ✗ (zero palavras depois de "todo")
publish: todo.created           → ✓
publish: todo.created.foo       → ✗ (duas palavras depois de "todo")
```

Quem quer "qualquer caminho depois de todo, incluindo nenhum" usa `todo.#`.

### 3. Conflito de bindings (cópias duplicadas)

```
binding 1: todo.created   → queue.X
binding 2: todo.*         → queue.X
```

Publicar `rk="todo.created"` casa nos **dois** bindings. Mas como apontam
pra **mesma fila X**, o RabbitMQ entrega **uma única cópia** — ele
deduplifica por destino. Sem pegadinha.

Diferente é:

```
binding 1: todo.created   → queue.X
binding 2: todo.*         → queue.Y
```

Aí publicar `rk="todo.created"` entrega em ambas. Cópias separadas em
filas separadas.

### 4. Routing key case-sensitive

`todo.created` ≠ `Todo.Created` ≠ `TODO.CREATED`. Convenção: tudo lowercase.
Vale codificar essa regra no publisher (ou em constantes) pra não
depender de disciplina manual.

### 5. Padrão `#.algo` é caro

`#` "no meio" da rk pode explodir combinações. `#.created` exige o
matcher testar todos os sufixos. Em projeto pequeno é invisível, mas em
sistemas com milhares de bindings é gargalo conhecido. Quando possível,
prefira `#` **no fim** ou no começo.

---

## Inspecionar

### Listar exchanges topic

```powershell
docker exec rabbitmq rabbitmqctl list_exchanges name type | Select-String "topic"
```

Em ambiente do projeto saudável:

```
amq.topic             topic    ← built-in
todo.exchange         topic
todo.dlx              topic
todo.audit.dlx        topic
```

### Listar bindings de uma topic

```powershell
docker exec rabbitmq rabbitmqctl list_bindings source_name routing_key destination_name | Select-String "todo.exchange"
```

Saída esperada:

```
todo.exchange    todo.created   todo.created.queue
todo.exchange    todo.updated   todo.updated.queue
todo.exchange    todo.deleted   todo.deleted.queue
todo.exchange    todo.#         todo.audit.queue
```

A coluna `routing_key` mostra o **padrão** (com curinga se houver), não
a rk da mensagem.

### Painel Mgmt UI

`http://localhost:15672` → **Exchanges** → clicar em `todo.exchange`.

- A seção **Bindings** mostra todos os padrões e filas destino.
- A seção **Publish message** permite testar uma rota: digite
  `routing_key=todo.created` e veja qual fila incrementa. Útil quando
  você acabou de adicionar um binding com `#` ou `*` e quer confirmar
  o match.

### Validar padrão num REPL

Não tem REPL oficial, mas em projeto Java você pode usar
`AmqpTopicMatcher` (interno do Spring AMQP) ou rodar lógica do tipo
"`todo.#` casa com `todo.created`?" via teste unitário.

---

## Resumo

- **Topic = routing key como hierarquia** (`dominio.acao`) +
  **curingas no binding** (`*` = 1 palavra, `#` = N palavras).
- **`#` é frouxo** (zero ou mais), **`*` é estrito** (exatamente 1).
- Curinga vai **só no binding**, nunca na rk publicada.
- Permite "todo consumer quer um pedaço diferente" filtrando no broker —
  o ganho central sobre fanout.
- Permite hierarquia natural e evolução sem mudança no publisher —
  o ganho central sobre direct.
- Custo de routing é O(log N) na prática (trie do RabbitMQ); só
  importa em milhares de bindings.
- **Projeto usa topic em todas as 3 exchanges** porque a rk é
  hierárquica (`todo.{action}`) e tem dois consumers com perfis
  diferentes (notification = 3 rks exatas, audit = `todo.#`).

Links cruzados:
- [`../exchange.md`](../exchange.md) — visão geral das exchanges do projeto.
- [`../filas.md`](../filas.md) — tabela completa de bindings.
- [`../publisher.md`](../publisher.md) — como o publisher manda na topic.
- [`../consumer.md`](../consumer.md) — como o consumer escuta.
- [`./direct.md`](./direct.md) — o tipo de irmão sem curinga.
- [`./fanout.md`](./fanout.md) — broadcast cego.
- [`./headers.md`](./headers.md) — roteamento por headers AMQP.
