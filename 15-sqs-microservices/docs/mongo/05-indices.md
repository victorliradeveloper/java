# 5. Índices — toda query frequente precisa de um

## O problema antes da solução

Imagine uma collection com **100 mil documentos**. Você quer "todos os pendentes".

**Sem índice**, o Mongo lê **documento por documento** verificando o filtro — 100 mil leituras pra responder uma query. Em produção com milhões de docs, é a morte do app. Isso chama **`COLLSCAN`** (*Collection Scan*).

**Com índice**, o Mongo mantém uma estrutura ordenada (B-tree) separada, que aponta direto pros documentos relevantes. Em vez de ler tudo, faz alguns "pulos" na árvore. 100 mil docs viram ~17 leituras (log₂ 100k ≈ 17). Isso chama **`IXSCAN`** (*Index Scan*).

**Analogia**: lista telefônica em papel. Sem índice = ler página por página procurando "Silva". Com índice = pular direto na borda alfabética: A, B, C, …, S → Silva.

## Como saber se a query está usando índice

Toda query frequente devia passar por `.explain("executionStats")`:

```javascript
db.outbox_events.find({ published_at: null }).explain("executionStats")
```

Olhe `winningPlan.stage`:

| Valor | Significado |
|---|---|
| `IXSCAN` | Usou índice — bom |
| `COLLSCAN` | Leu tudo — bandeira vermelha em coleção grande |
| `FETCH` + `IXSCAN` | Filtrou por índice e depois buscou os documentos completos — OK |

Outras métricas no output que ajudam:
- `totalDocsExamined` — quantos documentos foram lidos (quanto menor, melhor)
- `executionTimeMillis` — tempo total da query

## Regra ESR — para índice de mais de um campo

Quando você indexa **dois ou mais campos juntos** (índice composto), a ordem dos campos no índice importa muito. A regra ESR diz qual ordem usar:

1. **E** — *Equality* — campos com `=` (filtros exatos, ex.: `status: "PENDING"`)
2. **S** — *Sort* — campos do `.sort()` (ex.: `createdAt: -1`)
3. **R** — *Range* — campos com `$gt`/`$lt`/`$in` (ex.: `priority > 5`)

**Por que nessa ordem?**

- **Equality primeiro** porque corta o índice ao mínimo. Você filtra exato → o Mongo entra em um galho específico da árvore e ignora o resto.
- **Sort no meio** porque, dentro daquele galho já cortado, o índice **já está ordenado** — o Mongo lê em ordem sem precisar reordenar nada.
- **Range por último** porque varre uma faixa da árvore (não é um ponto exato). Quanto antes você tiver cortado a árvore, menor a faixa que sobra pra varrer.

## Exemplo passo a passo

Suponha a query:

```javascript
db.todos.find({ userId: "u123", priority: { $gt: 5 } }).sort({ createdAt: -1 })
```

Classificando cada parte:
- `userId: "u123"` → **E** (equality, igualdade exata)
- `priority: { $gt: 5 }` → **R** (range, faixa)
- `.sort({ createdAt: -1 })` → **S** (sort)

Aplicando ESR, o índice ideal é: **`{ userId: 1, createdAt: -1, priority: 1 }`**.

Se você invertesse pra `{ priority: 1, userId: 1, createdAt: -1 }` (R, E, S), o índice **ainda funcionaria**, mas o Mongo teria que varrer várias faixas da árvore. Bem mais lento.

## Neste projeto

O único índice não-implícito hoje é em `outbox_events.published_at` (single-field, criado pela migration [`V001_BaselineIndexes.java`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V001_BaselineIndexes.java)).

Por quê esse índice é crítico? A query do `OutboxPublisher` em [`OutboxEventRepositoryImpl.claimNext`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/repository/OutboxEventRepositoryImpl.java) procura eventos pendentes a cada 2 segundos:

```
filtro:  published_at == null  AND  (lease_expires_at == null OR lease_expires_at < agora)
sort:    created_at ASC
```

Sem índice em `published_at`, **cada poll** do publisher faria `COLLSCAN` na collection inteira. Em produção real, com milhões de eventos retidos pra histórico, isso mataria o cluster em horas.

A anotação `@Indexed` na entidade está **banida** neste projeto — todo índice nasce em `@ChangeUnit` versionada do Mongock, ponto único de verdade do schema.

---

[← Anterior: Embed vs Reference](./04-embed-vs-reference.md) · [Índice](./README.md) · [Próximo: Operadores de update →](./06-operadores-update.md)
