# Profiles e Ambientes (dev / prod)

## Visão Geral

Este projeto tem **dois ambientes** configurados no `todo-service`:
**dev** (desenvolvimento local) e **prod** (simula deploy). Eles diferem em
logging, fail-fast de configuração, Flyway e Actuator.

O fluxo é controlado por **dois mecanismos diferentes** que muita gente
confunde — e a confusão é a fonte de todo bug de "subi com a config
errada". Esta doc separa os dois e mostra o fluxo que adotamos no projeto.

A regra de ouro:

> **Você nunca edita arquivo `.yml` pra trocar de ambiente.**
> Você só muda *uma* env var (`SPRING_PROFILES_ACTIVE`), e o Spring carrega
> o `application-{valor}.yml` correspondente.

---

## Os dois mecanismos (entenda antes de qualquer coisa)

Existem dois "sistemas de profile" rodando ao mesmo tempo, e eles são
**independentes**:

| Mecanismo | Quem lê | O que controla | Como ativa |
|---|---|---|---|
| **Spring Profiles** | Spring Boot, dentro da JVM | Qual `application-*.yml` é carregado | env var `SPRING_PROFILES_ACTIVE` |
| **Docker `--env-file`** | Docker Compose, *antes* do container subir | Quais valores entram nos `${VAR}` do `docker-compose.yml` | flag `--env-file .env.dev` |

**Como eles se conectam neste projeto**: o `.env.dev`/`.env.prod` contém
a linha `SPRING_PROFILES_ACTIVE=dev` (ou `prod`). O `docker-compose.yml`
lê essa var e injeta no container. O Spring, dentro do container, vê a
env var e ativa o profile correspondente.

```
.env.dev (SPRING_PROFILES_ACTIVE=dev)
       ↓
docker-compose interpola ${SPRING_PROFILES_ACTIVE} no compose.yml
       ↓
Container sobe com a env var SPRING_PROFILES_ACTIVE=dev
       ↓
Spring lê a env var no boot
       ↓
Carrega application.yml + application-dev.yml
```

Trocar o `.env` controla tudo — segredos (SMTP) **e** profile do Spring.

---

## Arquivos no projeto

```
01-microservices/
├── .env.dev                          ← perfil de desenvolvimento (gitignored)
├── .env.prod                         ← perfil de producao  (gitignored)
├── .env.example                      ← template commitado
├── docker-compose.yml                ← interpola ${SPRING_PROFILES_ACTIVE}
└── todo-service/src/main/resources/
    ├── application.yml               ← config comum aos dois ambientes
    ├── application-dev.yml           ← sobrescreve para dev
    └── application-prod.yml          ← sobrescreve para prod
```

> **Decisao do projeto:** nao mantemos um `.env` "default" (sem sufixo).
> O Compose le `.env` automaticamente quando voce roda sem `--env-file`,
> e isso esconde qual ambiente subiu. Removendo o `.env`, todo `up` exige
> a flag — fica impossivel rodar "por engano" no profile errado.

### O que cada `application-*.yml` tem

O Spring **sempre** carrega `application.yml`. Em cima dele, carrega
**também** o `application-{profile}.yml` ativo, e os valores deste último
**sobrescrevem** os do base.

| Aspecto | dev | prod |
|---|---|---|
| Logging do projeto | `DEBUG` (vê fluxo do outbox, idempotência) | `INFO` |
| SQL do Hibernate | `DEBUG` + parâmetros bindados | `WARN` |
| `show-sql` | `true` (Hibernate mostra queries) | `false` |
| Datasource URL | Fallback `localhost:5432` se faltar env | **Sem fallback** — falha rápido no boot |
| Pool de conexões | Default do Hikari | `maximum-pool-size: 20`, etc |
| Flyway `clean` | Permitido (`clean-disabled: false`) | Bloqueado (`clean-disabled: true`) |
| Flyway `validate-on-migrate` | Default | `true` (detecta drift de checksums) |
| Actuator | `include: "*"` (todos os endpoints) | `include: health, info` |
| `health` details | Default | `show-details: never` |

**Por que isso importa**: em prod, se alguém esquecer de setar
`SPRING_DATASOURCE_URL`, o app **não sobe** — em vez de subir apontando
pra um banco errado. Em dev, ele cai no `localhost` e a IDE funciona sem
precisar configurar nada.

---

## Como rodar — fluxo escolhido (`docker-compose --env-file`)

### Dev

```powershell
docker-compose --env-file .env.dev up --build
```

O que acontece passo a passo:

1. Compose lê `.env.dev` → vê `SPRING_PROFILES_ACTIVE=dev` e os `SMTP_*`.
2. Substitui os `${VAR}` no `docker-compose.yml` por esses valores.
3. Container do `todo-service` sobe com `SPRING_PROFILES_ACTIVE=dev` no
   ambiente.
