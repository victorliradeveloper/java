# Direct Exchange

## Visão Geral

A **direct exchange** é o tipo mais simples de exchange do AMQP 0-9-1: ela
roteia uma mensagem **comparando a routing key da mensagem com a routing
key do binding por igualdade exata**. Sem curinga, sem padrão, sem regex —
ou bate caractere por caractere, ou não bate.

```
                          ┌────────────────────────┐
publish(rk="error") ─────►│   exchange (direct)    │
                          │                        │
                          │  binding rk="info"  ✗  │
                          │  binding rk="error" ✓  │──► queue.errors
                          │  binding rk="debug" ✗  │
                          └────────────────────────┘
```

Pensa nela como um **switch/case** do roteador AMQP: cada binding é um
`case` explícito, e a mensagem cai exatamente em um (ou em vários, se
houver bindings duplicados com a mesma rk em filas diferentes).

> O projeto atual não usa direct exchange — usa **topic** em todas as três
> exchanges (`todo.exchange`, `todo.dlx`, `todo.audit.dlx`). Por quê está
> explicado no final, em "Por que o projeto não usa direct". Ainda vale
> conhecer o tipo: é o que ensina, por contraste, a lógica de cada um dos
> outros tipos.

---

## A regra de roteamento, formalmente

Quando uma mensagem com routing key `K` chega numa direct exchange `E`, o
broker:

1. Olha **todos os bindings** entre `E` e qualquer fila.
2. Pra cada binding com routing key `K_b`, testa `K == K_b` (igualdade
   literal de string — case-sensitive, sem trim, sem normalização).
3. Pra cada match, **copia** a mensagem na fila destino do binding.
4. Se nenhum binding casar, a mensagem é **descartada silenciosamente**
   (a menos que a mensagem tenha sido publicada com flag `mandatory=true`,
   caso em que o broker devolve via `basic.return`).

Pontos não-óbvios:

- **Bindings iguais em filas diferentes geram cópias múltiplas.** Se duas
  filas estão bindadas em `direct.exchange` com a mesma rk `"error"`, uma
  publicação com `rk="error"` entrega cópias em **ambas**. Isso permite
  fan-out controlado sem usar `fanout`.
- **Várias rks numa mesma fila são permitidas.** Você pode bindar
  `queue.X` em `direct.exchange` com rk `"info"`, `"warn"` e `"error"`
  — três bindings distintos. A fila recebe mensagens publicadas com
  qualquer uma das três.
- **A comparação é byte-a-byte.** `"Error"` ≠ `"error"`, `" error"` ≠
  `"error"`. Trim e case normalization são responsabilidade do publisher.

---

## Cenários típicos

### Cenário 1: severidade de log (o exemplo clássico)

Você tem um sistema que publica logs com routing key igual à severidade
(`info`, `warn`, `error`, `debug`) e quer:

- Uma fila que só recebe **errors** → consumer manda Slack alert.
- Uma fila que recebe **errors + warns** → consumer grava em disco.
- Uma fila que recebe **tudo** → consumer manda pra Elasticsearch.

Topologia em direct:

```
                                ┌─────────────┐
publish(rk="error") ────┐       │             │ rk="error"  ──► queue.slack
                        │       │   direct    │
publish(rk="warn")  ────┼──────►│  exchange   │ rk="error"  ──► queue.disk
                        │       │             │ rk="warn"   ──► queue.disk
publish(rk="info")  ────┤       │             │
                        │       │             │ rk="info"   ──► queue.elastic
publish(rk="debug") ────┘       │             │ rk="warn"   ──► queue.elastic
                                │             │ rk="error"  ──► queue.elastic
                                │             │ rk="debug"  ──► queue.elastic
                                └─────────────┘
```

`queue.disk` tem **dois bindings** (`warn` e `error`). `queue.elastic` tem
**quatro**. `queue.slack` tem só **um**. Tudo via igualdade exata, sem
necessidade de wildcard.

