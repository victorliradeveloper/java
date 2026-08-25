# UI Type (Management Console)

A aba **Queues and Streams** do RabbitMQ Management UI tem uma coluna **Type** logo depois do nome da fila. Esse valor diz qual **implementação interna** o broker usa pra armazenar e entregar mensagens daquela fila — e cada tipo tem um perfil bem diferente de durabilidade, replicação e custo.

Diferente das [Features](./ui-features.md) (que são argumentos opcionais aplicados em cima), o **Type** é a categoria base da fila. Você escolhe um e ele dita o comportamento fundamental.

---

## Mapa do projeto

| Fila | Type |
|---|---|
| `todo.created.queue` / `.dlq` | classic |
| `todo.updated.queue` / `.dlq` | classic |
| `todo.deleted.queue` / `.dlq` | classic |
| `todo.audit.queue` / `.dlq`   | classic |

Todas as 8 filas do projeto são **classic** — escolha padrão do `QueueBuilder.durable(...)` sem argumento extra. Faz sentido em dev/single-node; em produção com HA o caminho seria migrar pra quorum (ver mais abaixo).

---

## Os 3 tipos

### `classic`

A implementação **original** do RabbitMQ. Cada fila vive em **um único node** do cluster — o "queue master". Réplicas só existem se você ligar a feature legada de *mirrored queues* (depreciada no RabbitMQ 3.13+).

**Quando usar:**
- Single-node (dev, staging, projetos pequenos)
- Filas onde perder mensagem na queda do node não é crítico
- Latência mínima importa mais que replicação

**Tradeoffs:**
- ✅ **Mais leve** — menor consumo de CPU/memória, throughput maior por mensagem
- ✅ **Latência menor** — sem coordenação entre nodes
- ❌ **Sem HA real** — se o node morre, a fila some até ele voltar (e pode perder mensagens não persistidas)
- ❌ **Mirrored queues depreciadas** — não há mais caminho oficial de HA pra classic

> No projeto: classic é a escolha certa pra dev local com 1 node. Se algum dia subir um cluster RabbitMQ pra HA, o plano é migrar as 4 filas principais pra quorum.

---

### `quorum`

Substituto **moderno** das mirrored queues, baseado no algoritmo de consenso **Raft**. Cada fila tem N réplicas (tipicamente 3 ou 5), distribuídas entre nodes do cluster. Uma escrita só é confirmada depois que a maioria das réplicas (`N/2 + 1`) gravou em disco.

**Quando usar:**
- Cluster RabbitMQ em produção com >1 node
- Filas onde **perder mensagem não é aceitável** (financeiro, compliance, audit trail)
- Você quer HA verdadeira sem o legado de mirrored queues

**Tradeoffs:**
- ✅ **HA forte** — node cai, fila continua disponível nos outros, sem perda de mensagens confirmadas
- ✅ **Failover automático** — Raft elege novo líder em segundos
- ✅ **Suportado long-term** — caminho oficial pra HA no RabbitMQ moderno
- ❌ **Mais pesado** — 3x storage (uma cópia por réplica), CPU/rede do consenso
- ❌ **Latência maior** — escrita só confirma depois do quorum (típico: 1-5ms extra)
- ❌ **Mensagens sempre em disco** — não tem o modo "RAM only" do classic

Pra declarar via Spring AMQP:
```java
QueueBuilder.durable("minha.queue")
    .quorum()
    .build();
```

---

### `stream`

Introduzido no RabbitMQ 3.9 — não é "fila" no sentido AMQP tradicional, mas um **log append-only** (parecido com Kafka). Mensagens não são deletadas quando consumidas; ficam até atingir um limite de tempo/tamanho. Consumers leem por offset.

**Quando usar:**
- Replay de eventos (reprocessar histórico do começo)
- Múltiplos consumers lendo o **mesmo** stream em paralelo, cada um com seu offset (broadcast com cursor)
- Throughput muito alto de mensagens pequenas (>1M msg/s)
- Event sourcing, ingestão de telemetria, audit trail navegável

**Tradeoffs:**
- ✅ **Throughput altíssimo** — ordem de magnitude acima de classic/quorum
- ✅ **Replay** — consumer pode voltar pro offset 0 e re-ler tudo
- ✅ **Múltiplos consumers independentes** — cada um mantém seu próprio cursor
- ❌ **Protocolo próprio** (porta 5552) — clients AMQP tradicionais funcionam mas perdem features
- ❌ **Sem dead-letter** — modelo não combina com DLX/DLQ; falhas tratam-se no consumer
- ❌ **Storage cresce** — você precisa configurar `max-age` ou `max-length-bytes` ou ele enche o disco

Pra declarar:
```java
QueueBuilder.durable("minha.stream")
    .stream()
    .build();
```

---

## Comparação rápida

| Aspecto | classic | quorum | stream |
|---|---|---|---|
| HA | Não (mirror depreciado) | Sim (Raft) | Sim (replicado) |
| Mensagem deletada após ack | Sim | Sim | **Não** |
| Replay possível | Não | Não | **Sim** |
| Suporta DLX | Sim | Sim | **Não** |
| Latência típica | Baixa | Média | Baixa-Média |
| Throughput | Médio | Médio | **Muito alto** |
| Mensagens em RAM | Opcional | Não (sempre em disco) | Não (sempre em disco) |
| Caso de uso | Default, single-node | Produção HA | Event log, replay |

---

## Como escolher

**Default:** classic. Só migra se tiver motivo concreto.

**Migra pra quorum se:**
- Tem cluster com 3+ nodes
- Perda de mensagem é inaceitável em queda de node
- Está em produção e o broker não pode ser SPOF

**Migra pra stream se:**
- Precisa de replay
- Throughput de classic/quorum não atende
- Quer múltiplos consumers lendo o mesmo histórico

Não é decisão "uma vez por broker" — você pode misturar tipos no mesmo cluster. Tipicamente: filas principais em quorum, audit/event-log em stream, filas de teste em classic.

---

## Sobre mudar o tipo de uma fila existente

**Não dá.** O type é definido no momento da declaração e é imutável. Pra "mudar", a estratégia é:

1. Declarar nova fila com o type novo e nome diferente (`todo.created.queue.v2`)
2. Adicionar binding na mesma exchange/routing key
3. Atualizar consumers pra ler da nova
4. Drenar a antiga (esperar Ready=0)
5. Deletar a antiga
6. Renomear a nova (ou viver com o `.v2`)

Esse é o mesmo padrão de migration que vale pra qualquer mudança incompatível de argumento de fila (ver [feedback de produção](../../15-sqs-microservices) sobre não deletar/recriar com mesmo nome em prod).
