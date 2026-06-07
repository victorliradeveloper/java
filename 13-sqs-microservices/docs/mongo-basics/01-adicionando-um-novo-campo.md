# Evolução de schema no MongoDB — adicionando um campo novo

Como adicionar um campo novo (ex: `priority: HIGH|MEDIUM|LOW`) a uma collection existente onde os documentos antigos não têm o campo.

> Mongo é **schemaless**: não existe `ALTER TABLE`. Adicionar o campo na entity Java já basta pros novos inserts. Os antigos é que viram o problema — ficam sem o campo, e isso pode quebrar a app se ela assumir que sempre vai existir.

A solução é: **backfill versionado via Mongock**. Roda uma única vez no startup, marca como aplicado no `mongockChangeLog`, nunca repete.

---

## Passos

### 1. Modelar o campo na app

- Criar `enum Priority { HIGH, MEDIUM, LOW }` em `infrastructure/entity` (ou em `domain`).
- Adicionar `private Priority priority;` na entity `Todo`.
- Atualizar os DTOs:
  - `TodoRequestDTO` — obrigatório no POST (`@NotNull`) ou opcional com default `MEDIUM`. Escolha de negócio.
  - `TodoUpdateDTO` — opcional (PATCH/PUT parcial).
  - `TodoResponseDTO` — sempre retorna.
- Atualizar o `TodoMapper`.

> Bean Validation aceita enum direto — Spring rejeita automaticamente se o cliente mandar `"FOO"`.

### 2. ChangeUnit Mongock para backfill

Cria `V006_BackfillTodoPriority.java` em `infrastructure/migration/`. A operação equivalente no Mongo é:

```javascript
db.todos.updateMany(
  { priority: { $exists: false } },
  { $set: { priority: "MEDIUM" } }
)
```

Em Spring, vira `MongoTemplate.updateMulti(...)` com `Criteria.where("priority").exists(false)` e `Update.set("priority", "MEDIUM")`.

**Template pronto no projeto:** `V002_BackfillTodoUpdatedAt` ([infrastructure/migration/V002_BackfillTodoUpdatedAt.java](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V002_BackfillTodoUpdatedAt.java)) — copie a estrutura.

### 3. (Opcional) Índice se for filtrar por priority

Se houver endpoint tipo `GET /todos?priority=HIGH`, vale outra ChangeUnit:

```javascript
db.todos.createIndex({ priority: 1 })
```

Template: `V001_BaselineIndexes`. Sem índice, query por priority faz collection scan — funciona com poucos docs, mas degrada em produção.

### 4. Deploy

- Rebuild do `todo-service`.
- No startup, Mongock dispara → backfill completa → app aceita tráfego.
- Em múltiplas réplicas, o `mongockLock` garante que só uma instância roda o backfill. As outras esperam.

---

## Por que esse padrão funciona bem aqui

- **Idempotente:** roda uma vez, marca no `mongockChangeLog`, nunca repete.
- **Auditável:** todo histórico de migrações fica gravado no banco.
- **Versionado:** o número da ChangeUnit (`V006_*`) deixa explícita a ordem.
- **Coerente com o projeto:** o `V002_BackfillTodoUpdatedAt` já faz exatamente esse padrão para o campo `updatedAt`.

---

## Quando esse padrão NÃO é suficiente — a preocupação real em escala

O padrão "Mongock no startup" funciona aqui porque o backfill leva milissegundos. Adicionar um campo numa collection com **milhares de usuários e milhões de documentos** é um desafio real, e os problemas raramente estão onde a intuição manda olhar. Não é "muitos usuários" que machuca — é **muitos documentos a serem reescritos** combinado com **um sistema que não pode parar enquanto isso acontece**.

### Onde mora o desafio

- **Bloqueio de deploy.** Mongock roda no startup, antes do readiness probe responder OK. Backfill em 10M docs leva minutos. Durante esse tempo, Kubernetes não envia tráfego pro pod novo, o rolling update trava, e o `mongockLock` segura todas as réplicas esperando. Em sistema com SLA 99.9%, você tem 8h de downtime/mês de orçamento — gastar 30min nisso é incidente.

- **Pressão concorrente no Mongo.** A migração não roda no vácuo. Enquanto ela escreve em 10M docs, a app está atendendo milhares de req/s. As duas competem por write lock, o cache evicta queries quentes, o oplog enche, replicação atrasa nas secondaries. Latência p99 da app explode.

- **Atomicidade enganosa.** `updateMany` em N docs **não** é uma transação. É atômico por doc. Crash no meio (OOM, eviction, deploy abortado)? Metade do banco está num estado, metade noutro — e a app precisa lidar com os dois sem saber qual.

- **Reverter é tão caro quanto aplicar.** Se a migração foi errada, o undo é outro `updateMany` com os mesmos problemas. Não tem `git revert` pra dados.

- **Validators são uma faca de dois gumes.** Ligar `$jsonSchema` exigindo `priority` obrigatório com 1 doc sem `priority` ainda no banco faz **todo update naquele doc passar a falhar**. A validação vira um bug de produção.

- **Múltiplos escritores.** Em produção real raramente uma só app escreve. Tem batch noturno, CDC pra data lake, scripts ad-hoc, outros microserviços. Qualquer um pode reintroduzir docs sem `priority` se não conhecer o schema novo.

- **Sharded cluster muda tudo.** Em Mongo sharded, se o filtro `{ priority: { $exists: false } }` não usar a shard key, vira **broadcast** em todas as shards. Custo distribuído, lag heterogêneo, debug pesado.

### Sinais de que o caminho simples vai quebrar

| Sinal | Implicação |
|---|---|
| Collection > 1M docs | Backfill no startup começa a virar minutos |
| SLA > 99.9% | Não pode parar a app pra migrar |
| Múltiplas réplicas do serviço | `mongockLock` bloqueia o deploy todo |
| Outras apps escrevendo na mesma collection | Schema novo não é unilateral |
| Reads em secondaries | Replication lag durante backfill afeta consistência |
| Cluster sharded | Filtros sem shard key são caros |

### O que se faz em escala real

Separa **schema evolution** de **deploy de feature**:

1. **Deploy 1 (forward-compatible):** código sobe tolerante a `null`. Leituras tratam `priority` ausente como default `MEDIUM`. Escritas sempre setam o campo. **Nenhum backfill.**
2. **Backfill em background:** job paginado por `_id` (não `skip`), em lotes de 500–2000 docs, com throttle entre lotes. Pode levar dias. Idempotente — pode parar e retomar. Métrica de count restante em dashboard.
3. **Deploy 2 (cleanup):** quando o count restante é zero por algumas horas, remove o fallback `null → MEDIUM`, torna o campo `@NotNull`, opcionalmente liga `$jsonSchema` validator.

Cada fase é reversível sozinha. Nenhuma trava deploy. O custo é **complexidade de código** (fallback temporário em duas camadas) trocada por **previsibilidade operacional**.

### Resposta direta

O que rodamos hoje funciona porque a collection é pequena e o projeto não tem SLA. Em produção real com milhares de usuários ativos, esse mesmo padrão vira munição de incidente. Mas a **mecânica de versionar via Mongock** (idempotente, auditável, ordem garantida) **permanece** — o que muda é **onde o backfill roda** (background, não startup) e **em quantas fases o deploy é dividido**.
