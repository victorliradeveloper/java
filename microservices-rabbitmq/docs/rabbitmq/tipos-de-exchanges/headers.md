# Headers Exchange

## Visão Geral

A **headers exchange** ignora a routing key e roteia mensagens
comparando **headers AMQP** da mensagem contra **headers declarados no
binding**. É o único tipo de exchange que olha **dentro** do envelope
AMQP em vez de só pra rk.

```
                            ┌─────────────────────────────────┐
publish(rk="ignored")       │   exchange (headers)            │
headers={                   │                                 │
  "format": "pdf",   ──────►│  binding match={format=pdf,     │
  "size":   "large", │      │                  size=large} ✓  │──► queue.pdf-large
  "lang":   "pt"     │      │                                 │
}                           │  binding match={format=jpg}  ✗  │
                            │  binding match={lang=pt}     ✓* │──► queue.pt-only
                            └─────────────────────────────────┘
                                                          *se x-match=any
```

Cada binding em headers exchange carrega um **mapa de headers
esperados** + um modificador `x-match` que define se **todos** os
headers do binding precisam casar (`all`) ou **qualquer um** deles
(`any`).

> O projeto **não usa** headers exchange — usa topic em todas as três
> exchanges. Headers é o tipo mais raro em sistemas reais e quase sempre
> pode ser substituído por topic com routing key composta. Detalhes do
> porquê em "Por que headers é raro" e "Por que o projeto não usa".
> Vale conhecer pra reconhecer quando é o tipo certo — e principalmente
> pra reconhecer quando *não* é.

---

## A regra de roteamento, formalmente

Quando uma mensagem chega numa headers exchange `E`:

1. Broker pega os **headers AMQP** da mensagem (não o body — só os
   headers que viajam no envelope, como `content-type`, `priority`, e
   qualquer header customizado que o publisher tenha setado).
2. Pra cada binding na exchange, lê o **mapa de headers esperados** e o
   modificador **`x-match`** (argumento do binding).
3. Se `x-match=all`: a mensagem casa só se **todos** os headers do
   binding estiverem na mensagem com **os valores exatos**.
4. Se `x-match=any`: a mensagem casa se **pelo menos um** header bater.
5. Pra cada binding que case, copia a mensagem na fila destino.

Pontos não-óbvios:

- **Routing key da mensagem é ignorada**, igual fanout. Pode publicar
  com rk vazia. (Boa prática: ainda preencher pra log/inspeção.)
- **Headers que existem na mensagem mas não no binding são irrelevantes**
  pro match. Eles continuam na mensagem (consumer recebe), só não
  participam da decisão de roteamento.
- **Headers que existem no binding mas não na mensagem fazem o match
  falhar em `x-match=all`.** O publisher precisa garantir que todos os
  headers esperados estão presentes.
- **`x-match` é o único header do binding que não conta como critério** —
  ele é a **regra do match**, não um header esperado.
- **Comparação de valores é por igualdade**: sem regex, sem wildcard,
  sem range. Igualdade estrita de string ou número.

---

## `x-match=all` vs `x-match=any`

### `all` — AND lógico (default)

Binding: `{x-match=all, format=pdf, size=large}` significa
"casa se a mensagem tiver `format=pdf` E `size=large`".

| Mensagem | Match? |
|---|---|
| `{format=pdf, size=large}` | ✓ |
| `{format=pdf, size=large, lang=pt}` | ✓ (header extra ok) |
| `{format=pdf}` | ✗ (falta `size`) |
| `{format=pdf, size=small}` | ✗ (`size` errado) |
| `{format=jpg, size=large}` | ✗ (`format` errado) |

### `any` — OR lógico

Mesmo binding com `x-match=any`: "casa se a mensagem tiver `format=pdf`
OU `size=large`".

| Mensagem | Match? |
|---|---|
| `{format=pdf, size=large}` | ✓ |
| `{format=pdf, size=small}` | ✓ (um casou) |
| `{format=jpg, size=large}` | ✓ (um casou) |
| `{format=jpg, size=small}` | ✗ (nenhum) |
| `{lang=pt}` | ✗ (nenhum header relevante) |

