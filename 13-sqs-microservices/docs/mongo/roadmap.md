# Roadmap MongoDB — Operações do Dia a Dia

Guia prático das operações que você vai repetir todo dia trabalhando com MongoDB. Os exemplos usam a sintaxe do `mongosh` (shell), mas o mesmo modelo vale pro driver Java/Spring Data com pequenas variações.

---

## 1. Conectar e navegar

```js
// conectar
mongosh "mongodb://localhost:27017"

show dbs                  // lista bancos
use minhaApp              // seleciona/cria banco (lazy: só existe quando insere algo)
show collections          // lista collections do db atual
db.usuarios.countDocuments() // conta docs
```

> Pensa em **database → collection → document**. Equivale a *schema → tabela → linha*, mas o document é JSON (BSON), sem schema fixo.

---

## 2. Insert — criar documentos

```js
// um doc
db.usuarios.insertOne({ nome: "Victor", idade: 30, tags: ["dev"] })

// vários
db.usuarios.insertMany([
  { nome: "Ana", idade: 25 },
  { nome: "Bruno", idade: 40 }
])
```

- `_id` é gerado automático (`ObjectId`) se você não passar.
- `insertMany` por padrão é **ordered**: para no primeiro erro. Use `{ ordered: false }` pra continuar.

---

## 3. Find — ler documentos

```js
db.usuarios.find()                              // todos
db.usuarios.find({ nome: "Victor" })            // filtro por igualdade
db.usuarios.findOne({ _id: ObjectId("...") })   // um único doc

// projeção (escolhe campos)
db.usuarios.find({}, { nome: 1, _id: 0 })

// ordenação, limite, paginação
db.usuarios.find().sort({ idade: -1 }).skip(20).limit(10)
```

### Operadores de query mais usados

| Operador | Uso |
|---|---|
| `$eq`, `$ne` | igual / diferente |
| `$gt`, `$gte`, `$lt`, `$lte` | comparações numéricas/data |
| `$in`, `$nin` | valor está / não está numa lista |
| `$exists` | campo existe |
| `$regex` | match por regex |
| `$and`, `$or`, `$not`, `$nor` | composição lógica |

```js
db.usuarios.find({ idade: { $gte: 18, $lt: 65 } })
db.usuarios.find({ tags: { $in: ["dev", "ops"] } })
db.usuarios.find({ $or: [ { idade: { $lt: 18 } }, { vip: true } ] })
```

---

## 4. Update — alterar documentos

Sempre use **operadores** (`$set`, `$inc`, etc.). Passar um objeto cru substitui o documento inteiro.

```js
// atualizar 1
db.usuarios.updateOne(
  { _id: ObjectId("...") },
  { $set: { idade: 31 }, $currentDate: { atualizadoEm: true } }
)

// atualizar vários
db.usuarios.updateMany(
  { ativo: false },
  { $set: { arquivado: true } }
)

// upsert: insere se não existir
db.usuarios.updateOne(
  { email: "x@y.com" },
  { $set: { nome: "X" } },
  { upsert: true }
)
```

### Operadores de update essenciais

| Operador | O que faz |
|---|---|
| `$set` / `$unset` | define / remove campo |
| `$inc` | incrementa número (`{ $inc: { saldo: -10 } }`) |
| `$mul` | multiplica |
| `$rename` | renomeia campo |
| `$push` / `$pull` | adiciona / remove de array |
| `$addToSet` | push sem duplicar |
| `$pop` | remove primeiro/último do array |
| `$currentDate` | seta timestamp atual |

---

## 5. Delete — remover documentos

```js
db.usuarios.deleteOne({ _id: ObjectId("...") })
db.usuarios.deleteMany({ ativo: false })
```

> Cuidado: `deleteMany({})` apaga **tudo** na collection. Não tem `WHERE` esquecido que dói mais.

---

## 6. findOneAndUpdate / findOneAndDelete — operações atômicas

Quando você precisa **ler e modificar no mesmo passo** (ex: pegar próximo item de fila, decrementar estoque):

```js
db.pedidos.findOneAndUpdate(
  { status: "PENDENTE" },
  { $set: { status: "PROCESSANDO" } },
  { returnDocument: "after", sort: { criadoEm: 1 } }
)
```

