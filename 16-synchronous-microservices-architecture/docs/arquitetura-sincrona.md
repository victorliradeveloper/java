# Arquitetura sincrona (HTTP) vs assincrona (mensageria)

Este projeto eh o espelho do `01-microservices` com uma diferenca fundamental:
**toda a comunicacao entre servicos eh HTTP request/response**. Sem RabbitMQ,
sem outbox, sem DLQ.

## Visao geral do fluxo

```
Cliente
  |
  v
[api-gateway]  ----lb://todo-service---->  [todo-service]
                                                |
                                                |--- (1) persiste no Postgres
                                                |
                                                |--- (2) HTTP -> [audit-service]
                                                |
                                                |--- (3) HTTP -> [notification-service]
                                                |
                                                v
                                          retorna 201
```

Os passos (2) e (3) acontecem **depois do commit** da transacao do passo (1).
Cada um eh isolado por Circuit Breaker + Retry e tem fallback proprio que NAO
derruba o request principal.

## O que mudou em relacao ao 01-microservices

| Aspecto | `01-microservices` (async) | `16-synchronous-...` (sync) |
|---|---|---|
| Transporte | AMQP (RabbitMQ) | HTTP (Feign + Eureka LB) |
| Garantia de entrega | Outbox + persistent retry | Best-effort + Resilience4j |
| Latencia do POST | Volta rapido (so' grava no outbox) | Espera audit + notification responderem |
| Acoplamento temporal | Downstreams podem estar offline | Downstreams precisam estar UP |
| Backpressure | Fila absorve picos | Cliente sente picos no downstream |
| Topologia | Exchange + queues + bindings + DLX | Endpoints REST + service discovery |
| Codigo a manter | Outbox, publisher, listener, DLQ listener | Feign client, Resilience4j, fallback |
| Mensagens perdidas | Quase nunca (outbox retenta) | Quando downstream cai + CB esgota retry |

## Quando escolher cada um

### Use sincrono quando:

- **Latencia importa pro caller**, mas a maquina nao tem trafego suficiente pra
  justificar a complexidade da mensageria.
- **Os eventos sao "nice to have"** (auditoria, notificacao informativa) — pode
  perder em incidente sem comprometer o negocio.
- **Voce ja tem service discovery + load balancer** (Eureka, k8s, Consul) — o
  custo marginal de adicionar HTTP cross-service eh ~zero.
- **A equipe eh pequena** e operar RabbitMQ/Kafka eh sobrecarga real.

### Use assincrono quando:

- **Eventos sao criticos** (cobranca, pagamento, billing, integracao contabil).
- **Picos de trafego** sao previsiveis ou comuns — fila como buffer evita
  cascata de falhas downstream.
- **Downstreams sao lentos** (processamento batch, ML, integracao externa) —
  resposta rapida do request principal eh requisito.
- **Voce precisa de fan-out estavel** — 1 evento -> N consumidores
  independentes, sem o publisher saber quem ouve.
- **Failure isolation eh prioridade**: queda do consumer nao deve afetar o
  publisher.

## Por que o request principal nao falha quando downstream cai

O `TodoService` no fluxo de `create` faz:

```
1. persistence.create(dto)   <-- TX commit
2. notifyAudit(event)        <-- Resilience4j: retry, CB, fallback
3. notifyNotification(event) <-- Resilience4j: retry, CB, fallback
```

O `DownstreamNotifier` tem `@CircuitBreaker(fallbackMethod = "...")`. Quando o
downstream falha alem do limite:

- O Retry tenta 3x com backoff exponencial (200ms, 400ms, 800ms).
- Apos esgotar, a exception cai no fallback que **apenas loga e segue**.
- Resultado pro cliente: 201 Created. Trade-off: o evento foi perdido.

Em prod real, esse fallback gravaria num **dead letter local** (tabela no
Postgres do todo-service) pra reprocessamento posterior. Aqui mantemos
simples — o log explicita o que esta sendo perdido.

## Por que persistencia em transacao separada das chamadas HTTP

```java
// TodoService.create()
TodoResponseDTO response = persistence.create(dto);  // TX commit aqui
notifyDownstreams(...)                                // chamada HTTP fora da TX
```

Se a chamada HTTP estivesse dentro da transacao:

- A conexao do pool ficaria segurada o tempo todo do request remoto (potencialmente
  segundos).
- Sob carga, o pool esgota rapido e todos os requests comecam a esperar.
- O DB nao tem nada a ver com a chamada remota, mas seria penalizado por ela.

Por isso: salva, commita, libera a conexao, ENTAO faz a chamada HTTP.

## Idempotencia: por que ainda eh importante

Cada chamada do `todo-service` aos downstreams inclui um `eventId` (UUID gerado
no momento). Esse UUID eh:

- Chave primaria em `todo_audit_log` (audit-service) — `ON CONFLICT DO NOTHING`.
- Chave em `processed_events` (notification-service) — `ON CONFLICT DO NOTHING`.

Se o Retry do Resilience4j entrega a mesma chamada 2x (por exemplo, timeout
mas o downstream ja processou), a segunda recebe 202 OK mas nao duplica
trabalho.

Esse padrao eh igual ao da versao com mensageria — apenas o transporte mudou.

## O que voce perde sem mensageria

1. **Durabilidade**: outbox + ack do consumer garantem que evento eventualmente
   chega. Sem isso, evento gerado durante outage do downstream + esgotamento
   do retry = perdido.
2. **Fan-out independente**: pra adicionar um 4o consumer (ex.: analytics), no
   modelo async basta criar uma queue nova bindada na exchange. Aqui voce
   precisa adicionar uma chamada HTTP a mais no `TodoService` (acoplamento
   crescente).
3. **Buffer de picos**: 1000 requests/s simultaneos no `todo-service` viram
   2000 requests/s nos downstreams (audit + notification). Fila absorveria.
4. **Failure isolation real**: aqui o downstream em panico (responde lento
   mas nao falha) consome request slot do `todo-service`. O CircuitBreaker
   mitiga, mas nao elimina.

## O que voce ganha

1. **Simplicidade operacional**: zero RabbitMQ, zero outbox, zero DLQ.
2. **Debugging linear**: erro em audit aparece no log do todo-service no mesmo
   request — sem precisar correlacionar com a mensagem na fila.
3. **Latencia ponta-a-ponta visivel**: tempo total = tempo do request principal.
4. **Menos codigo**: ~40% menos classes que a versao async.