`any` raramente é a escolha certa: torna o binding pouco específico e
geralmente alguém quer "esses dois critérios precisam estar lá", não
"qualquer um". Use `all` por default.

---

## Cenário típico: roteamento multi-dimensional

O ponto onde headers brilha é quando o roteamento precisa combinar
**várias dimensões independentes**. Exemplo: pipeline de processamento
de documentos onde cada documento tem `formato`, `tamanho`, `região`:

```
publish(
  headers={ format=pdf, size=large, region=eu },
  payload=<documento>
)

      │
      ▼
┌──────────────────────────────────────────────────────┐
│ docs.headers (headers exchange)                      │
│                                                      │
│  binding {x-match=all, format=pdf, size=large}    ✓  │──► queue.heavy-pdf
│  binding {x-match=all, format=pdf, region=eu}     ✓  │──► queue.eu-pdf
│  binding {x-match=all, format=jpg}                ✗  │
│  binding {x-match=any, region=eu, region=us}      ✓  │──► queue.americas-or-eu
└──────────────────────────────────────────────────────┘
```

Tentar fazer isso com topic exigiria **rks como `pdf.large.eu`** e
bindings com curinga (`pdf.*.eu`, `pdf.large.*`). Funciona, mas amarra
a ordem dos campos na rk e o número de dimensões fica fixo na convenção.
Adicionar `lang` no meio implica refactor de todas as rks publicadas e
de todos os bindings.

Em headers, as dimensões são **independentes** e podem ser
adicionadas/removidas sem mexer no que já existe.

**Mas leia "Por que headers é raro" antes de adotar.** O exemplo acima é
o caso de uso ideal — e mesmo assim, topic com convenção bem desenhada
geralmente resolve.

---

## Por que headers é raro

A maioria dos sistemas que considera headers acaba escolhendo topic.
Razões:

1. **Topic com rk composta resolve 90% dos casos.** `pdf.large.eu`
   funciona; o overhead "ordem fixa" raramente é problema real porque
   na prática a hierarquia é estável.
2. **Headers são menos visíveis.** Routing key aparece em todo lugar —
   logs, painel UI, `rabbitmqctl list_bindings`, `x-death`. Headers
   exigem inspeção do envelope inteiro — diagnóstico mais chato.
3. **Performance pior.** Headers exchange precisa iterar todos os
   bindings e comparar maps inteiros; topic usa trie otimizada.
   Diferença ínfima em escala pequena, mas real em milhares de bindings.
4. **Idiomas/clients diferentes lidam pior com headers.** Routing key
   é uma string — universal. Headers customizados às vezes esbarram em
   nomenclatura case-sensitive, tipos (`String` vs `Long` no header
   vira bug silencioso de mismatch), e clients que não anexam todos os
   headers automaticamente.
5. **`x-match=all` raramente é o que se quer com muitos headers.** Quanto
   mais headers no binding, mais restritivo — chega num ponto em que só
   uma combinação específica casa, e aí você poderia ter usado direct
   com uma rk única.

A regra prática: **se você está pensando em headers, faça primeiro o
desenho com topic**. Se topic exige hierarquia rígida que vai amarrar
demais a evolução do schema de eventos, aí headers pode valer.

---

## Comparação 4 tipos

| Aspecto | headers | topic | direct | fanout |
|---|---|---|---|---|
| O que decide o roteamento | match em headers AMQP | padrão de rk com curinga | rk exata | nada (broadcast) |
| Custo de routing | O(N) bindings, comparação de map | O(log N) trie | O(1) hash lookup | O(1) por fila |
| Dimensões independentes | **suporta nativamente** | precisa codificar na rk | não suporta | não aplicável |
| Visibilidade do critério | só no envelope (chato) | rk aparece em log/UI | rk literal | n/a |
| Quando faz sentido | múltiplas dimensões ortogonais | hierarquia evolutiva | rotas fixas | broadcast cego |

A coluna que justifica headers existir é "dimensões independentes" — em
todos os outros aspectos, ele perde pra topic.

---

## Quando usar headers

Use headers quando **todas** as condições abaixo forem verdadeiras:

1. **O roteamento depende de múltiplas dimensões ortogonais.**
   `format` × `size` × `region`, sem hierarquia natural entre elas.