Esse é o padrão pra evitar race conditions sem precisar de transação.

---

## 7. Índices — performance no dia a dia

```js
db.usuarios.createIndex({ email: 1 }, { unique: true })  // único
db.usuarios.createIndex({ nome: 1, idade: -1 })          // composto
db.usuarios.createIndex({ descricao: "text" })           // full-text
db.usuarios.createIndex({ criadoEm: 1 }, { expireAfterSeconds: 3600 }) // TTL

db.usuarios.getIndexes()
db.usuarios.dropIndex("email_1")
```

Use `.explain("executionStats")` pra ver se a query está usando índice:

```js
db.usuarios.find({ email: "x@y.com" }).explain("executionStats")
```

Se ver `COLLSCAN`, você está varrendo a collection inteira — falta índice.

---

## 8. Aggregation pipeline — relatórios e transformações

Pipeline = lista de estágios; a saída de um vira a entrada do próximo.

```js
db.pedidos.aggregate([
  { $match: { status: "PAGO" } },                       // filtra
  { $group: {                                            // agrupa
      _id: "$clienteId",
      total: { $sum: "$valor" },
      qtd:   { $sum: 1 }
  }},
  { $sort: { total: -1 } },                              // ordena
  { $limit: 10 },                                        // top 10
  { $project: { clienteId: "$_id", total: 1, _id: 0 } }  // formata saída
])
```

### Estágios que você vai usar quase sempre

| Estágio | Pra que serve |
|---|---|
| `$match` | filtro (igual ao `find`) — coloque cedo no pipeline |
| `$project` | escolhe/renomeia/calcula campos |
| `$group` | agregações: `$sum`, `$avg`, `$min`, `$max`, `$push` |
| `$sort` | ordenação |
| `$limit` / `$skip` | paginação |
| `$lookup` | "join" com outra collection |
| `$unwind` | quebra array em vários docs |
| `$addFields` | adiciona/calcula campos sem remover os outros |

---

## 9. Modelagem — embed vs reference

Decisão diária que define performance:

- **Embed** (documento aninhado) quando os dados são lidos juntos, têm tamanho previsível e não são compartilhados.
  ```js
  { _id: 1, nome: "Pedido", itens: [ { sku: "A", qtd: 2 } ] }
  ```
- **Reference** (guardar só o `_id` do outro doc) quando os dados crescem sem limite, são compartilhados, ou atualizados em vários lugares.

Regra de bolso: **"o que é lido junto, fica junto"**. Evite documentos crescendo sem limite (limite hard do BSON é 16 MB).

---

## 10. Transações (multi-document)

Necessárias só quando você precisa garantir atomicidade entre **várias collections** ou **vários docs**. Antes disso, tente resolver com `findOneAndUpdate` num único doc.

```js
const session = db.getMongo().startSession()
session.startTransaction()
try {
  db.contas.updateOne({ _id: 1 }, { $inc: { saldo: -100 } }, { session })
  db.contas.updateOne({ _id: 2 }, { $inc: { saldo:  100 } }, { session })
  session.commitTransaction()
} catch (e) {
  session.abortTransaction()
} finally {
  session.endSession()
}
```

Requer **replica set** (até standalone local precisa rodar como rs pra transação funcionar).

---

## 11. Ferramentas do dia a dia

- **mongosh** — shell oficial, ótimo pra script e troubleshooting rápido.
- **MongoDB Compass** — GUI; bom pra explorar, ver explain visual e construir aggregation pipelines.
- **mongodump / mongorestore** — backup e restore.
- **mongoimport / mongoexport** — JSON/CSV em massa.

---

## Ordem sugerida pra estudar

1. CRUD básico (`insert`, `find`, `update`, `delete`)
2. Operadores de query e update
3. Índices + `explain`
4. Aggregation pipeline (`$match`, `$group`, `$project`, `$lookup`)
5. Modelagem (embed vs reference)
6. `findOneAndUpdate` atômico
7. Transações
8. Replica set / sharding (quando for pra produção)

Faz o 1–4 com sua mão num banco local que 80% do trabalho do dia a dia já está coberto.
