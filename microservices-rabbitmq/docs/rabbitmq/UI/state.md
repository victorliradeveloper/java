# UI State (Management Console)

A coluna **State** na aba **Queues and Streams** mostra o estado operacional da fila no momento — saúde, capacidade de aceitar mensagens, presença no cluster. É o primeiro lugar pra olhar quando uma fila parece "estranha" (mensagens parando, latência subindo, app reclamando de timeout).

Diferente do [Type](./ui-types.md) (imutável após declaração) e das [Features](./ui-features.md) (argumentos da declaração), o **State** é **dinâmico** — muda em runtime conforme o broker reage a carga, memória, disco e topologia de cluster.

---

## Mapa do projeto

Em estado normal, todas as 8 filas devem estar **`running`** (badge verde). Qualquer outra coisa em dev local é sinal pra investigar.

| Fila | State esperado |
|---|---|
| `todo.*.queue` / `.dlq` (8 filas) | `running` |

---

## Estados possíveis

### `running` 🟢

Estado saudável. A fila aceita publishes, entrega pros consumers, processa acks normalmente.

> No projeto: o que você quer ver em 100% das filas, 100% do tempo.

---

### `idle` ⚪

A fila existe e está disponível, mas **sem atividade recente**. Algumas versões/configs do UI mostram `idle` em vez de `running` pra filas sem tráfego há algum tempo.

**Não é problema** — só uma indicação visual de "tá lá, não fez nada nos últimos N segundos". Quando uma mensagem chegar ou um consumer fizer ack, volta pra `running`.

---

### `flow` 🟡

**Flow control ativado.** O broker percebeu que o publisher está mandando mais rápido do que a fila consegue processar/persistir, e está aplicando **backpressure** nas conexões publicadoras pra desacelerar.

**O que causa:**
- Consumer muito lento ou parado (mensagens acumulando)
- Disco lento na hora de persistir
- CPU saturada serializando/processando

**O que olhar:**
- Coluna `Ready` — número de mensagens esperando (alto = consumer não dá conta)
- Coluna `Unacked` — entregues mas não confirmadas (alto = consumer travou ou demorando demais)
- `incoming` vs `deliver / get` — se incoming >> deliver, sobrecarga clara

**O que fazer:**
- Subir mais consumers (escalar horizontalmente)
- Aumentar `prefetch` do consumer (se ele está ocioso entre acks)
- Investigar lentidão no `doWork` do consumer

> Em produção, ver `flow` rapidamente é normal sob pico. Ver sustentado por minutos é incidente.

---

### `blocked` 🔴

**Connection blocked por alarme do broker.** O RabbitMQ atingiu um threshold de memória ou disco e parou de aceitar novas publicações pra se proteger.

**O que causa:**
- `vm_memory_high_watermark` atingido (default: 40% da RAM)
- `disk_free_limit` atingido (default: 50MB livres ou 2x da RAM)

**Sintoma no app:** publishes ficam pendurados/timeout. Consumers continuam funcionando normalmente — o broker bloqueia só quem publica.

**O que fazer:**
- Drenar filas grandes (mais consumers, ou purge manual em DLQs antigas)
- Liberar disco do volume do RabbitMQ
- Subir os thresholds no `rabbitmq.conf` (paliativo — a causa raiz é capacidade)

---

### `blocking` 🟠

**Aviso prévio do `blocked`.** O broker está perto do limite (memória/disco) e vai bloquear em breve se a tendência continuar. Publishers ainda funcionam, mas com latência crescente.

> Trate como alerta amarelo: se tem on-call, é hora de pingar.

---

### `down` ⚫

A **fila não está disponível** no cluster. Em single-node (caso do projeto), significa que o broker caiu. Em cluster:

- **classic**: o node que hospeda o queue master está offline → a fila inteira está inacessível até ele voltar.
- **quorum**: uma das réplicas está offline, mas a fila continua operacional pelas outras (a UI mostra `down` pra essa réplica específica, não pra fila inteira).

**O que olhar:**
- `docker compose ps rabbitmq` (no projeto)
- `rabbitmqctl cluster_status` (em cluster real)

---

### `crashed` 💀

O processo Erlang da fila **crashou**. Estado raro — indica bug do broker ou estado interno corrompido.

**O que fazer:**
- Olhar `/var/log/rabbitmq/` no broker pra stack trace
- Restart do node (`docker restart rabbitmq` em dev)
- Se persistir, deletar a fila e recriar (em prod, com cuidado — drena primeiro)

---

### `minority` (só quorum) ⚫

A fila quorum **perdeu a maioria** das réplicas. Sem quórum (`N/2 + 1` réplicas vivas), a fila não pode aceitar escritas nem confirmar leituras — Raft exige maioria pra qualquer operação.

**Quando acontece:** 2 de 3 nodes do cluster caíram. A 1 réplica restante fica em `minority` esperando os pares voltarem.

**O que fazer:** trazer nodes offline de volta. Não há "force" seguro — o que Raft garante é justamente que você não pode quebrar a consistência pra "ganhar disponibilidade".

---

### `stopped` ⚫

A fila foi **explicitamente parada** via API ou ferramenta de admin (`rabbitmqctl`). Não aceita publishes nem entregas até `start`.

Raro em uso normal — geralmente aparece em migrações controladas ou debugging.

---

### `terminated` ⚫

A fila **foi deletada** ou está em processo de deleção. Você raramente vê esse estado no UI porque ele desaparece em seguida.

---

## Comparação rápida

| State | Cor | Aceita publish | Entrega ao consumer | Severidade |
|---|---|---|---|---|
| `running` | 🟢 verde | Sim | Sim | OK |
| `idle` | ⚪ branco | Sim | Sim | OK |
| `flow` | 🟡 amarelo | Sim (desacelerado) | Sim | Aviso |
| `blocking` | 🟠 laranja | Sim (perto do limite) | Sim | Alerta |
| `blocked` | 🔴 vermelho | **Não** | Sim | Incidente |
| `down` | ⚫ cinza | Não | Não | Incidente |
| `crashed` | 💀 vermelho | Não | Não | Incidente |
| `minority` | ⚫ cinza | Não | Não | Incidente (quorum) |
| `stopped` | ⚫ cinza | Não | Não | Intencional |

---

## Como usar isso na prática

**Triagem em incidente** — "publish está lento":
1. Olha a coluna State. `flow`/`blocking`/`blocked` resolve a hipótese na hora.
2. Se `running`, problema é fora do RabbitMQ (network, app, consumer).

**Monitoramento contínuo** — métricas que vale exportar pro Prometheus:
- Total de filas em cada state (ideal: 100% em `running`/`idle`)
- Alerta se qualquer fila ficar em `flow`/`blocking` por > N segundos
- Alerta imediato em `blocked`, `crashed`, `minority`

**Após restart do broker:**
- Filas `classic` durable voltam em `running` quando o node sobe
- Filas `quorum` voltam quando atingem quórum (espera réplicas)
- Filas não-durable (`D` ausente — ver [Features](./ui-features.md)) **somem** — não há "state após restart" pra elas
