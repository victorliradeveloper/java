# Outbox Pattern — Plano de Implementação

Persistir eventos no banco, na **mesma transação** do save da entidade, e publicar no SQS de forma assíncrona via job dedicado. Resolve o problema de **dual-write** identificado em [`idempotency.md`](./idempotency.md) §2.

Status: implementado e verificado E2E (2026-05-22). Pré-requisitos: PR 1 consumer dedupe ✅ + [migração Mongo](./migration-mongo.md) ✅. Pattern resultante: [`../../03-patterns/outbox.md`](../../03-patterns/outbox.md).

> **AVISO — estratégia de lock muda com Mongo**: o desenho deste documento referencia `SELECT FOR UPDATE SKIP LOCKED` (Postgres). Esse mecanismo não existe em MongoDB. Na implementação, substituir por **lease pattern com `findOneAndUpdate` atômico**: o worker reivindica um doc pendente setando `processingNode` + `leaseExpiresAt` numa operação atômica. Múltiplos workers competem; cada doc só é processado por um. Lease com TTL evita pendentes presos se um worker crashar. Ajustar `OutboxEventRepository` e `OutboxPublisher` conforme antes de implementar.

---

## Por que fazer isso

### Estado atual (`todo-service`)

```java
Todo todo = repository.save(mapper.toEntity(dto));    // 1) escreve no banco
publish(SqsConfig.QUEUE_CREATED, event);              // 2) publica no SQS
return mapper.toResponse(todo);
```

São **duas escritas em sistemas diferentes** (banco + SQS) sem garantia de atomicidade. Cenários problemáticos:

| Falha | Consequência |
|---|---|
| Save OK, SQS cai antes de publish | Banco tem Todo, fila não. **Evento perdido.** Notificação nunca chega. |
| Save OK, processo crasha entre 1 e 2 | Idem — evento perdido. |
| Save falha mas alguém reordenar pro publish vir antes | Evento fantasma — fila recebe, banco não tem o Todo. |

O anti-pattern `.spec/02-anti-patterns/java-spring.md` §Transações diz literalmente: *"Nunca dispare e-mail/fila/HTTP dentro de transação ativa sem `@TransactionalEventListener`"*. O outbox é a resposta institucional pra isso.

### Estado desejado

```java
@Transactional
public TodoResponseDTO create(TodoRequestDTO dto) {
    Todo todo = repository.save(mapper.toEntity(dto));    // ambos
    outboxService.record(QUEUE_CREATED, event);            // commitam
    return mapper.toResponse(todo);                        // ou nenhum
}
```

Save do Todo e gravação do evento na tabela `outbox_events` acontecem **na mesma transação**. Um `@Scheduled` separado lê eventos pendentes e publica no SQS, marcando como publicado em transação distinta.

**Garantia**: se o Todo está no banco, o evento eventualmente vai pro SQS. Se o evento foi pro SQS, o Todo está no banco. Atomicidade efetiva entre os dois sistemas.

---

## Fluxo antes vs depois

### Antes (dual-write quebrado)

```
Controller ──> TodoService.create
                   │
                   ├──> repository.save(todo)         [TX commitada]
                   │
                   └──> sqsTemplate.send(event)       [fora de TX, pode falhar]
                                                       SE FALHAR → evento perdido
```

### Depois (outbox)

```
Controller ──> TodoService.create  [@Transactional]
                   │
                   ├──> repository.save(todo)             ┐
                   │                                       │ MESMA TX
                   └──> outboxService.record(event)        ┘ — ou ambos commitam
                                                              ou nenhum

                   ... TX commita ...

@Scheduled OutboxPublisher (a cada 2s)
   │
   ├──> SELECT * FROM outbox_events
   │    WHERE published_at IS NULL
   │    ORDER BY created_at
   │    FOR UPDATE SKIP LOCKED
   │    LIMIT 50
   │
   ├──> para cada evento:                    [TX nova, REQUIRES_NEW]
   │       │
   │       ├──> sqsTemplate.send(destination, payload)
   │       │
   │       ├──> SE OK: UPDATE outbox_events SET published_at = NOW()
   │       │
   │       └──> SE FALHA: UPDATE attempts = attempts + 1, last_error = '...'
   │                       (published_at continua null → próximo ciclo tenta de novo)
```

---

## Componentes novos

### 1. Tabela `outbox_events`

