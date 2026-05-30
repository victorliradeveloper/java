# UI Messages (Management Console)

O bloco **Messages** na aba **Queues and Streams** mostra **quantas mensagens** estão na fila e em que ponto do ciclo de vida. É a coluna que você olha pra saber se a fila está fluindo, acumulando, ou se um consumer travou no meio do trabalho.

São 3 contadores:

| Subcoluna | O que conta |
|---|---|
| **Ready** | Mensagens **na fila esperando** entrega a um consumer |
| **Unacked** | Mensagens **entregues** a um consumer mas **ainda sem ack** |
| **Total** | `Ready + Unacked` — tudo que existe na fila agora |

Estado normal: ambos baixos. Anormalidades específicas em cada um indicam problemas diferentes — esse doc explica como ler cada cenário.

---

## Mapa do projeto

Em operação normal, com consumers vivos e SMTP saudável:

| Cenário | Ready | Unacked | Total |
|---|---|---|---|
| Pico curto de criação de todos | sobe rápido, cai rápido | ≈ 0 | flutua |
| Consumer parado, publisher ativo | sobe e fica | 0 | sobe |
| Consumer lento (ex.: SMTP demorando) | baixo | sobe e fica | sobe |
| Consumer crashou no meio | 0 | fica > 0 e depois volta pra Ready | flutua |
| Mensagem indo pra DLQ | cai em `*.queue` → sobe em `*.dlq` | 0 | constante (transferiu) |

---

## Os 3 contadores

### `Ready` — pendente de entrega

Mensagens que estão **dentro da fila**, persistidas (se durable) ou em RAM, esperando o broker entregar pra um consumer.

**O que faz subir:**
- Publisher publicando mais rápido que consumer consome
- Nenhum consumer registrado na fila (`Consumers = 0`)
- Consumers estão todos com `prefetch` cheio e não ackam

**Como ler:**
- `Ready = 0` constante → tudo flui sem acumular ✓
- `Ready` subindo monotonicamente → consumer não dá conta (ou está parado)
- `Ready` sobe em pico mas cai em segundos → carga normal, sistema absorve

**No projeto, exemplo real:** quando o `notification-service` rodava na IDE com SMTP quebrado, o evento publicado pelo todo-service apareceu como `Ready=1` em `todo.created.queue` — entregue ao broker, mas o consumer rejeitava por falha de SMTP e a mensagem voltava pra fila (até a DLX absorver via retry esgotado).

---

### `Unacked` — entregue, esperando ack

Mensagens que o broker **já entregou ao consumer** mas o consumer ainda **não confirmou** (`basic.ack`). Elas contam contra o `prefetch` do consumer e ficam "reservadas" — nenhum outro consumer recebe a mesma.

**O que faz subir:**
- Consumer demora pra processar (latência alta no `doWork`)
- Consumer está com prefetch alto e processando em série
- Consumer travou (deadlock, espera infinita)
- Consumer foi pausado em breakpoint do debugger (clássico em desenvolvimento)

**O que acontece se Unacked fica preso:**
- Outras mensagens da fila **não param** — vão pra outros consumers se houver
- Se o consumer desconectar antes de ackar, mensagem **volta pra Ready** e é redelivered (RabbitMQ não perde mensagem de consumer que sumiu)
- Atenção: ela vem marcada com `redelivered=true` — código defensivo deve dedupar (no projeto, [`TodoEventListener`](../../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java) usa `processed_messages` pra isso)

**Como ler:**
- `Unacked > 0` brevemente durante processamento → normal
- `Unacked` cresce e não cai → consumer está travado ou processando muito devagar
- `Unacked` igual ao `prefetch` do consumer → consumer cheio, broker não entrega mais nada pra ele

---

### `Total` — soma simples

`Total = Ready + Unacked`. Só o agregado. Útil pra um olhar rápido sem se importar onde a mensagem está.

> No UI o Total às vezes inclui mensagens em outros estados internos (entregues mas não vistas, em transit), mas pra fins práticos é `Ready + Unacked`.

---

## Relação com `prefetch`

O `prefetch` (configurado via `basic.qos`) é **quantas mensagens não-ackadas o broker manda pra um consumer antes de parar**. É o teto do `Unacked` por consumer.

Spring AMQP default: `prefetch = 250` em listener simple, mas pra workloads transacionais o ajuste comum é `1` ou `5` — você quer que o broker distribua mensagens entre consumers em vez de um pegar 250 e os outros ficarem ociosos.

**Cenários:**

| prefetch | Comportamento |
|---|---|
| `1` | Consumer pega 1, processa, acka, pega próxima. Distribuição perfeita, latência maior. |
| `10-50` | Bom equilíbrio pra workload com I/O. Default sensato. |
| `250+` | Throughput máximo, mas distribuição ruim — um consumer "açambarca" todas as mensagens. |

**Como aparece no UI:** se o consumer está com `prefetch=10` e a fila tem `Unacked=10`, o broker não vai entregar mais nada pra ele até ackar alguma. Adicionar consumers é a saída.

---

## Como usar na prática

### Triagem rápida

> "Email não está chegando."

1. Olha `Ready` na fila do consumer (ex.: `todo.created.queue`).
   - `Ready > 0` crescente → consumer não está consumindo. Verifica se está vivo (`Consumers` column), se subiu sem erro.
   - `Ready ≈ 0` → broker está entregando, problema é depois.
2. Olha `Unacked`.
   - `Unacked > 0` parado → consumer recebeu mas travou. Olha logs/threaddump.
   - `Unacked` flutua → processando normalmente, talvez devagar. Mede latência do `doWork`.
3. Olha `*.dlq`.
   - `Ready > 0` na DLQ → mensagem chegou a ser processada, falhou todas as tentativas, foi parar lá. Olha o motivo no header `x-death`.

### Diagnosticar lentidão

Mensagens fluindo mas com latência maior que o esperado:
- `Unacked` sustentado alto → consumer está lento no trabalho (sem necessariamente travado). Instrumenta o `doWork` com métrica de latência.
- `Ready` oscilando subindo → publisher ganhando do consumer. Considere escalar consumer (horizontalmente) ou aumentar prefetch.

### Detectar consumer fantasma

Consumer registrado no UI mas `Unacked` cresce e nunca cai:
- Conexão TCP está viva, mas a thread do consumer travou (deadlock comum em código mal escrito)
- O broker continua entregando até atingir prefetch, depois para
- Mensagens **não voltam** pra Ready até a conexão cair ou o broker fechá-la por heartbeat timeout (default 60s)

---

## Casos especiais

### DLQ acumulando

Se `Ready` em `*.dlq` cresce, **algo está sistematicamente falhando**:
- Bug no consumer (sempre que processa mensagem X, lança exception)
- Dependência externa fora (SMTP, banco, API terceira)
- Mensagem mal-formada que estoura o deserializer

DLQ que cresce sem ninguém olhar é dívida silenciosa — em produção, alerta de `dlq.Ready > 0` é obrigatório.

### Streams (`Type = stream`)

Em fila do tipo `stream` (ver [ui-types.md](./ui-types.md)), `Ready` e `Unacked` têm semântica diferente — mensagens **não são deletadas** após ack. O contador reflete o número total no log, não "pendentes". Pra streams, métrica relevante é offset do consumer vs offset do head do log (lag).

### Mensagem "indo pra DLQ"

Quando uma mensagem é dead-lettered, ela **sai** da fila origem e **entra** na DLQ. Você vai ver:
- `todo.created.queue`: Total cai em 1
- `todo.created.dlq`: Ready sobe em 1
- Total agregado (origem + DLQ) é constante — só transferiu de lugar
