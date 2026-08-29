# Pattern — Versionamento de schema Mongo com Mongock

Schema, índices e migrações de dados em MongoDB versionadas como código Java, executadas no startup de cada serviço **antes** de aceitar tráfego.

Implementado em [`todo-service`](../../todo-service) e [`notification-service`](../../notification-service). Substitui o `spring.data.mongodb.auto-index-creation: true` (anti-pattern de produção, ver `02-anti-patterns/mongo-db.md`).

---

## Problema que resolve

`auto-index-creation: true` é OK em dev/POC mas falha em produção real por três motivos:

1. **Sem histórico**: não há como saber quando um índice foi criado, em qual ambiente, ou se já rodou.
2. **Acoplado à anotação**: índice só existe enquanto a `@Indexed` estiver na entidade — refactor que remove o campo deixa o índice órfão no banco.
3. **Sem migração de dados**: criar índice é o caso trivial. O que fazer quando precisar normalizar um campo de 10 milhões de documentos? `auto-index-creation` não ajuda.

Mongock resolve os três: cada mudança é uma classe `@ChangeUnit` versionada, registrada em uma coleção de controle (`mongockChangeLog`), com transição declarada (`@Execution` e `@RollbackExecution`).

---

## Componentes

### 1. Dependências (`pom.xml`)

BOM gerencia versão única, e os dois artefatos vêm sem `<version>`:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.mongock</groupId>
      <artifactId>mongock-bom</artifactId>
      <version>5.5.1</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.mongock</groupId>
    <artifactId>mongock-springboot-v3</artifactId>
  </dependency>
  <dependency>
    <groupId>io.mongock</groupId>
    <artifactId>mongodb-springdata-v4-driver</artifactId>
  </dependency>
</dependencies>
```

`mongock-springboot-v3` = runner para Spring Boot 3.x. `mongodb-springdata-v4-driver` = adapter para Spring Data MongoDB 4.x. Trocar de versão exige verificar matriz de compatibilidade.

### 2. Ativação

`@EnableMongock` na classe `@SpringBootApplication` (ver [`TodoServiceApplication.java`](../../todo-service/src/main/java/com/microservices/todo/TodoServiceApplication.java) e [`NotificationServiceApplication.java`](../../notification-service/src/main/java/com/microservices/notification/NotificationServiceApplication.java)).

### 3. Configuração (`application.yml`)

```yaml
mongock:
  migration-scan-package: com.microservices.todo.infrastructure.migration
  runner-type: initializingbean
  transactional: false
  enabled: true