4. Spring Boot no boot lê a env var e carrega
   `application.yml` + `application-dev.yml`.
5. Logs ficam verbosos, Actuator expõe tudo, Flyway permite `clean`.

Como **confirmar** que o profile certo subiu, depois do container start:

```powershell
docker logs todo-service | Select-String "profiles are active"
```

Esperado: `The following 1 profile is active: "dev"`.

### Prod

```powershell
docker-compose --env-file .env.prod up --build
```

A mesma cadeia, mas o `.env.prod` traz `SPRING_PROFILES_ACTIVE=prod`. O
Spring carrega `application-prod.yml`, logs ficam em `INFO`, Actuator
fecha pra só `health`/`info`, banco sem fallback.

### Sem flag (`docker-compose up --build`) — **não use neste projeto**

O Compose carrega `.env` (sem sufixo) por default. Como deletamos o
`.env` propositalmente, rodar `docker-compose up` sem `--env-file` cai
num estado quebrado. Comportamento concreto:

#### 1. Warnings no terminal (um por variável faltando)

```
WARN  The "SPRING_PROFILES_ACTIVE" variable is not set. Defaulting to a blank string.
WARN  The "SMTP_HOST" variable is not set. Defaulting to a blank string.
WARN  The "SMTP_PORT" variable is not set. Defaulting to a blank string.
WARN  The "SMTP_USER" variable is not set. Defaulting to a blank string.
WARN  The "SMTP_PASS" variable is not set. Defaulting to a blank string.
WARN  The "NOTIFICATION_MAIL_TO" variable is not set. Defaulting to a blank string.
```

#### 2. Como os containers ficam configurados

Confira com `docker-compose config` (read-only, não sobe nada):

**`todo-service`:**
```yaml
environment:
  SPRING_PROFILES_ACTIVE: ""   # <-- vazio
```
Spring vê env var vazia → cai no default do `application.yml` (`dev`).
Sobe normalmente, **mas sem ficar claro** que profile está ativo.

**`notification-service`:**
```yaml
environment:
  SPRING_MAIL_HOST: ""
  SPRING_MAIL_PORT: ""
  SPRING_MAIL_USERNAME: ""
  SPRING_MAIL_PASSWORD: ""
  NOTIFICATION_MAIL_FROM: ""
  NOTIFICATION_MAIL_TO: ""
```
JavaMailSender configurado com host vazio. App sobe, mas qualquer tentativa
de enviar email lança `MailSendException: Couldn't connect to host ""`.

#### 3. Resumo do impacto por serviço

| Serviço | Sobe? | Funciona? |
|---|---|---|
| `todo-service` | Sim | Sim — cai em `dev` por acaso (default do `application.yml`) |
| `notification-service` | Sim | **Não** — falha ao enviar qualquer email |
| `audit-service` | Sim | Sim — não usa vars do `.env` |
| `api-gateway` | Sim | Sim — não usa vars do `.env` |
| `eureka-server` | Sim | Sim — não usa vars do `.env` |

#### 4. Como inspecionar sem subir nada

```powershell
docker-compose config                       # renderiza com .env (atualmente vazio)
docker-compose --env-file .env.dev config   # renderiza com .env.dev resolvido
```

O `config` é a melhor ferramenta pra entender "o que o Compose vai
realmente passar pros containers" antes de rodar `up`.

#### Conclusão

**Sempre passe `--env-file`.** O comando sem flag é mantido aqui só pra
documentar o anti-fluxo. A "punição" por esquecer (warnings + email
quebrado) é proposital — força o hábito de ser explícito sobre o ambiente.

---

## Rodar local pela IDE / Maven (sem Docker)

Quando você roda `./mvnw spring-boot:run` ou clica em Debug no IntelliJ,
o Docker não está envolvido. Os `.env.*` são ignorados — eles são do
Compose, não do Spring.

Nesse caso, o profile é decidido por:

1. Env var `SPRING_PROFILES_ACTIVE` na sua sessão de shell (se setada).
2. Senão, o default no `application.yml`: `profiles.active: dev`.

### Rodando dev local (caso comum)

Não precisa fazer nada. O default já é dev.

```powershell
./mvnw spring-boot:run -pl todo-service
```

### Forçar prod local (pra testar fail-fast)

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
./mvnw spring-boot:run -pl todo-service
```

**Esperado**: o app **não sobe**, porque `SPRING_DATASOURCE_URL` não
está setada e o `application-prod.yml` não tem fallback. Esse é o
comportamento desejado — falhar no boot em vez de subir errado.

Pra ver o app subir em prod localmente, você teria que setar também:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/tododb"
$env:SPRING_DATASOURCE_USERNAME="todo_user"
$env:SPRING_DATASOURCE_PASSWORD="todo_pass"
$env:SPRING_PROFILES_ACTIVE="prod"
./mvnw spring-boot:run -pl todo-service
```