Criada via `ddl-auto: update` do JPA (consistente com o resto do projeto). Migrar pra Flyway é dívida separada.

| Coluna | Tipo | Notas |
|---|---|---|
| `id` | UUID PK | Gerado pelo Hibernate, vai virar `messageDeduplicationId` se um dia migrar pra FIFO |
| `aggregate_id` | VARCHAR | ID do Todo (futuramente outras entidades) |
| `aggregate_type` | VARCHAR | `"Todo"` por enquanto |
| `event_type` | VARCHAR | `CREATED` / `UPDATED` / `DELETED` |
| `destination` | VARCHAR | Nome da fila SQS |
| `payload` | TEXT | JSON serializado do `TodoEvent` |
| `created_at` | TIMESTAMP | Quando o evento foi gravado |
| `published_at` | TIMESTAMP NULL | NULL = pendente, NOT NULL = já publicado |
| `attempts` | INT DEFAULT 0 | Contador de tentativas (debug/observability) |
| `last_error` | TEXT NULL | Stacktrace resumido da última falha |

**Índice**: `(published_at, created_at)` — varredura ordenada de pendentes pelo publisher.

### 2. `OutboxEvent` — entidade JPA

`todo-service/.../infrastructure/entity/OutboxEvent.java`

Sem `@Data` do Lombok (regra do anti-pattern). Usa `@Getter` + `@NoArgsConstructor` + `@AllArgsConstructor` + builder se precisar.

### 3. `OutboxEventRepository`

`todo-service/.../infrastructure/repository/OutboxEventRepository.java`

```java
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxEvent e WHERE e.publishedAt IS NULL ORDER BY e.createdAt")
    List<OutboxEvent> findPendingBatch(Pageable pageable);
}
```

`jakarta.persistence.lock.timeout = -2` → SKIP LOCKED (Postgres). Permite múltiplas instâncias do `todo-service` puxando lotes diferentes sem conflito.

### 4. `OutboxService`

`todo-service/.../outbox/OutboxService.java`

```java
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void record(String destination, String aggregateId, String eventType, Object payload) {
        String json = serialize(payload);
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId(aggregateId)
                .aggregateType("Todo")
                .eventType(eventType)
                .destination(destination)
                .payload(json)
                .build();
        repository.save(event);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento outbox", e);
        }
    }
}
```

**Sem `@Transactional` próprio** — herda a TX do `TodoService` chamador (regra: TX no service de negócio, não em service de infra que participa dela).

### 5. `OutboxPublisher`

