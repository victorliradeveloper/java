# Fanout Exchange

## Visão Geral

A **fanout exchange** é o tipo mais simples do AMQP: ela **ignora
completamente a routing key** e entrega uma cópia da mensagem **para
todas as filas bindadas nela**. Sem regra de match, sem padrão, sem
filtro. Se a fila estiver bindada, recebe.

```
                         ┌──────────────────────────┐
publish(rk=qualquer) ───►│   exchange (fanout)      │──► queue A
                         │   rk é ignorada          │──► queue B
                         │                          │──► queue C
                         │   binding rk = irrelevant│──► queue D
                         └──────────────────────────┘
```

É o **broadcast cego** do RabbitMQ: a exchange não faz pergunta nenhuma,
só multiplica. A routing key continua viajando junto da mensagem (o
consumer pode lê-la se quiser), mas o broker não a consulta pra rotear.

> O projeto atual não usa fanout — usa **topic** em todas as três
> exchanges. Detalhes do porquê em "Por que o projeto não usa fanout".
> Vale conhecer o tipo: muito sistema real usa fanout pra um problema
> específico (cache invalidation, broadcast em cluster), e a confusão
> entre fanout e "pub/sub em topic com `#`" é frequente.

---

## A regra de roteamento, formalmente

Quando uma mensagem chega numa fanout exchange `E`:

1. Broker pega a lista de **todas as filas bindadas** em `E`.
2. Pra **cada** uma, copia a mensagem.
3. Routing key da mensagem **nem é olhada**.
4. Routing key do binding **nem é olhada** (você pode passar string vazia
   ou qualquer coisa — broker ignora).

Pontos não-óbvios:

- **Routing key continua na mensagem.** O broker preserva no header AMQP;
  o consumer recebe normalmente via `MessageProperties.getReceivedRoutingKey()`.
  Apenas o roteamento ignora — o conteúdo do envelope, não.
- **Filtragem é responsabilidade do consumer.** Se você quer que só
  parte das mensagens importem, lê a rk (ou um campo do body) e descarta
  no código. **O broker não filtra fanout.** Isso muda profundamente
  como você desenha consumers.
- **Adicionar consumer = declarar fila + binding.** Zero coordenação com
  publisher. Esse é o ponto forte: fan-out elástico, sem registry, sem
  service discovery.
- **Remover consumer = parar de declarar a fila.** Se a fila era
  `auto-delete`, some quando o último consumer desconecta. Se era
  durable, fica órfã acumulando mensagens — preste atenção.

---

## Cenários típicos

### Cenário 1: cache invalidation em cluster (o caso clássico)

Você tem N instâncias de uma aplicação, cada uma com cache local
(in-memory). Quando o dado X muda em qualquer instância, **todas** as
outras precisam invalidar o cache delas. Solução clássica:

```
                                  ┌───────────────────┐
instance A (app:1)  ──invalidate──►│                  │──► queue.cache.A
                                  │   cache.fanout   │──► queue.cache.B
instance B (app:2)  ──invalidate──►│                  │──► queue.cache.C
                                  │                  │──► queue.cache.D
instance D (app:4)  ──invalidate──►│                  │
                                  └───────────────────┘
```

Cada instância declara **sua própria fila** (`auto-delete=true`,
`exclusive=true`, nome único gerado em runtime). Quando publica uma
invalidação, ela mesma recebe também (fanout não tem self-exclusion),
e o consumer descarta no código se reconhecer o `instance-id` do header
como o próprio.

Por que fanout aqui:

- **Número de instâncias é dinâmico** — escala horizontal, deploy
  rolling. Não dá pra listar bindings num arquivo de config.
- **Não precisa filtrar no broker** — toda mensagem é relevante pra todo
  mundo (com exceção da self-loop, resolvida no código).
- **Latência baixa** — broker não calcula nada, só copia. Direct também
  seria rápido, mas exigiria binding explícito por instância.

