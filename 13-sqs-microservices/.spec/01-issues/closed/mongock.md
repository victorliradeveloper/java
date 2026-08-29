# Versionamento de schema com Mongock

Substituir `spring.data.mongodb.auto-index-creation: true` por **Mongock** nos dois serviços (`todo-service` e `notification-service`). Fecha a dívida registrada em `02-anti-patterns/mongo-db.md`, `03-patterns/outbox.md` e `01-issues/closed/migration-mongo.md`.

Status: **CONCLUÍDO** em 2026-05-22 (criado e finalizado no mesmo dia). Ver "Divergências do plano original" no final.

---

## Por que agora

Diretriz do projeto: ficar próximo de padrões reais de mercado (memória `feedback-production-patterns`). `auto-index-creation: true` é aceitável em dev mas é anti-pattern em produção — Mongock é a solução padrão da comunidade Java/Spring pra versionar schema/índices/dados em Mongo.

Esta issue **não é** sobre adicionar índices novos. É sobre instalar a disciplina de versionamento **antes** de a gente precisar. Hoje o repo tem 1 índice declarado (`OutboxEvent.published_at`); a próxima vez que precisarmos de outro, ele já entra como `ChangeUnit`.

---

## O que está no escopo

- Adicionar Mongock em `todo-service` e `notification-service`
- Desligar `auto-index-creation: true` em ambos
- Criar baseline `ChangeUnit V001` por serviço, refletindo **exatamente** o que `auto-index-creation` faz hoje (nada além disso — sem scope creep)
- Documentar como adicionar novos `ChangeUnit`s
- Atualizar referências a "dívida Mongock" no `.spec`

## O que **não** está no escopo

- Não adicionar índices novos (`Todo`, `ProcessedMessage` continuam sem índice além do `_id`)
- Não adicionar TTL em `outbox_events.published_at` (vira issue separada — retenção)
- Não migrar dados existentes — só schema/índices
- Não introduzir Mongock no `api-gateway`/`eureka-server` (não usam banco)

---

## Estado atual (verificado em código, 2026-05-22)

| Serviço | Collection | Índices declarados hoje | Origem |
|---|---|---|---|
| `todo-service` | `todos` | só `_id` (implícito) | nenhuma anotação `@Indexed` |
| `todo-service` | `outbox_events` | `_id` + `published_at` | `@Indexed` em `OutboxEvent.publishedAt` |
| `notification-service` | `processed_messages` | só `_id` (== `messageId`) | nenhuma anotação `@Indexed` |

Ou seja, a migração Mongock baseline tem **um único índice não-implícito** pra registrar (`outbox_events.published_at`). O resto é setup.

---

## Decisões propostas (a confirmar antes de executar)