> Esse é o exemplo que aparece no tutorial oficial 4 do RabbitMQ
> (["Routing"](https://www.rabbitmq.com/tutorials/tutorial-four-java)).
> Se for procurar referência externa, esse é o nome.

### Cenário 2: worker pool com 1 fila

Variante extrema: **uma única rk, uma única fila, vários consumers**.

```
publish(rk="task") ────► direct.exchange ──rk="task"──► queue.tasks ──┬─► worker1
                                                                       ├─► worker2
                                                                       └─► worker3
```

Aqui a direct exchange está fazendo um trabalho que `default exchange`
(ver §6) também faria. A vantagem de declarar uma direct explícita: você
pode adicionar uma segunda rk depois (`task.priority`, p.ex.) sem mudar a
fila base.

Distribuição entre workers é responsabilidade da fila (round-robin com
`prefetch=1`), **não da exchange** — direct copia pra fila, fila distribui
pros consumers. Mesma semântica de qualquer outro tipo de exchange.

---

## Múltiplos bindings (uma fila, várias rks)

Padrão muito usado em direct. Em Spring AMQP:

```java
@Bean
public Queue diskQueue() {
    return new Queue("queue.disk", true);
}

@Bean
public Binding diskBindWarn(Queue diskQueue, DirectExchange logExchange) {
    return BindingBuilder.bind(diskQueue).to(logExchange).with("warn");
}

@Bean
public Binding diskBindError(Queue diskQueue, DirectExchange logExchange) {
    return BindingBuilder.bind(diskQueue).to(logExchange).with("error");
}
```

Dois `@Bean Binding` separados, mesma fila destino, rks diferentes.
Spring AMQP aceita N bindings com a mesma fila — o broker grava cada um
como uma entrada distinta na tabela de bindings.

> No painel Mgmt UI → aba **Queues** → clique na fila → seção **Bindings**.
> Cada rk aparece como uma linha. Útil pra confirmar que todos os bindings
> esperados estão de pé depois do startup.

---

## Múltiplas filas, mesma routing key (fan-out controlado)

O caso oposto: você quer que **N filas independentes** recebam toda
mensagem de uma certa rk.

```java
@Bean
public Binding slackBind(Queue slack, DirectExchange logEx) {
    return BindingBuilder.bind(slack).to(logEx).with("error");
}

@Bean
public Binding elasticBindError(Queue elastic, DirectExchange logEx) {
    return BindingBuilder.bind(elastic).to(logEx).with("error");
}
```

Publish com `rk="error"` entrega cópia em **`slack` e `elastic`**. Tipo
fanout, mas restrito a uma rk específica. É como falar "broadcast, mas só
pra quem se inscreveu nessa rk".

Quando esse padrão começa a ficar incômodo (muitas rks compartilhando
mesmo conjunto de filas), é sinal de que **topic** com curingas pode
simplificar.

---

## Comparação rápida com topic

| Aspecto | direct | topic |
|---|---|---|
| Match | igualdade exata | padrão com `*` (1 palavra) e `#` (N palavras) |
| Custo de routing | hash map lookup — O(1) | match de padrão — O(N bindings) |
| Flexibilidade pra novos consumers | precisa adicionar binding explícito por rk | binding `dominio.#` pega todas as ações futuras |
| Legibilidade dos bindings | cada rk listada — explícito | padrão resume várias rks num binding |
| Custo de evolução | alto: nova rk = revisar todos os consumers | baixo: consumer com wildcard absorve sozinho |

Regra prática:

- **Rotas conhecidas, fixas, poucas (3–6) → direct.**
- **Rotas com hierarquia (`dominio.acao`, `regiao.tipo.id`) e expectativa
  de evolução → topic.**

O custo de routing quase nunca importa em prática (RabbitMQ otimiza ambos),
mas o custo de evolução é real.

---

## A default exchange: caso especial

Todo broker RabbitMQ tem uma exchange embutida chamada `""` (string vazia).
Ela é uma **direct exchange especial** com uma regra implícita: **toda
fila do broker está automaticamente bindada nela com routing key igual
ao nome da fila**.

Isso explica o atalho:

```java
rabbitTemplate.convertAndSend("queue.tasks", payload);
//                            ^^^^^^^^^^^^^
//                            isso aqui é "routing key", não "exchange name"
```

Não tem versão de 2 argumentos com exchange — esse one-arg overload
publica na **default exchange** com rk igual ao nome da fila. Funciona
por causa do binding automático.

Pontos a ter em mente:

- **Você não declara, não pode redeclarar e não pode remover** a default
  exchange. Existe e ponto.
- Publicar nela ignora qualquer routing customizado — vai **literal** pra
  fila com aquele nome (ou em lugar nenhum, se a fila não existir).
- Em sistemas event-driven a default exchange é uma armadilha: acopla
  publisher ao **nome da fila**, em vez de à exchange/rk. Mudou nome da
  fila? Quebrou o publisher. Por isso o projeto **nunca** publica na
  default exchange — sempre via `RabbitMQConfig.EXCHANGE` explícita (ver
  [`publisher.md`](../publisher.md)).

---

## Quando usar direct

Use direct quando:

1. **As rotas são conhecidas e estáticas.** Você consegue listar todas
   num enum. Ninguém vai inventar uma rk nova em runtime.
2. **Não tem hierarquia natural na rk.** As rks são "atômicas"
   (`error`, `info`) e não decompostas (`log.app.error`).
3. **Quer pegar erro de typo na inicialização.** Binding com rk errada em
   direct **simplesmente nunca casa** — bug silencioso. Em topic, `*` ou
   `#` mal colocado pode mascarar o problema. (Argumento fraco mas real.)
4. **Carga é gigantesca e routing custo importa.** Cenário muito raro:
   milhões de msg/s onde a economia de microssegundos por publish vira
   relevante. Em prática nunca aparece.

**Não use direct quando:**

- A rk tem hierarquia óbvia (`dominio.acao`) → topic é mais expressivo.
- Existem múltiplos "perfis de inscrição" (um consumer quer tudo, outro
  só uma fatia) → topic deixa os bindings mais curtos.
- Você ainda não sabe quais consumers vão existir → topic com
  `dominio.#` deixa porta aberta sem custo.

---

## Declaração no Spring AMQP

```java
@Configuration
public class LogExchangeConfig {

    public static final String EXCHANGE = "log.direct";

    @Bean
    public DirectExchange logExchange() {
        return new DirectExchange(EXCHANGE);          // durable=true, autoDelete=false (defaults)
    }

    @Bean
    public Queue errorQueue() {
        return new Queue("queue.error", true);
    }

    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange logExchange) {
        return BindingBuilder.bind(errorQueue).to(logExchange).with("error");
    }
}
```

Variantes:

- `new DirectExchange(name, durable, autoDelete)` — controle explícito.
  `durable=true` (default) sobrevive a restart; `autoDelete=true` apaga
  quando o último binding sumir (raro fora de testes).
- `new DirectExchange(name, durable, autoDelete, arguments)` — args como
  `alternate-exchange` (pra capturar mensagens não-roteadas). Avançado;
  não usado no projeto.

Publish em direct exchange:

```java
rabbitTemplate.convertAndSend("log.direct", "error", payload);
//                            ^exchange      ^rk      ^payload
```

Idêntico ao publish em topic — o tipo da exchange é transparente do lado
do publisher. Quem decide a regra de roteamento é o broker, a partir do
tipo da exchange declarada.

---

## Inspecionar

### Listar exchanges (e ver o tipo)

```powershell
docker exec rabbitmq rabbitmqctl list_exchanges name type durable
```

Exchanges built-in que aparecem por default:

```
(AMQP default)     direct   true        ← a default exchange ""
amq.direct         direct   true        ← direct genérica, sempre presente
amq.topic          topic    true
amq.fanout         fanout   true
amq.headers        headers  true
amq.match          headers  true        ← alias de amq.headers
```

`amq.direct` está sempre lá e pode ser usada sem declarar — mas convenção
sólida é **declarar a sua própria**, com nome de domínio. Usar `amq.direct`
mistura tráfego de aplicações diferentes na mesma exchange.

### Listar bindings de uma direct

```powershell
docker exec rabbitmq rabbitmqctl list_bindings source_name routing_key destination_name
```

Filtre mentalmente pela exchange que interessa. Em direct, cada linha é
uma rk literal — sem `*` ou `#`.

### Testar uma rota sem subir app

No painel Mgmt UI (`http://localhost:15672`) → **Exchanges** → clica na
direct → seção **Publish message**. Preenche routing key e payload, manda.
A fila que tinha binding pra essa rk recebe na hora — bom pra confirmar
topologia sem precisar publish real do app.

---

## Por que o projeto não usa direct

Resumo da decisão (detalhada em [`../exchange.md`](../exchange.md)):

- Os eventos do projeto têm **hierarquia natural**: `todo.created`,
  `todo.updated`, `todo.deleted`. Estrutura `dominio.acao` casa com
  routing key de topic.
- O `audit-service` se inscreve em **todos os eventos do domínio** via
  binding `todo.#`. Em direct, isso exigiria 3 bindings explícitos hoje
  e mais um a cada nova ação no futuro — sempre lembrando de adicionar.
- O `notification-service` se inscreve nas 3 rks exatas. Em direct,
  esse lado funcionaria igual a topic — não há ganho nem perda.

Resultado: topic atende os dois consumers com a mesma exchange, sem
amarrar o audit a uma lista que pode envelhecer. Direct teria forçado um
trade-off no audit que não vale a pena.

> Se o projeto **não tivesse audit-service** (só notification, com 3 rks
> exatas), direct seria uma escolha perfeitamente válida — talvez até
> mais legível, porque listaria literalmente as rotas válidas no
> `RabbitMQConfig`.

---

## Resumo

- **Direct = match exato** entre routing key da msg e routing key do
  binding. Sem curinga.
- Uma fila pode ter **N bindings** (uma por rk que ela aceita).
- Várias filas podem ter bindings **com a mesma rk** — todas recebem cópia.
- A **default exchange** (`""`) é uma direct especial: binding implícito
  `nome-da-fila = routing-key`. Atalho útil em scripts; armadilha em apps
  reais.
- Boa pra rotas estáticas e pequenas; ruim pra evolução. Quando a rk
  tem hierarquia, prefira **topic** (ver [`topic.md`](./topic.md)).
- O projeto não usa direct — escolheu topic pra acomodar o
  `audit-service` com binding `todo.#`. Ver [`../exchange.md`](../exchange.md).

Links cruzados:
- [`../exchange.md`](../exchange.md) — visão geral de exchanges do projeto.
- [`./topic.md`](./topic.md) — o tipo que o projeto usa.
- [`./fanout.md`](./fanout.md) — broadcast cego.
- [`./headers.md`](./headers.md) — roteamento por headers AMQP.
- [`../filas.md`](../filas.md) — filas concretas e seus bindings.