### Cenário 2: notificação em tempo real pra múltiplos subsistemas

Um evento de negócio precisa chegar em vários sistemas heterogêneos ao
mesmo tempo: dashboard real-time, sistema de email, sistema de analytics,
sistema de auditoria. Cada um lê do seu próprio jeito.

```
                                    ┌────────────────┐
publish(order.created) ────────────►│                │──► queue.dashboard.ws
                                    │                │──► queue.email.notif
                                    │ events.fanout  │──► queue.analytics.stream
                                    │                │──► queue.audit.log
                                    └────────────────┘
```

> ⚠️ Aqui mora um **anti-padrão comum**: usar fanout quando topic
> resolveria. Se o "dashboard" só quer eventos de `order.*` e o "email"
> só quer `order.created` e `order.canceled`, **topic é melhor** — o
> broker filtra; cada consumer só recebe o que importa. Fanout aqui
> obrigaria cada consumer a receber tudo e descartar 90% — desperdício
> de banda e CPU. Use fanout só quando **todo consumer realmente quer
> tudo**.

### Cenário 3: notificação de mudança de configuração

Centralizada de feature flags muda → todas as instâncias precisam
recarregar:

```
config-service ──publish──► config.fanout ──► queue.config.<host1>
                                            ──► queue.config.<host2>
                                            ──► queue.config.<hostN>
```

Mesmo padrão do cenário 1: filas curtas, efêmeras, criadas em runtime
pelo próprio consumer. O publisher (`config-service`) não precisa saber
quantos consumers existem.

---

## Routing key em fanout: ignorada, mas presente

Confusão recorrente: "se fanout ignora routing key, posso publicar com
rk vazia?". Pode — mas convenção de produção é **sempre publicar com uma
rk significativa** mesmo em fanout.

Motivos:

1. **Inspeção e log**: o `x-death` da DLQ, os logs do broker, os tools
   de monitoring (DataDog, etc.) todos mostram a rk. Vazio vira ruído.
2. **Consumer pode usar pra lógica**: se um consumer precisa filtrar
   localmente (mesmo cenário do "anti-padrão" acima), a rk é um campo
   barato de ler.
3. **Refactor futuro pra topic**: se um dia trocar a fanout por uma
   topic exchange, mensagens já publicadas com rk vazia ficam difíceis
   de roteamento retroativo. Manter rks "futuro-friendly" não custa.

Exemplo:

```java
rabbitTemplate.convertAndSend("cache.fanout", "user.invalidate", payload);
//                                            ^^^^^^^^^^^^^^^^^
//                              fanout ignora, mas vai aparecer em log/header
```

---

## Comparação rápida com os outros tipos

| Aspecto | fanout | direct | topic | headers |
|---|---|---|---|---|
| O que decide o roteamento | nada (broadcast) | rk exata | padrão de rk com curinga | match em headers AMQP |
| Custo de routing | O(1) por fila bindada | O(1) lookup | O(N) bindings | O(N) bindings |
| Permite filtrar no broker | **não** | sim (rk exata) | sim (padrão) | sim (headers) |
| Quem decide consumers | qualquer fila bindada | publisher+binding | publisher+binding | publisher+headers |
| Caso ideal | broadcast cego | rotas fixas e poucas | hierarquia evolutiva | múltiplos critérios |

A linha **"permite filtrar no broker"** é o que mais separa fanout dos
outros: você ganha simplicidade radical, paga com a impossibilidade de
deixar o broker descartar mensagens.

---

## Quando usar fanout

Use quando **todas as condições** abaixo forem verdadeiras:

1. **Todo consumer bindado realmente quer toda mensagem.** Não há
   filtragem necessária.
2. **O número de consumers é dinâmico ou desconhecido.** Você não tem
   binding explícito por consumer; cada instância declara sua fila ao
   subir.