2. **A ordem das dimensões nem pode ser fixada.** Se hoje você roteia
   por `format` primeiro mas amanhã pode ser `region`, topic com rk
   composta fica frágil.
3. **Adicionar uma dimensão nova é frequente.** Em headers, é setar um
   header novo e bindar quem quiser — sem quebrar bindings antigos.
   Em topic com rk composta, adicionar dimensão = refactor de todas
   as rks.
4. **Você consegue garantir que o publisher seta todos os headers
   relevantes.** Header faltando em mensagem + `x-match=all` no binding
   = match falha silenciosamente. Em prod, isso é bug difícil.

Se faltar **uma** dessas, topic é mais simples e mais legível.

**Não use headers quando:**

- A rk teria hierarquia natural (`dominio.acao`) → topic.
- O critério é "tudo" → fanout.
- O critério é "rk exata específica" → direct.
- Você não consegue garantir que publisher anexa headers consistentes
  → topic pelo menos te dá feedback visível (rk errada não casa nada).

---

## Declaração no Spring AMQP

```java
@Configuration
public class DocumentRoutingConfig {

    public static final String EXCHANGE = "docs.headers";

    @Bean
    public HeadersExchange docsExchange() {
        return new HeadersExchange(EXCHANGE);    // durable=true, autoDelete=false
    }

    @Bean
    public Queue heavyPdfQueue() {
        return new Queue("queue.heavy-pdf", true);
    }

    /**
     * x-match=all (default no whereAll) — todos os headers precisam casar.
     */
    @Bean
    public Binding heavyPdfBinding(Queue heavyPdfQueue, HeadersExchange docsExchange) {
        return BindingBuilder.bind(heavyPdfQueue)
                .to(docsExchange)
                .whereAll(Map.of(
                        "format", "pdf",
                        "size",   "large"
                ))
                .match();
    }

    /**
     * x-match=any — qualquer um dos headers casa.
     */
    @Bean
    public Binding americasOrEuBinding(Queue regionQueue, HeadersExchange docsExchange) {
        return BindingBuilder.bind(regionQueue)
                .to(docsExchange)
                .whereAny(Map.of(
                        "region", "eu",
                        "region", "us"            // note: chave duplicada não funciona em Map.of
                ))
                .match();
        // Pra "region in (eu, us)" o jeito real é um binding por valor.
    }
}
```

Detalhes importantes:

- **`.whereAll(map).match()`** equivale a `x-match=all` no binding.
  **`.whereAny(map).match()`** equivale a `x-match=any`.
- O **`.match()`** no final é obrigatório — termina o builder e
  produz o `Binding`.
- **`Map.of` não aceita chaves duplicadas** — o exemplo "region in
  (eu, us)" precisa ser **dois bindings separados**, um por valor. Headers
  não tem operador `in`.
- **Tipos do header importam**: `whereAll(Map.of("priority", 5))` (Integer)
  não casa com mensagem que tem `priority=5` como String. O publisher
  precisa setar o mesmo tipo.
- O `HeadersExchange` aceita `durable`, `autoDelete` e `arguments` igual
  aos outros tipos.

Publicar uma mensagem com headers customizados:

```java
rabbitTemplate.convertAndSend("docs.headers", "", payload, msg -> {
    msg.getMessageProperties().setHeader("format", "pdf");
    msg.getMessageProperties().setHeader("size",   "large");
    msg.getMessageProperties().setHeader("region", "eu");
    return msg;
});
```

A rk (`""`) é ignorada. O post-processor seta os headers que o broker
vai consultar no roteamento.

---

## Inspecionar

### Listar exchanges headers

```powershell
docker exec rabbitmq rabbitmqctl list_exchanges name type | Select-String "headers"
```

Em ambiente saudável aparecem (no mínimo):

```
amq.headers    headers   ← built-in
amq.match      headers   ← alias de amq.headers, também built-in
```

Quando você declara as suas, aparecem aqui também.

### Listar bindings com seus argumentos

`rabbitmqctl list_bindings` mostra rk vazia em headers exchange — o que
importa são os **arguments**. Eles aparecem na coluna `arguments`:

```powershell
docker exec rabbitmq rabbitmqctl list_bindings source_name destination_name arguments
```