```

| Propriedade | Valor adotado | Por quê |
|---|---|---|
| `migration-scan-package` | `<pacote>.infrastructure.migration` por serviço | Restringe scan — sem isso, Mongock varre todo o classpath |
| `runner-type` | `initializingbean` | Roda **antes** do Tomcat aceitar tráfego (ver Gotcha #2) |
| `transactional` | `false` | MongoDB não suporta DDL dentro de transação (ver Gotcha #1) |
| `enabled` | `true` | Default. Pode-se desligar em testes unitários |

### 4. ChangeUnit

Uma classe Java por mudança. Vive em `<serviço>/src/main/java/.../infrastructure/migration/`.

```java
@ChangeUnit(id = "V001_baseline_indexes", order = "001", author = "victor")
public class V001_BaselineIndexes {

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps("outbox_events")
                .ensureIndex(new Index().on("published_at", Sort.Direction.ASC));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps("outbox_events").dropIndex("published_at_1");
    }
}
```

Exemplo real: [`V001_BaselineIndexes.java`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V001_BaselineIndexes.java).

### 5. Coleções de controle (criadas pelo Mongock automaticamente)

| Coleção | Função |
|---|---|
| `mongockChangeLog` | Histórico: cada execução vira uma entry com `changeId`, `state` (`EXECUTED`/`FAILED`), `executionMillis`, `errorTrace`, `executionHostname` |
| `mongockLock` | Lock distribuído — impede que duas instâncias subindo ao mesmo tempo apliquem a mesma migração. TTL embutido |

Ficam **no mesmo database** do serviço (`tododb` / `notificationdb`) — cada bounded context é dono do seu controle.

---

## Convenções deste projeto

- **Nome da classe**: `V{NNN}_{SnakeCaseDescricao}`. Ex.: `V001_BaselineIndexes`, `V002_AddProcessedAtTtl`.
- **`id` do `@ChangeUnit`**: `V{NNN}_snake_case_descricao` (lowercase). Tem que ser único no histórico — uma vez `EXECUTED`, nunca mais reaproveitar.
- **`order`**: zero-padded string de 3 dígitos (`"001"`, `"002"`, ...). Determina ordem de execução quando há múltiplas migrações pendentes no mesmo startup.
- **`author`**: nome de quem criou (no projeto: `victor`). Aparece no log.
- **Não misturar DDL e DML** em uma `@ChangeUnit` transacional (ver Gotcha #1).
- **Anotação `@Indexed`/`@CompoundIndex` na entidade está banida** — schema é responsabilidade exclusiva do Mongock. Anotação + Mongock = dois donos, comportamento incoerente.

---

## Como adicionar uma nova migração

1. Criar classe em `<serviço>/src/main/java/.../infrastructure/migration/V{NNN}_<descricao>.java`.
2. `@ChangeUnit(id = "...", order = "...", author = "...")` no topo. **Se for migração de dados que precisa de atomicidade**, adicionar `transactional = true`. **Nunca** misturar DDL nesse caso.
3. Métodos `@Execution` e `@RollbackExecution` (obrigatório — Mongock falha se faltar rollback).
4. Parâmetros injetados pelo container Spring (qualquer bean — `MongoTemplate`, repositórios, services). Recomenda-se `MongoTemplate` para schema/índices.
5. **Não chamar services de negócio dentro de migration** — eles podem disparar eventos, audit logs, listeners de domínio que não pertencem ao contexto de migração.
6. Rodar o serviço local com volume limpo: nova entry deve aparecer em `mongockChangeLog` com `state: EXECUTED`.

---

## Fluxo end-to-end no startup

```
docker-compose up todo-service
  ├─ Spring context inicia
  ├─ @EnableMongock dispara o InitializingBean do Mongock
  │   ├─ Mongock acquire lock em mongockLock
  │   ├─ Lê mongockChangeLog → identifica migrations pendentes
  │   ├─ Loop em ordem crescente de `order`:
  │   │    ├─ Executa @Execution
  │   │    ├─ Em caso de sucesso: grava entry com state=EXECUTED
  │   │    └─ Em caso de falha: state=FAILED, abort, lança exception (app não sobe)
  │   ├─ Release lock
  │   └─ "Mongock has finished" no log
  ├─ Tomcat start (aceita tráfego HTTP) — só agora
  └─ "Started TodoServiceApplication"