`todo-service/.../outbox/OutboxPublisher.java`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxPublisher self;   // workaround pra self-invocation de @Transactional

    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelayString = "${outbox.poll-interval:2000}")
    public void publishPending() {
        List<OutboxEvent> pending = repository.findPendingBatch(PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : pending) {
            self.publishOne(event);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(OutboxEvent event) {
        try {
            TodoEvent payload = objectMapper.readValue(event.getPayload(), TodoEvent.class);
            sqsTemplate.send(event.getDestination(), payload);
            event.markPublished();
            log.info("[OUTBOX] publicado id={} destination={}", event.getId(), event.getDestination());
        } catch (Exception e) {
            event.markFailed(truncate(e.toString()));
            log.warn("[OUTBOX] falha id={} attempts={}: {}", event.getId(), event.getAttempts(), e.getMessage());
        }
        repository.save(event);
    }

    private String truncate(String s) { return s.length() > 2000 ? s.substring(0, 2000) : s; }
}
```

Notas:
- `Self-invocation` (`this.publishOne(...)`) ignoraria `@Transactional` — daí a injeção `private final OutboxPublisher self`. Padrão documentado no anti-pattern.
- `REQUIRES_NEW` garante que cada evento publica/falha isoladamente: uma falha não rola back o lote inteiro.
- `markPublished` / `markFailed` são métodos na entidade que setam os campos — encapsulamento.

### 6. Habilitar scheduling

`TodoServiceApplication.java`:

```java
@SpringBootApplication
@EnableScheduling
public class TodoServiceApplication { ... }
```

### 7. Configuração

`application.yml`:

```yaml
outbox:
  poll-interval: 2000   # ms entre cada varredura do publisher
  batch-size: 50        # eventos por ciclo (constante por enquanto, sem flag)
```

---

## Componentes alterados

### `TodoService.java`

```java
@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository repository;
    private final OutboxService outboxService;   // ← antes era SqsTemplate
    private final TodoMapper mapper;

    @Transactional                                // ← novo
    public TodoResponseDTO create(TodoRequestDTO dto) {
        Todo todo = repository.save(mapper.toEntity(dto));
        outboxService.record(
            SqsConfig.QUEUE_CREATED,
            todo.getId(),
            "CREATED",
            TodoEvent.of(todo.getId(), todo.getTitle(), "CREATED")
        );
        return mapper.toResponse(todo);
    }

    @Transactional
    public TodoResponseDTO update(String id, TodoUpdateDTO dto) {
        // ... lógica de diff do PR 1.2 mantida ...
        if (!before.equals(after)) {
            outboxService.record(SqsConfig.QUEUE_UPDATED, id, "UPDATED", ...);
        }
        return ...;
    }

    @Transactional
    public void delete(String id) {
        repository.findById(id).ifPresent(todo -> {
            repository.delete(todo);
            outboxService.record(SqsConfig.QUEUE_DELETED, id, "DELETED", ...);
        });
    }
}
```

`SqsTemplate` sai do `TodoService` — passa a ser dependência só do `OutboxPublisher`.

---

## Tarefas (com checkbox)

### Fase 1 — Entidade e repositório
- [ ] Criar `OutboxEvent.java` com os campos da tabela + métodos `markPublished()` / `markFailed(String reason)`
- [ ] Criar `OutboxEventRepository.java` com `findPendingBatch(Pageable)` usando `@Lock(PESSIMISTIC_WRITE)` + hint SKIP LOCKED
- [ ] Adicionar índice `@Index(columnList = "published_at, created_at")` na anotação `@Table`
- [ ] Subir app local e verificar que `ddl-auto: update` cria a tabela `outbox_events` com o índice

### Fase 2 — Service e fluxo no TodoService
- [ ] Criar `OutboxService.record(destination, aggregateId, eventType, payload)`
- [ ] Anotar `create`, `update`, `delete` no `TodoService` com `@Transactional`
- [ ] Substituir `publish(...)` por `outboxService.record(...)`
- [ ] Remover dependência de `SqsTemplate` do `TodoService`
- [ ] Verificar via psql que cada POST/PUT/DELETE grava linha em `outbox_events` com `published_at = NULL`

### Fase 3 — Publisher (scheduler)
- [ ] Adicionar `@EnableScheduling` em `TodoServiceApplication`
- [ ] Criar `OutboxPublisher` com `@Scheduled` + `publishOne(@Transactional REQUIRES_NEW)`
- [ ] Resolver self-invocation injetando `private final OutboxPublisher self`
- [ ] Adicionar `outbox.poll-interval` no `application.yml`
- [ ] Verificar via logs que `[OUTBOX] publicado id=...` aparece a cada ciclo
- [ ] Verificar via psql que `published_at` é preenchido após publish

### Fase 4 — Resiliência
- [ ] Verificar comportamento com SQS down: parar `localstack`, fazer POST → evento fica `published_at = NULL`, `attempts` incrementa
- [ ] Religar `localstack` → próximo ciclo do publisher publica e marca como publicado
- [ ] Confirmar que falha em um evento não bloqueia os outros do lote (graças ao `REQUIRES_NEW`)

### Fase 5 — Verificação end-to-end
- [ ] POST/PUT/DELETE no `todo-service` → linha em `outbox_events`
- [ ] Em até 2s, mensagem chega no `notification-service` via SQS
- [ ] `processed_messages` ganha 1 linha (dedupe do PR 1.3 segue funcionando)
- [ ] Email é enviado normalmente

---

## Decisões / trade-offs

### 1. Lock para múltiplas instâncias
**Escolha**: `SELECT ... FOR UPDATE SKIP LOCKED` via JPA hint.

**Por quê**: deixa o projeto pronto pra rodar com 2+ pods do `todo-service` sem dois publishers pegarem o mesmo evento. Em escala única, não custa nada.

**Alternativa descartada**: lock advisory do Postgres (`pg_try_advisory_lock`). Mais simples mas serializa todo o publisher num único pod.

### 2. Retention dos eventos publicados
**Escolha por agora**: deixar acumulando.

**Por quê**: facilita debug/auditoria em dev. Em produção precisaria de cleanup periódico (job apagando linhas com `published_at < now() - 7 days`).

**Anotado como dívida**, igual ao cleanup de `processed_messages`.

### 3. Backoff em erro
**Escolha por agora**: sem backoff. Só incrementa `attempts` e tenta de novo no próximo ciclo (2s depois).

**Por quê**: simplicidade. Casos reais de falha persistente (SQS fora por minutos) ainda funcionam — eventualmente o serviço volta e o publisher catch up.

**Futuro**: adicionar `next_attempt_at` com backoff exponencial (1s, 2s, 4s, 8s... cap em 5min) se virar problema operacional.

> **Resolvido em 2026-05-24**: backoff exponencial com jitter (±25%) implementado em `OutboxEvent.markFailed(reason, initialMs, maxMs)`. Sequência atual com defaults (`outbox.backoff.initial-ms=2000`, `max-ms=60000`): 2s → 4s → 8s → 16s → 32s → 60s (cap). `claimNext` filtra docs com `next_attempt_at > now`. Índice composto criado pela `V005_OutboxNextAttemptIndex`. Pattern atualizado em [`03-patterns/outbox.md`](../../03-patterns/outbox.md).

### 4. Poll interval
**Escolha**: 2000ms (2s), configurável via `outbox.poll-interval`.

**Por quê**: trade-off latência vs custo de polling. 2s é responsivo o suficiente pra notificações de Todo, e o overhead é desprezível (uma query indexada por ciclo).

### 5. Idempotência do publisher
**Cenário**: publisher faz `sqsTemplate.send` com sucesso, mas crasha **antes** de marcar `published_at`. No próximo ciclo, republica.

**Consequência**: SQS recebe o evento 2x. O consumer (PR 1.3) descarta a duplicata via `processed_messages`. **Sem o PR 1.3, esse PR pioraria a duplicação** — daí a ordem PR 1 → PR 2.

### 6. Payload como TEXT (JSON) vs JSONB
**Escolha**: TEXT.

**Por quê**: simplicidade. Não consultamos o conteúdo do payload, só lemos/desserializamos. JSONB seria útil pra `WHERE payload->>'todoId' = ...` em queries de debug — não precisamos disso.

### 7. Não usar `@TransactionalEventListener` do Spring
**Por quê**: ele resolve só "fazer X depois do commit", não persiste durabilidade. Se o processo cair entre o commit e o publish, perde igual. Outbox resolve essa janela com durabilidade no banco.

### 8. Migration via `ddl-auto: update` (dívida)
**Aceito por consistência** com o resto do projeto. Em produção real, migrar pra Flyway com versionamento (`V2__create_outbox_events.sql`).

---

## Riscos conhecidos

| Risco | Severidade | Mitigação |
|---|---|---|
| Tabela `outbox_events` cresce sem limite | Média | Cleanup periódico (dívida). |
| Lote muito grande prende lock muito tempo | Baixa | `BATCH_SIZE = 50` mantém a TX curta. Ajustável. |
| Self-invocation quebra `@Transactional` | Alta se esquecer | Padrão `OutboxPublisher self` documentado e usado. |
| Publisher para de rodar (bug no scheduler) | Alta | Adicionar métrica/health check do publisher. **Não no escopo desta PR.** |
| Encoding do JSON quebra serialização | Baixa | `ObjectMapper` reusado do Spring (já tem `JavaTimeModule`). |

---

## Definição de pronto

PR fecha quando:

1. [ ] Todos os checkboxes das fases 1-5 marcados
2. [ ] `mvn -q compile` sem erro nos dois módulos
3. [ ] `docker-compose up` sobe tudo healthy
4. [ ] Fluxo end-to-end funcional (POST → outbox → SQS → notification → email)
5. [ ] Cenário de SQS down validado (eventos ficam pendentes, retomam quando volta)
6. [ ] Checkboxes do PR 2 em `idempotency.md` marcados como `[x]`

---

## Próximos PRs habilitados

| PR | O que destrava |
|---|---|
| PR 3 — `Idempotency-Key` | Outbox precisa estar em pé pra a resposta do POST ser cacheável de forma confiável |
| PR 4 — SQS FIFO | `outbox_events.id` vira `messageDeduplicationId` natural |
| Observabilidade | Métrica "lag do outbox" (`MAX(now() - created_at)` em pendentes) vira KPI |