Saída esperada (formato simplificado):

```
docs.headers   queue.heavy-pdf   [{<<"x-match">>,<<"all">>},{<<"format">>,<<"pdf">>},{<<"size">>,<<"large">>}]
```

A formatação é Erlang-style (`<<"...">>` são binaries) — feia, mas
informativa. No painel UI fica mais legível.

### Painel Mgmt UI

`http://localhost:15672` → **Exchanges** → clica na headers exchange.

- Seção **Bindings** mostra os argumentos numa tabela limpa: coluna
  "Arguments" com `x-match=all` ou `=any` + cada header esperado.
- Seção **Publish message** permite testar: campo "Headers" aceita
  pares `key=value` linha por linha. Útil pra confirmar match sem
  subir o publisher.

### Diagnóstico de "mensagem não chega"

Em headers, esse é o sintoma mais comum e mais chato. Checklist:

1. **Olhe os headers que a mensagem realmente tem** via painel UI →
   `Get messages` na fila origem ou DLQ. Confira nomes (case-sensitive)
   e tipos (`String` vs `Integer`).
2. **Compare com o binding** na exchange — todos os headers do binding
   estão na mensagem com os mesmos tipos e valores?
3. **`x-match=all` é estrito demais?** Mude pra `any` temporariamente e
   veja se passa a casar — confirma que o problema é uma chave
   específica.

Esse loop de diagnóstico é o motivo de muita gente preferir topic: rk
errada aparece literal no log, headers errados exigem inspeção do
envelope.

---

## Por que o projeto não usa headers

Análise da topologia do projeto vs. critérios de "quando usar":

| Critério | Atende? |
|---|---|
| Múltiplas dimensões ortogonais no roteamento? | **Não** — só `action` (created/updated/deleted) |
| Ordem das dimensões não pode ser fixada? | **Não** — só tem uma dimensão |
| Adicionar dimensão é frequente? | **Não** — `todo.{action}` é estável |
| Publisher garante headers consistentes? | Sim, mas não há demanda |

Headers seria over-engineering total aqui. Os eventos têm **uma única
dimensão de roteamento** (qual a ação), e topic com `todo.{action}` +
binding `todo.#` no audit resolve com 4 linhas de config.

> Headers só faria sentido no projeto se eventos passassem a ter
> **múltiplas dimensões independentes** — por exemplo, se o audit
> tivesse subníveis tipo "alto valor" vs "baixo valor" *ortogonal* à
> ação. Aí `{action=created, importance=high}` casado por headers seria
> mais limpo que uma rk hierárquica `todo.created.high` (que amarra a
> ordem e dificulta consumers que só ligam pra um dos eixos). Hoje, não
> existe essa segunda dimensão — então topic basta.

---

## Resumo

- **Headers = match em headers AMQP, rk ignorada.** Único tipo de
  exchange que não usa rk.
- **`x-match=all`** (AND lógico, default) ou **`x-match=any`** (OR
  lógico) no binding.
- Comparação é **igualdade estrita** (nome, valor, tipo). Sem regex,
  sem range, sem wildcard.
- **Caso ideal**: múltiplas dimensões **independentes** sem hierarquia
  natural — ex.: `format` × `size` × `region` num pipeline de
  documentos.
- **Raro em prática**: topic com rk composta resolve a maioria dos
  casos com mais visibilidade e performance.
- **Pitfalls**: tipos divergentes em headers (Integer vs String),
  ausência silenciosa de header faz `x-match=all` falhar, diagnóstico
  exige inspecionar envelope.
- `amq.headers` / `amq.match` embutidas existem — não use em produção,
  declare a sua.
- Projeto não usa headers porque eventos têm **uma única dimensão de
  roteamento** (`action`) — topic resolve melhor.

Links cruzados:
- [`../exchange.md`](../exchange.md) — visão geral das exchanges do projeto.
- [`./topic.md`](./topic.md) — o tipo que o projeto usa, com curingas
  no lugar de match em headers.
- [`./direct.md`](./direct.md) — match exato sem hierarquia.
- [`./fanout.md`](./fanout.md) — broadcast cego.
- [`../filas.md`](../filas.md) — filas concretas e seus bindings.