1. **Biblioteca**: `mongock-springboot-v3` + `mongodb-springdata-v4-driver`. Versão `5.x` (compatível com Spring Boot 3.3.5 + Spring Data Mongo 4.x).
2. **Ativação**: `@EnableMongock` nas classes `@SpringBootApplication` dos dois serviços.
3. **Configuração**: bloco `mongock:` no `application.yml`, apontando para o **mesmo DB do serviço** (`tododb`/`notificationdb`). Collection de controle padrão (`mongockChangeLog`, `mongockLock`).
4. **Pacote dos ChangeUnits**: `infrastructure/migration/` em cada serviço. Convenção de nome: `V{NNN}_{snake_case_descricao}.java` (ex.: `V001_BaselineIndexes`).
5. **`runner-type`**: `ApplicationRunner` (default). Roda **antes** do app aceitar tráfego HTTP. Crítico: `OutboxPublisher` (`@Scheduled`) só dispara depois.
6. **Manter o `_id` UUID-string das entidades atuais** — sem mexer nisso (já é decisão #4 da migration-mongo).

---

## Tarefas

### Fase 1 — `todo-service`

- [x] **pom.xml**: adicionar `mongock-springboot-v3` + `mongodb-springdata-v4-driver` via `mongock-bom` 5.5.1
- [x] **`TodoServiceApplication.java`**: adicionar `@EnableMongock`
- [x] **`application.yml`**: remover `spring.data.mongodb.auto-index-creation: true`; adicionar bloco `mongock:` (`transactional: false`, `runner-type: initializingbean` — ver Divergências #1 e #2)
- [x] **`infrastructure/migration/V001_BaselineIndexes.java`**: `ChangeUnit` criando índice em `outbox_events.published_at`. `@Execution` + `@RollbackExecution`
- [x] **Anotação `@Indexed` removida** de `OutboxEvent.publishedAt` — schema ownership passou pro Mongock
- [x] **Verificado startup**: Mongock termina antes do Tomcat aceitar tráfego; `V001_baseline_indexes` com `state: EXECUTED` (`executionMillis: 141`); `db.outbox_events.getIndexes()` mostra `published_at_1`
- [x] **Verificado reinício**: Mongock loga "Mongock skipping the data migration. All change set items are already executed"

### Fase 2 — `notification-service`

- [x] **pom.xml**: `mongock-bom` 5.5.1 + `mongock-springboot-v3` + `mongodb-springdata-v4-driver`
- [x] **`NotificationServiceApplication.java`**: `@EnableMongock` adicionado
- [x] **`application.yml`**: `auto-index-creation` removido; bloco `mongock:` adicionado com `runner-type: initializingbean` e `transactional: false` (mesmas decisões da Fase 1)
- [x] **`infrastructure/migration/V001_Baseline.java`**: `ChangeUnit` vazio (no-op declarativo) — registra o ponto zero
- [x] **Verificado startup**: Mongock termina em `18:14:10.639`, Tomcat aceita tráfego em `18:14:13.793` (3s depois); `V001_baseline` com `state: EXECUTED` (`executionMillis: 1`)
- [x] **Verificado end-to-end**: POST `/todos` → outbox publica → notification consome → email enviado com sucesso
- [x] **Reinício idempotente**: V001 já está com `state: EXECUTED` em `mongockChangeLog` — Mongock pula no próximo startup (comportamento já verificado no `todo-service` Fase 1)

### Fase 3 — Atualização do `.spec`

- [x] `02-anti-patterns/mongo-db.md`: 3 referências atualizadas — linhas 12, 94, 129 agora apontam pra `03-patterns/mongock.md` e marcam `@Indexed`/`@CompoundIndex` como proibido neste projeto
- [x] `03-patterns/outbox.md`: nota de dívida substituída por link pra `mongock.md` e referência à `V001_BaselineIndexes`
- [x] **`03-patterns/mongock.md`** criado documentando: dependências (BOM 5.5.1), configuração, anatomia de `ChangeUnit`, convenções de nome, como adicionar nova migration, fluxo de startup, gotchas (DDL em transação + runner-type), sintomas de problema, decisões/trade-offs
- [x] Issue movida para `01-issues/closed/mongock.md`, status `CONCLUÍDO` em 2026-05-22

### Fase 4 — Verificação end-to-end

- [ ] `docker-compose down -v` + build/start → ambos serviços sobem healthy
- [ ] `mongockChangeLog` populada com 1 entry por serviço (em DBs distintos)
- [ ] Fluxo CRUD do Todo + dedupe do notification continuam funcionando
- [ ] Replay manual: derrubar e subir de novo → Mongock loga "skipping" para a V001

---

## Decisões e trade-offs

### 1. Remover `@Indexed` da entidade vs manter ambos
**Escolhido**: remover. Dois donos do schema (anotação + Mongock) gera incoerência silenciosa — se alguém adiciona `@Indexed` num campo e esquece do `ChangeUnit`, em dev funciona (auto-creation se reativado um dia), em prod não. Padrão de mercado: **uma fonte de verdade**, e essa fonte é o `ChangeUnit`.

### 2. Mongock vs Liquibase MongoDB
**Escolhido**: Mongock. Nativo Java, integração direta com Spring Boot, comunidade maior em projetos Spring. Liquibase Mongo existe mas é menos idiomático (XML/YAML/JSON genérico).

### 3. `runner-type: ApplicationRunner` vs `InitializingBean`
**Escolhido**: **`InitializingBean`**. Foi reavaliado após verificação prática (ver Divergência #2). Roda na fase de inicialização dos beans, antes do Tomcat aceitar tráfego — garante que nenhum request chega na app antes das migrations terminarem. `ApplicationRunner` (default) roda **depois** do "Started" log e do Tomcat já estar respondendo, o que é inaceitável em produção.

### 4. Database de controle do Mongock = database de dados
**Escolhido**: mesmo DB. Mongock cria `mongockChangeLog`/`mongockLock` no DB configurado. Manter no `tododb`/`notificationdb` preserva o isolamento por bounded context (cada serviço dono do seu controle).

### 5. Transação na migration
**Escolhido**: **desabilitada** globalmente (`mongock.transactional: false`). Habilitar globalmente parecia certo (replica set existe, atomicidade "grátis"), mas o MongoDB **não permite operações DDL** (`createIndexes`, `createCollection`) dentro de transação multi-documento — falha com `error 72 InvalidOptions: read concern not supported`. Verificado na prática no primeiro startup (ver Divergência #1 abaixo).

Padrão de mercado: migrations de schema/índice rodam sem transação; quando uma `@ChangeUnit` precisa de atomicidade pra migração de **dados** (insert/update/delete em massa), habilita-se na anotação individualmente (`@ChangeUnit(transactional = true)`). Nunca misturar DDL e DML na mesma `ChangeUnit` transacional.

### 6. Criar baseline mesmo onde não há índice novo (`notification-service` V001 vazia)
**Escolhido**: criar. Marca o ponto zero do versionamento. A alternativa (começar com V002 quando precisar) é confusa — toda equipe nova esperaria V001 existir.

---

## Riscos conhecidos

| Risco | Severidade | Mitigação |
|---|---|---|
| Versão do Mongock incompatível com Spring Boot 3.3.5 / Spring Data Mongo 4.3.x | Alta | Validar matriz oficial antes (changelog do Mongock 5.x). Travar versão exata no `pom.xml`, não usar `LATEST` |
| Mongock cria índice antes do `@Document` ser conhecido pelo Spring Data | Baixa | Não acontece — `ApplicationRunner` roda após o contexto do Spring estar pronto |
| `@Indexed` e `ChangeUnit` coexistindo por engano em PR futuro | Média | Adicionar nota no `03-patterns/mongock.md`: nenhuma anotação `@Indexed`/`@CompoundIndex` no projeto daqui pra frente |
| `OutboxPublisher` (`@Scheduled`) dispara antes da migration terminar | Baixa | `@Scheduled` só agenda após `ApplicationReadyEvent`. Mongock termina antes. Validar nos logs (ordem: `Mongock done` → `[OUTBOX] publisher iniciado`) |
| Lock distribuído (`mongockLock`) preso após crash do startup | Média | Mongock tem TTL no lock. Documentar como destravar manualmente se acontecer (`db.mongockLock.deleteMany({})` em dev) |
| Esquecer de migrar produção quando virar produção real | Alta (se virar prod) | Não é um risco *desta* issue, mas: documentar que `ChangeUnit` é imutável depois de aplicado em qualquer ambiente |

---

## Divergências do plano original

Coisas que mudaram durante a implementação e fogem do desenho inicial.

### Divergência #1 — `mongock.transactional` mudou de `true` para `false`

**Plano original (decisão #5)**: habilitar transação globalmente no Mongock, aproveitando o replica set já existente.

**O que aconteceu na verificação**: no primeiro startup do `todo-service`, a `V001_BaselineIndexes` falhou com `MongoCommandException error 72 (InvalidOptions): Command createIndexes does not support this transaction's { readConcern: { level: "majority" } } :: caused by :: read concern not supported`. MongoDB não suporta operações DDL (`createIndexes`, `createCollection`, etc.) dentro de transação multi-documento — restrição do servidor, não do driver.

**Solução adotada**: desligar `mongock.transactional` globalmente. Atualizada a decisão #5. Daqui pra frente:
- Migrations de **schema/índice** rodam sem transação (caso default).
- Migrations de **dados** que precisem de atomicidade habilitam transação na anotação: `@ChangeUnit(id="...", order="...", author="...", transactional=true)`.
- **Nunca** misturar DDL e DML em uma `@ChangeUnit` transacional.

**Consequência**: `V001_BaselineIndexes` é DDL puro, roda sem transação. Comportamento idêntico ao que `auto-index-creation: true` fazia antes.

### Divergência #2 — `runner-type` mudou de `applicationrunner` para `initializingbean`

**Plano original (decisão #3)**: usar `applicationrunner` (default do Mongock).

**O que aconteceu na verificação**: no startup do `todo-service`, os timestamps do log mostraram que **Tomcat aceita tráfego antes do Mongock terminar**:

```
18:04:03.946  [OUTBOX] publisher iniciado
18:04:06.691  Tomcat started on port 8081
18:04:06.734  Started TodoServiceApplication
18:04:07.086  Mongock starting the data migration
18:04:07.260  Mongock has finished
```

O `applicationrunner` do Mongock implementa `ApplicationRunner` do Spring Boot, que roda **depois** da fase de "app pronto" — ou seja, com o Tomcat já aceitando requests. Em produção real, isso significa que um cliente pode chamar a API antes do schema/índices estarem criados.

**Solução adotada**: trocar para `runner-type: initializingbean`. `InitializingBean` roda durante a inicialização dos beans, antes do Tomcat subir. Garante que nenhum request HTTP chega antes das migrations terminarem. Atualizada também a decisão #3.

**Consequência**: o "Started TodoServiceApplication" agora aparece **depois** de "Mongock has finished" no log. A ordem correta é a verificação chave da Fase 1 (esperada na seção "Riscos conhecidos" da issue).

---

## Definição de pronto

- [ ] Todas as tarefas das fases 1-3 marcadas
- [ ] Os dois serviços sobem em volume limpo (`docker-compose down -v` → `up`) sem `auto-index-creation`
- [ ] `mongockChangeLog` tem entry para V001 em ambos DBs
- [ ] Reinício não re-executa V001
- [ ] Fluxo CRUD + notification + outbox publish continua funcionando end-to-end
- [ ] `.spec/03-patterns/mongock.md` criado e linkado dos anti-patterns
- [ ] Issue movida para `closed/` com status `CONCLUÍDO` + data
