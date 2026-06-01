# Profiles e Ambientes (dev / prod)

O `todo-service` roda em dois ambientes: **dev** (local) e **prod** (simula
deploy). Você troca de ambiente mudando **uma env var**
(`SPRING_PROFILES_ACTIVE`) — nunca editando `.yml`.

---

## Os dois mecanismos

Existem dois "sistemas de profile" rodando juntos, independentes:

| Mecanismo | Quem lê | O que controla |
|---|---|---|
| **Spring Profiles** | Spring Boot na JVM | Qual `application-*.yml` é carregado |
| **Docker `--env-file`** | Compose, antes do container subir | Valores dos `${VAR}` no `docker-compose.yml` |

Eles se conectam assim:

```
.env.dev (SPRING_PROFILES_ACTIVE=dev)
   → Compose interpola no docker-compose.yml
   → Container sobe com a env var
   → Spring carrega application.yml + application-dev.yml
```

Trocar o `.env` controla tudo: segredos (SMTP) **e** profile do Spring.

---

## Arquivos

```
01-microservices/
├── .env.dev        ← gitignored, dev
├── .env.prod       ← gitignored, prod
├── .env.example    ← commitado, template
├── docker-compose.yml
└── todo-service/src/main/resources/
    ├── application.yml         ← config comum
    ├── application-dev.yml     ← overrides de dev
    └── application-prod.yml    ← overrides de prod
```

> **Sem `.env` default**: removido propositalmente. Sem ele, todo
> `docker-compose up` exige `--env-file` — impossível subir "por engano"
> no profile errado.

---

## Diferenças dev vs prod

O Spring **sempre** carrega `application.yml` + o `application-{profile}.yml`
ativo (este sobrescreve o base).

| Aspecto | dev | prod |
|---|---|---|
| Logs do projeto | `DEBUG` | `INFO` (default) |
| Datasource | Fallback `localhost` | Sem fallback (fail-fast) |
| Flyway `clean` | Permitido | Bloqueado |
| Actuator | Tudo exposto | Só `health`, `info` |

---

## Como rodar

### Docker (fluxo principal)

```powershell
docker-compose --env-file .env.dev  up --build    # dev
docker-compose --env-file .env.prod up --build    # prod
```

Conferir qual profile subiu:
```powershell
docker logs todo-service | Select-String "profile is active"
```

### IDE / Maven local (sem Docker)

Default já é `dev`, não precisa fazer nada:
```powershell
./mvnw spring-boot:run -pl todo-service
```

Pra forçar prod local (vai falhar sem env vars de DB — esperado):
```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
./mvnw spring-boot:run -pl todo-service
```

No IntelliJ: `Edit Configurations…` → VM options: `-Dspring.profiles.active=prod`.

---

## Princípios dos arquivos

**1. Se o valor é igual ao default, não coloque no `.yml`.**
Linhas que repetem default mentem — sugerem que "alguém escolheu", quando o
framework já faz sozinho. Exceção: decisões de segurança valem documentar
(ex: `flyway.clean-disabled: true` em prod, mesmo sendo default).

**2. Chave repetida em dev e prod ≠ duplicação ruim.**
Quando a chave existe nos dois com valores diferentes (ex: `datasource.url`,
`flyway.clean-disabled`, `actuator.exposure.include`), é o **propósito do
profile** — override intencional, como em OOP.

---

## Ligar SQL sob demanda (sem reiniciar)

Dev não loga SQL por default — o outbox poller faz `SELECT` a cada 2s, o
log fica ilegível. Pra inspecionar uma query pontual, use o Actuator:

```powershell
# liga
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/actuator/loggers/org.hibernate.SQL" `
  -ContentType "application/json" -Body '{"configuredLevel":"DEBUG"}'

# rode sua operação...

# desliga
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/actuator/loggers/org.hibernate.SQL" `
  -ContentType "application/json" -Body '{"configuredLevel":null}'
```

> Esse padrão é prática real de produção — em prod, mexer no
> `logging.level` e reiniciar é custoso; o `/actuator/loggers` permite
> ajuste em runtime sem downtime.

---

## Troubleshooting

**"Profile errado subiu"** — confira nos logs (`Select-String "profile is active"`).
Causa comum: esqueceu o `--env-file`. A flag tem que vir **antes** do
subcomando: `docker-compose --env-file .env.dev up`, não o contrário.

**"App não sobe em prod, erro de datasource"** — esperado se faltar env
var. O `application-prod.yml` remove fallback de propósito.

**"Mudei `application-*.yml` e nada mudou no container"** — Docker
empacota o yml no `mvn package` durante o build. Use `--build`:
`docker-compose --env-file .env.dev up --build`.

**"Quero ver toda config resolvida"** — em dev, `GET /actuator/env` mostra
cada propriedade com a origem (qual yml, env var, etc).

---

## Referências

- [debug.md](debug.md) — debug local e remoto
- [application.yml](../todo-service/src/main/resources/application.yml)
- [application-dev.yml](../todo-service/src/main/resources/application-dev.yml)
- [application-prod.yml](../todo-service/src/main/resources/application-prod.yml)