```

Ordem importa: `Mongock has finished` **sempre antes** de `Tomcat started`. Inverteu → bug de configuração (provavelmente `runner-type: applicationrunner`).

---

## O que se pode mudar e o que **não** se pode

### Pode mudar livre

- **Adicionar novas `@ChangeUnit`** — comportamento esperado, qualquer dev faz.
- **Alterar `migration-scan-package`** — se mover o pacote de migrations.
- **`enabled: false` em testes** — para isolar fixtures que não querem Mongock.

### Cuidado redobrado

- **Mudar `runner-type` para `applicationrunner`** — Tomcat passa a aceitar tráfego antes das migrations terminarem. Em prod, requests entram com schema desatualizado.
- **Habilitar `transactional: true` globalmente** — falha em qualquer migration de schema (ver Gotcha #1).
- **`@ChangeUnit` já aplicada em qualquer ambiente é imutável** — alterar o corpo de uma migration `EXECUTED` é o pior pecado do versionamento de schema. Ambientes ficam dessincronizados sem aviso. Sempre criar nova `@ChangeUnit` para corrigir/evoluir.

### Não funciona

- **Reaproveitar `id` ou `order` de migration deletada** — Mongock não detecta como "nova", pula achando que já rodou.
- **`@ChangeUnit` em pacote fora do `migration-scan-package`** — silenciosamente ignorada.
- **Misturar `@Indexed` e `@ChangeUnit` que criam o mesmo índice** — comportamento depende da ordem de startup do Spring; resultado imprevisível.

---

## Gotchas (lições da implementação)

### Gotcha #1 — DDL não roda dentro de transação no MongoDB

Habilitar `mongock.transactional: true` parece certo (replica set existe, atomicidade "grátis"), mas o MongoDB **não permite** `createIndexes`, `createCollection` e outros comandos DDL dentro de transação multi-documento. Falha com:

```
MongoCommandException error 72 (InvalidOptions):
'Command createIndexes does not support this transaction's
{ readConcern: { level: "majority" } } :: caused by :: read concern not supported'
```

**Regra**:
- Schema/índice → `transactional: false` (caso default).
- Migração de **dados** que precisa de atomicidade → `@ChangeUnit(transactional = true)` individual, e a `@ChangeUnit` deve ser **DML pura** (zero `createIndex`, `createCollection`).

### Gotcha #2 — `runner-type: applicationrunner` libera tráfego antes das migrations

Default do Mongock é `applicationrunner`, que implementa `ApplicationRunner` do Spring Boot. Spring Boot considera o app `Started` antes do `ApplicationRunner` rodar — ou seja, **Tomcat já aceita HTTP antes do Mongock começar**.

**Sintoma observado** durante a implementação:
```
18:04:06.691  Tomcat started on port 8081     ← tráfego aceito
18:04:07.086  Mongock starting migration       ← migrations começam
18:04:07.260  Mongock has finished
```

**Correção**: `runner-type: initializingbean`. `InitializingBean.afterPropertiesSet()` roda na fase de inicialização dos beans, antes do `ServletWebServerApplicationContext` subir o Tomcat.

### Gotcha #3 — `@ChangeUnit` exige `@RollbackExecution`

Mongock falha na inicialização se a classe não tiver `@RollbackExecution`. Mesmo em ChangeUnits "no-op" (baseline) — o método pode ser vazio, mas tem que existir.

### Gotcha #4 — Lock preso em crash

Se um startup crashar **enquanto Mongock segura o lock** (raro, mas possível), `mongockLock` fica com uma entry. Mongock tem TTL no lock para se autorrecuperar, mas em dev às vezes é mais rápido apagar:

```javascript
db.getSiblingDB("tododb").mongockLock.deleteMany({})
```

**Não fazer isso em produção sem confirmar que ninguém está aplicando migração agora.**

---

## Sintomas de problema

| Sintoma | Causa provável | Onde olhar |
|---|---|---|
| App não sobe, erro `MongockException` em `V###` no log | Migration falhou na execução | `state` da entry em `mongockChangeLog`, `errorTrace` do mesmo doc |
| App sobe sem rodar Mongock (sem `Mongock has finished` no log) | `@EnableMongock` faltando ou `enabled: false` | classe `*Application.java` e `application.yml` |
| Migration nova ignorada | Classe fora de `migration-scan-package` ou faltando `@ChangeUnit` | comparar pacote real com `application.yml` |
| Tomcat sobe antes do Mongock | `runner-type: applicationrunner` | `application.yml` |
| Startup trava em "Mongock trying to acquire the lock" | Lock preso de crash anterior, ou outra instância subindo simultânea | `db.<servicedb>.mongockLock.find()` |
| Erro `error 72 (InvalidOptions)` em migration de índice | `transactional: true` global ou na `@ChangeUnit` | `application.yml` e annotation da classe |
| Duas instâncias aplicaram a mesma migration | Lock burlado (não devia acontecer) — investigar imediatamente | logs das duas instâncias + `executionHostname` no changelog |

---

## Decisões / trade-offs registrados

- **Mongock 5.x via BOM**: única fonte de verdade pra versão. Sem `LATEST` no `pom.xml`.
- **Database de controle = database de dados**: cada serviço dono do seu `mongockChangeLog`/`mongockLock`. Preserva isolamento por bounded context.
- **`@Indexed` na entidade banido**: schema é responsabilidade exclusiva do Mongock. Anotação + Mongock juntos = comportamento incoerente.
- **`V001` mesmo quando vazio**: `notification-service` não tem nada para criar, mas existe `V001_baseline` como marcador. Equipe nova espera V001 existir; começar em V002 confunde.
- **Sem auto-rollback automático**: se uma migration falha, app não sobe; intervenção manual. Mongock tem `@RollbackExecution` para reversão explícita via API, mas não invocamos no startup.

---

## Quando usar este pattern

**Use sempre que**:
- Houver MongoDB no projeto e schema/índices forem parte da definição do serviço.
- O serviço precisar subir em múltiplos ambientes (dev/staging/prod) com schema consistente.
- Houver chance de múltiplas instâncias subindo simultaneamente (lock distribuído resolve).

**Não precisa quando**:
- POC absoluta de uma semana, descartável (mas a moratória é curta — assim que o projeto sobrevive ao primeiro mês, adote).
- Coleção 100% temporária criada por job batch e descartada (mesmo assim, índices da app principal deveriam estar versionados).

---

## Referências

- Issue de implementação: [`01-issues/closed/mongock.md`](../01-issues/closed/mongock.md) (incluindo Divergências #1 e #2)
- Anti-pattern Mongo (`@Indexed`/`auto-index-creation`): [`02-anti-patterns/mongo-db.md`](../02-anti-patterns/mongo-db.md)
- Documentação oficial: https://docs.mongock.io/