3. **Não há hierarquia ou categorização útil no evento.** Não dá pra
   dizer "esses consumers só querem `user.*`" — todos querem tudo.

Se **alguma** das três falhar, **topic** (ou às vezes direct) atende melhor.

**Não use fanout quando:**

- Algum consumer só quer parte das mensagens → topic com `#`/`*` filtra
  no broker, fanout obrigaria filtrar no código.
- Os destinos são fixos e listáveis (`slack`, `email`, `disk`) → direct
  é mais explícito; cada destino aparece no binding.
- Você precisa garantir que mensagem só vá pra **uma** fila (load
  balancing) → fanout é o **oposto** disso; manda pra todas.

---

## Declaração no Spring AMQP

```java
@Configuration
public class CacheInvalidationConfig {

    public static final String EXCHANGE = "cache.fanout";

    @Bean
    public FanoutExchange cacheExchange() {
        return new FanoutExchange(EXCHANGE);     // durable=true, autoDelete=false (defaults)
    }

    /**
     * Fila por instância: nome único gerado em runtime, exclusive (só esta conexão)
     * e auto-delete (some quando a conexão fechar). Padrão pra cache.
     */
    @Bean
    public Queue cacheQueue() {
        return QueueBuilder.nonDurable()
                .autoDelete()
                .exclusive()
                .build();
        // sem .name(...) → broker gera "amq.gen-XXXX..."
    }

    @Bean
    public Binding cacheBinding(Queue cacheQueue, FanoutExchange cacheExchange) {
        return BindingBuilder.bind(cacheQueue).to(cacheExchange);
        //                                                       ^
        //                                                       sem .with(...)
        //                                                       — fanout ignora rk
    }
}
```

Detalhes importantes:

- **`bind(...).to(fanoutExchange)`** não tem `.with(rk)` — o builder
  reconhece o tipo `FanoutExchange` e não pede routing key. Se você
  passar uma string mesmo assim (via `BindingBuilder.bind(q).to(ex).with("foo")`),
  o Spring aceita mas o broker ignora.
- **Filas efêmeras** (`exclusive + autoDelete`) são o padrão pra cache
  invalidation. Em outros cenários (analytics que precisa persistir
  eventos quando o consumer está fora), use filas duráveis nomeadas.
- O `FanoutExchange` herda do `AbstractExchange`, então aceita
  `durable`, `autoDelete` e `arguments` igual aos outros tipos.

Publish em fanout:

```java
rabbitTemplate.convertAndSend("cache.fanout", "user.invalidate", payload);
//                            ^exchange       ^rk (ignorada)    ^payload
```

Pode também usar a versão sem rk:

```java
rabbitTemplate.convertAndSend("cache.fanout", "", payload);
```

Funcionalmente igual. A primeira forma é melhor pelos motivos da seção
"Routing key em fanout".

---

## A `amq.fanout` embutida

Todo broker RabbitMQ tem uma fanout exchange pré-declarada chamada
`amq.fanout`. Funciona — mas convenção é **declarar a sua própria**,
com nome de domínio (`cache.fanout`, `events.broadcast`, etc.).

Por quê:

- **Isolamento de tráfego**: dois apps usando `amq.fanout` no mesmo
  cluster vão ver as mensagens uns dos outros. Caos imediato.
- **Permissões via vhost**: cada exchange própria pode ter ACL
  específica. `amq.fanout` é global.
- **Documentação no código**: ver `FanoutExchange("cache.fanout")` num
  `@Bean` é mais auto-explicativo que `convertAndSend("amq.fanout", ...)`
  espalhado.

Use `amq.fanout` só em testes rápidos ou debugging — nunca em código de
produção.

---

## Inspecionar

### Listar exchanges fanout existentes

```powershell
docker exec rabbitmq rabbitmqctl list_exchanges name type | Select-String "fanout"
```

Em projeto novo aparece só:

```
amq.fanout    fanout
```

Quando você declara as suas, aparecem aqui.

