# Contrato

## O que é

O **contrato** é o **acordo abstrato** entre consumer e provider: *quais campos a
mensagem precisa ter, de que tipo e em que formato*. É o conceito; o
[Pact](./pact.md) (arquivo JSON) é a forma concreta dele.

> Contrato = o acordo. Pact = o arquivo que expressa o acordo.

## No projeto

O contrato cobre o corpo do `TodoEvent`:

| Campo | Regra no contrato | Por quê |
|---|---|---|
| `todoId` | qualquer string (type matcher) | o consumer só precisa que exista e seja string |
| `title` | qualquer string (type matcher) | idem |
| `action` | **valor exato** (`CREATED`/`UPDATED`/`DELETED`) | é nele que o consumer ramifica |
| `occurredAt` | regex ISO-8601 | evita acoplar a uma precisão de fração de segundo |

## Consumer-driven

Quem dita o contrato é **quem consome**. Faz sentido: o consumer só depende dos
campos que realmente lê — não tem opinião sobre o resto. Se o provider mandar
campos a mais, tudo bem; o contrato só quebra se faltar algo que o consumer
espera.

## Matchers, não valores fixos

A escolha de usar **matchers** (tipo/regex) em vez de valores fixos é o que torna
o contrato robusto: ele valida a **forma** do payload, não um exemplo específico.
Só `action` é fixo, porque ali o valor exato é parte do significado.

## Relacionados

- [consumer.md](./consumer.md) — quem define o contrato
- [provider.md](./provider.md) — quem é verificado contra o contrato
- [pact.md](./pact.md) — o arquivo que materializa este contrato
