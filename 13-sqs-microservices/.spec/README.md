# `.spec/` — Manual vivo do projeto

Leitura obrigatória pra IA que vai editar este repo. Fora do controle de versão (`.gitignore`).

---

## Documentos

**Anti-patterns** (leia antes de sugerir código):

- [`02-anti-patterns/general.md`](./02-anti-patterns/general.md) — regras transversais
- [`02-anti-patterns/java-spring.md`](./02-anti-patterns/java-spring.md) — Java 21 + Spring Boot 3
- [`02-anti-patterns/mongo-db.md`](./02-anti-patterns/mongo-db.md) — Mongo 7 + Spring Data

**Patterns adotados** (forma certa neste projeto):

- Arquivos vão em [`03-patterns/`](./03-patterns/)

**Issues em aberto** (trabalho em andamento ou planejado):

- Arquivos vão em [`01-issues/open/`](./01-issues/open/)

---

## Quando atualizar

| Mudou… | Atualize |
|---|---|
| Comportamento de pattern | `03-patterns/<nome>.md` |
| Decisão sobre o stack | `02-anti-patterns/<stack>.md` |
| Resolveu issue | mover `open/` → `closed/`, status no topo |
| Decisão não-óbvia | issue nova em `01-issues/open/` |