### Ver quantas filas estão bindadas numa fanout

```powershell
docker exec rabbitmq rabbitmqctl list_bindings source_name destination_name | Select-String "cache.fanout"
```

Cada linha = uma fila recebendo broadcast. Em ambientes com filas
efêmeras (cache), o número flutua conforme instâncias sobem/descem.

### Testar broadcast pelo painel

`http://localhost:15672` → **Exchanges** → clica na fanout → seção
**Publish message**. Manda mensagem com rk qualquer (ou vazia). Todas as
filas bindadas recebem na hora. Bom pra confirmar que o broadcast está
configurado direito.

### Métrica útil pra monitorar

`messages_published` na exchange vs. `messages_delivered` somado nas
filas bindadas. Se você tem 4 filas bindadas, a relação **esperada** é:

```
delivered = published * número de filas bindadas
```

Não bate? Provavelmente alguma fila descolou da exchange ou tem consumer
zerado. Em fanout esse cálculo é mais simples que em topic (onde depende
de quais bindings casam).

---

## Por que o projeto não usa fanout

Análise da topologia do projeto vs. critérios de "quando usar":

| Critério | Atende? |
|---|---|
| Todo consumer realmente quer toda mensagem? | **Não** — `notification-service` filtra por ação (created, updated, deleted) e tem 3 listeners distintos pra renderizar templates diferentes |
| Consumers dinâmicos/desconhecidos? | **Não** — são 2 consumers conhecidos: notification e audit |
| Sem hierarquia útil no evento? | **Não** — `todo.{action}` tem hierarquia clara |

Falha em todos os 3. Forçar fanout exigiria:

- **No notification**: receber todas as mensagens numa única fila e
  fazer `switch(event.action())` no listener pra escolher o template.
  Funciona, mas concentra lógica de roteamento no consumer em vez de
  no broker — pior pra evoluir e pra paralelizar.
- **No audit**: nada muda — audit já recebe tudo. Não há ganho.

Topic atende os dois consumers com a mesma exchange, deixa o broker
filtrar o que vai pra cada fila e mantém os bindings autoexplicativos no
`RabbitMQConfig`. Fanout só faria sentido se o projeto fosse, p.ex., um
sistema de cache distribuído onde toda instância precisa de toda
mudança de estado — não é o caso.

> Caso real onde fanout seria a escolha certa no projeto: se houvesse
> **invalidação de cache no `todo-service`** rodando com várias
> instâncias, cada uma com cache local de `findById`. Aí uma exchange
> `todo.cache.fanout` separada faria sentido — não pra eventos de
> domínio, mas pra coordenação interna do cluster.

---

## Resumo

- **Fanout = broadcast cego.** Ignora routing key. Toda fila bindada
  recebe cópia.
- Use quando **todo consumer quer tudo** E **a quantidade de consumers
  é dinâmica**. Casos clássicos: cache invalidation, notificação de
  config, broadcast WebSocket.
- **Não use quando precisa filtrar** — fanout não filtra, e filtrar no
  código desperdiça banda e CPU. Topic resolve esse caso.
- Routing key continua na mensagem (consumer pode ler) — só o broker
  ignora ela.
- Filas efêmeras (`exclusive + autoDelete`) combinam bem com fanout em
  cenários de broadcast em cluster.
- `amq.fanout` embutida existe — não use em produção; declare a sua
  com nome de domínio.
- Projeto não usa fanout porque os consumers filtram por ação e a
  topologia é estática — topic encaixa melhor.

Links cruzados:
- [`../exchange.md`](../exchange.md) — visão geral das exchanges do projeto.
- [`./direct.md`](./direct.md) — irmã mais simples com filtragem por match exato.
- [`./topic.md`](./topic.md) — o tipo que o projeto usa.
- [`./headers.md`](./headers.md) — roteamento por headers AMQP.
- [`../filas.md`](../filas.md) — filas concretas e seus bindings.
