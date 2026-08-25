# Pact Broker / PactFlow

## O que é

O **Pact Broker** é um servidor central que **armazena e versiona** os
[Pacts](./pact.md) (os contratos). Em vez de o [consumer](./consumer.md) e o
[provider](./provider.md) trocarem um arquivo no disco, eles conversam com o
broker:

```
CONSUMER  ──publish──▶  PACT BROKER  ──fetch──▶  PROVIDER
                         (versiona,
                          guarda histórico,
                          responde can-i-deploy)
```

**PactFlow** é a versão SaaS comercial do broker (do mesmo time), com recursos a
mais (secrets, RBAC, bi-directional contracts). O conceito é o mesmo.

## Por que existe

Sem broker, o contrato é um arquivo (`@PactFolder("../pacts")`) — funciona, mas:

- O provider só verifica se o arquivo **estiver presente** (alguém precisa rodar
  o consumer antes, ou commitar o JSON).
- Não há versionamento por branch/ambiente, nem histórico de quem quebrou o quê.
- Não dá pra responder a pergunta de ouro: **`can-i-deploy`** — "esse serviço
  pode subir em produção sem quebrar quem depende dele?".

O broker resolve tudo isso sendo a **fonte de verdade** dos contratos.

## No projeto

Foi adicionado ao `docker-compose.yml` reaproveitando o Postgres existente:

```yaml
pact-broker:
  image: pactfoundation/pact-broker:latest
  ports:
    - "9292:9292"
  environment:
    PACT_BROKER_DATABASE_ADAPTER: postgres
    PACT_BROKER_DATABASE_HOST: postgres
    PACT_BROKER_DATABASE_NAME: pactbrokerdb   # criado pelo init-databases.sql
    PACT_BROKER_DATABASE_USERNAME: todo_user
    PACT_BROKER_DATABASE_PASSWORD: todo_pass
    PACT_BROKER_BASIC_AUTH_USERNAME: pact
    PACT_BROKER_BASIC_AUTH_PASSWORD: pact
  depends_on:
    postgres:
      condition: service_healthy
```

| | |
|---|---|
| UI / API | http://localhost:9292 |
| Login | `pact` / `pact` (basic auth) |
| Banco | `pactbrokerdb` no Postgres do projeto |

> ⚠️ O `init-databases.sql` só roda quando o volume do Postgres está **vazio**.
> Se o banco já existe, crie o `pactbrokerdb` à mão (ver
> [como-rodar.md](./como-rodar.md)).

## Fluxo com broker (vs. arquivo)

| Etapa | Sem broker (atual) | Com broker |
|---|---|---|
| Consumer gera | `pacts/...json` no disco | **publica** o pact no broker |
| Provider lê | `@PactFolder("../pacts")` | **baixa** do broker (`@PactBroker`) |
| Versionamento | diff no git | por versão/branch/tag no broker |
| Deploy seguro | — | `pact-broker can-i-deploy` |

## Estado atual

O broker está **provisionado** no compose, mas os testes ainda usam o modelo de
**arquivo** (`@PactDirectory` / `@PactFolder`). Migrar é o próximo passo:

1. Consumer: publicar o pact no broker (Maven plugin `pact-jvm-provider` /
   `pact_broker-client`, ou a task de publish).
2. Provider: trocar `@PactFolder("../pacts")` por `@PactBroker(url = ...)`.
3. CI: rodar `can-i-deploy` antes de promover cada serviço.

Com isso, fecha-se a **5ª entidade** e o fluxo passa a espelhar produção de
verdade.

## Relacionados

- [pact.md](./pact.md) — o artefato que o broker versiona
- [consumer.md](./consumer.md) — publica no broker
- [provider.md](./provider.md) — baixa do broker
- [como-rodar.md](./como-rodar.md) — comandos e setup do banco
