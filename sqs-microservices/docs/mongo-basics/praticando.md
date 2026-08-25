# Praticando MongoDB — 10 operações para começar

Exercícios para ganhar familiaridade com o `mongosh` usando o `tododb` que já está populado nesse projeto.

## Setup

**Pré-requisito:** `mongosh` instalado (`winget install MongoDB.Shell` no Windows, ou via Docker:
`docker exec -it todo-mongo mongosh`).

**Conectar a partir do host:**

```bash
mongosh "mongodb://localhost:27018/tododb?replicaSet=rs0&directConnection=true"
```

> O `directConnection=true` é necessário porque o replica set anuncia o host interno `mongo:27017`. Sem ele, o driver tenta resolver esse nome a partir da sua máquina e falha.

**Boa prática de cautela:** antes de qualquer write, sempre rode um `find` ou `countDocuments` com o mesmo filtro. Mongo NÃO pede `WHERE` — `updateMany({})` ou `deleteMany({})` sem filtro varrem a coleção inteira sem reclamar.

---

## 1. Reconhecer o terreno

Listar databases, coleções, e contar documentos. Operações 100% seguras (read-only).

```javascript
show dbs                    // todos os databases do cluster
use tododb                  // entra no database
show collections            // coleções (todos, idempotency_keys, mongockChangeLog...)
db.todos.countDocuments()   // quantos docs na coleção
db.todos.stats()            // tamanho, indices, storage
```

## 2. Inspecionar documentos com `find` e `findOne`

```javascript
db.todos.findOne()                          // primeiro doc (qualquer um)
db.todos.find().limit(3)                    // 3 docs
db.todos.find().pretty()                    // todos, formatado (cuidado em coleções grandes)
db.todos.find({}, { title: 1, _id: 0 })     // projeção: só o campo title
```

## 3. Filtros básicos

```javascript
// Igualdade exata
db.todos.find({ completed: false })

// Operadores de comparação
db.todos.find({ createdAt: { $gte: ISODate("2026-06-07") } })

// Regex (case-insensitive)
db.todos.find({ title: { $regex: /circuit/i } })

// Campo existe / não existe
db.todos.find({ description: { $exists: true } })

// Combinando com $and / $or
db.todos.find({
  $or: [{ title: /Resilience/i }, { title: /circuit/i }]
})
```

## 4. Ordenar, paginar, contar

```javascript
// Top 3 mais recentes
db.todos.find().sort({ createdAt: -1 }).limit(3)

// Paginação (skip + limit)
db.todos.find().sort({ createdAt: -1 }).skip(3).limit(3)

// Contar matches de um filtro (antes de qualquer update destrutivo!)
db.todos.countDocuments({ completed: false })
```

## 5. Inserir um documento

```javascript
db.todos.insertOne({
  _id: "manual-01",
  title: "Praticar mongosh",
  description: "Treinar CRUD no tododb",
  completed: false,
  createdAt: new Date(),
  updatedAt: new Date()
})
```

> O `_id` pode ser qualquer string/ObjectId/número desde que seja único. Se omitir, o Mongo gera um `ObjectId` automaticamente. Como a app gera UUID, evite criar `_id` que possa colidir com o formato da aplicação.

## 6. Inserir vários (batch)

```javascript
db.todos.insertMany([
  { _id: "manual-02", title: "Estudar agregações", completed: false, createdAt: new Date(), updatedAt: new Date() },
  { _id: "manual-03", title: "Configurar indices", completed: false, createdAt: new Date(), updatedAt: new Date() },
  { _id: "manual-04", title: "Backup com mongodump", completed: true, createdAt: new Date(), updatedAt: new Date() }
])
```

## 7. Atualizar um único documento (`updateOne` + `$set`)

```javascript
// 1) PRIMEIRO: confirme qual doc vai mudar
db.todos.findOne({ _id: "manual-01" })

// 2) DEPOIS: aplique o update
db.todos.updateOne(
  { _id: "manual-01" },
  { $set: { completed: true, updatedAt: new Date() } }
)

// Verifique
db.todos.findOne({ _id: "manual-01" })
```

Outros operadores úteis: `$inc` (incrementa numérico), `$unset` (remove campo), `$push` (adiciona em array), `$rename` (renomeia campo).

## 8. Atualizar vários documentos (`updateMany`) — operação destrutiva

**Sempre conte antes:**

```javascript
// 1) Quantos docs serão afetados?
db.todos.countDocuments({ completed: false })

// 2) Inspecione uma amostra
db.todos.find({ completed: false }).limit(2)

// 3) Só então aplique
db.todos.updateMany(
  { completed: false },
  { $set: { completed: true, updatedAt: new Date() } }
)
// Retorna: { matchedCount, modifiedCount } — confira que bate com o count anterior.
```

> Esse update marca TODOS os todos pendentes como concluídos. Em prod, esse tipo de comando exige PR + migração Mongock, não shell ad-hoc.

## 9. Deletar documentos — operação destrutiva

```javascript
// Deletar UM doc específico (id conhecido)
db.todos.deleteOne({ _id: "manual-04" })

// Deletar vários — SEMPRE conte antes
db.todos.countDocuments({ _id: /^manual-/ })   // quantos vou apagar?
db.todos.deleteMany({ _id: /^manual-/ })       // só depois de confirmar

// JAMAIS rode isso sem entender:
// db.todos.deleteMany({})   ← apaga TUDO. Sem confirmação. Sem trigger.
```

> Para reverter um delete acidental, só com backup (`mongodump` antes) ou oplog replay no replica set.

## 10. Agregação — analisando dados (read-only)

Pipeline que conta todos por estado de conclusão:

```javascript
db.todos.aggregate([
  { $group: { _id: "$completed", total: { $sum: 1 } } },
  { $sort: { total: -1 } }
])
```

Top 3 mais antigos com projeção:

```javascript
db.todos.aggregate([
  { $sort: { createdAt: 1 } },
  { $limit: 3 },
  { $project: { _id: 1, title: 1, createdAt: 1 } }
])
```

Bonus — contar audits por `todoId` no `auditdb`:

```javascript
use auditdb
db.audit_events.aggregate([
  { $group: { _id: "$todoId", eventos: { $sum: 1 } } },
  { $sort: { eventos: -1 } }
])
```

---

## Checklist antes de qualquer write em prod

1. `find(filtro)` ou `findOne(filtro)` para ver o que vai mudar.
2. `countDocuments(filtro)` — bate com sua expectativa?
3. `mongodump --uri ... --collection X --out backup/` (writes massivos).
4. Para mudanças permanentes, codifica como **ChangeUnit do Mongock** — não no shell.
5. Aplica primeiro em local/staging com cópia do dump de prod.

## Próximos passos

- Criar índices: `db.todos.createIndex({ title: 1 })` e ver impacto com `.explain("executionStats")`.
- Transações multi-coleção (replica set já está habilitado).
- Migrações versionadas via Mongock — ver `notification-service/.../migration/V001_Baseline.java` como exemplo neste projeto.