### Via IDE (IntelliJ)

`Run` → `Edit Configurations…` → seu run config → `VM options`:
```
-Dspring.profiles.active=prod
```

Ou em `Environment variables`:
```
SPRING_PROFILES_ACTIVE=prod
```

---

## Cadeia de precedência do `SPRING_PROFILES_ACTIVE`

Quando várias fontes definem o profile, o Spring usa esta ordem (a mais
forte ganha):

1. CLI: `--spring.profiles.active=prod` (passado ao `java -jar`)
2. System property: `-Dspring.profiles.active=prod` (VM options)
3. Env var do SO: `SPRING_PROFILES_ACTIVE=prod`
4. `application.yml`: `spring.profiles.active: dev` ← **default do projeto**

Por isso o `application.yml` tem:
```yaml
profiles:
  active: ${SPRING_PROFILES_ACTIVE:dev}
```

Isso significa: *"leia a env var; se não vier, use `dev`"*. **Você nunca
edita essa linha** — só sobrescreve a env var.

---

## Troubleshooting

### "Subi pelo compose mas o profile não é o que eu esperava"

```powershell
docker logs todo-service | Select-String "profile"
```

Procure: `The following 1 profile is active: "..."`.

Causas comuns:
- Esqueceu o `--env-file` e o `.env` default não tem `SPRING_PROFILES_ACTIVE`.
- Setou env var no shell (`$env:SPRING_PROFILES_ACTIVE`) que está vazando
  pro compose com outro valor — feche e abra o terminal pra limpar.

### "App não sobe em prod, erro de datasource"

É **esperado** se você não setou as env vars de banco. O `application-prod.yml`
remove o fallback de propósito. Sete as vars ou rode em dev.

### ".env.dev existe mas o Compose não está lendo"

O `--env-file` precisa ser passado **antes** do subcomando:

```powershell
# certo
docker-compose --env-file .env.dev up

# errado — flag depois do subcomando, ignorada
docker-compose up --env-file .env.dev
```

### "Mudei application-dev.yml e nada mudou"

Quando o app roda via Docker, ele carrega o `.yml` que foi empacotado
**no `mvn package` durante o `docker build`**. Edição local não tem
efeito até você rebuildar:

```powershell
docker-compose --env-file .env.dev up --build
```

O `--build` é o que pega seu `.yml` editado.

### "Como sei o que cada profile vai aplicar sem rodar?"

Em dev, o Actuator está aberto:

```
GET http://localhost:8081/actuator/env
```

Mostra todas as propriedades resolvidas (com a fonte de cada uma:
`application.yml`, `application-dev.yml`, env var, etc).

---

## Indo além

### `@Profile` em código

Você pode anotar beans pra existirem só em um ambiente:

```java
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {
    // popula dados fake no boot — so em dev
}
```

```java
@Configuration
@Profile("!prod")  // tudo menos prod
public class DevCorsConfig { ... }
```

Útil pra: seeders de dev, mocks de integração, endpoints de admin que
não devem ir pra prod.

### Profiles compostos

Você pode ativar **múltiplos** profiles ao mesmo tempo:

```
SPRING_PROFILES_ACTIVE=prod,metrics-export
```

O Spring carrega `application-prod.yml` + `application-metrics-export.yml`.
Útil pra "fatiar" comportamento ortogonal (ex: ligar exportação de
métricas separadamente do ambiente base).

### `application-{profile}.properties` vs `.yml`

Tanto faz, o Spring lê os dois. Este projeto usa `.yml` pela hierarquia
mais legível.

---

## Resumo prático

| Tarefa | Comando |
|---|---|
| Rodar dev em container | `docker-compose --env-file .env.dev up --build` |
| Rodar prod em container | `docker-compose --env-file .env.prod up --build` |
| Rodar dev pela IDE | Só `Debug` no `TodoServiceApplication` (default já é dev) |
| Forçar prod local | `$env:SPRING_PROFILES_ACTIVE="prod"` + env vars de DB |
| Confirmar qual profile subiu | `docker logs todo-service \| Select-String "profile is active"` |
| Ver toda a config resolvida | `GET /actuator/env` (só funciona em dev) |

---

## Referências

- [debug.md](debug.md) — como debugar a app local ou em container
- [application.yml](../todo-service/src/main/resources/application.yml) — config base + default profile
- [application-dev.yml](../todo-service/src/main/resources/application-dev.yml) — overrides de dev
- [application-prod.yml](../todo-service/src/main/resources/application-prod.yml) — overrides de prod
- [.env.example](../.env.example) — template das variáveis do Compose
