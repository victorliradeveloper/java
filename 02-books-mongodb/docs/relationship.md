# Relationships — PostgreSQL vs MongoDB

**Repositórios de referência:**
- [01 — Spring and MongoDB](https://github.com/victorliradeveloper/java/tree/main/02-spring-and-mongodb)
- [02 — Spring Data JPA](https://github.com/victorliradeveloper/java/tree/main/03-spring-data-jpa)
- [03 — Nest and MongoDB](https://github.com/victorliradeveloper/node/tree/main/03-nestjs-mongodb)
- [04 — Nest Data PostgreSQL](https://github.com/victorliradeveloper/node/tree/main/05-nestjs-postgresql)




# Relacionamentos — MongoDB vs JPA/PostgreSQL

## Projeto 02 — MongoDB

```
┌─────────────────────────┐
│      BookDocument       │
├─────────────────────────┤
│ - id: String            │
│ - title: String         │
│ - publisher: @DBRef     │
│ - authors: List<@DBRef> │
│ - review: (embedded)    │
└─────────────────────────┘
          │
          │ @DBRef (ManyToOne)         ┌─────────────────────────┐
          ├───────────────────────────▶│    PublisherDocument    │
          │                            ├─────────────────────────┤
          │                            │ - id: String            │
          │                            │ - name: String          │
          │                            └─────────────────────────┘
          │
          │ @DBRef * (ManyToMany)      ┌─────────────────────────┐
          ├───────────────────────────▶│     AuthorDocument      │
          │                            ├─────────────────────────┤
          │                            │ - id: String            │
          │                            │ - name: String          │
          │                            └─────────────────────────┘
          │
          │ embedded (OneToOne)
          ▼
┌─────────────────────────┐
│     ReviewDocument      │  ← não tem @Document,
├─────────────────────────┤    mora dentro do BookDocument
│ - comment: String       │    na mesma coleção
└─────────────────────────┘
```

> **Em palavras simples:** No MongoDB, um livro é como uma ficha de papel onde você escreve tudo sobre ele.
> O nome da editora e os autores ficam anotados como referências (tipo "veja a ficha X"), e a avaliação
> (review) fica escrita diretamente na mesma ficha — não existe uma ficha separada pra ela.
> Se você quiser saber quais livros uma editora publicou, precisa ir procurar em todas as fichas de livros.

---

## Projeto 03 — JPA / PostgreSQL

```
┌─────────────────────────┐                    ┌─────────────────────────┐
│      PublisherModel     │                    │       AuthorModel       │
│      (TB_PUBLISHER)     │                    │       (TB_AUTHOR)       │
├─────────────────────────┤                    ├─────────────────────────┤
│ - id: UUID              │                    │ - id: UUID              │
│ - name: String          │                    │ - name: String          │
├─────────────────────────┤                    ├─────────────────────────┤
│ @OneToMany              │                    │ @ManyToMany             │
│   mappedBy="publisher"  │                    │   mappedBy="authors"    │
└─────────────────────────┘                    └─────────────────────────┘
          │                                              │
          │ 1                                          * │
          │                                              │
          │         ┌────────────────────────┐          │
          │         │       BookModel         │          │
          │         │       (TB_BOOK)         │          │
          │         ├────────────────────────┤          │
          └────────▶│ - id: UUID             │◀─────────┘
       @ManyToOne   │ - title: String        │   @ManyToMany
       publisher_id │ - publisher_id (FK)    │
       (FK em       ├────────────────────────┤
        TB_BOOK)    │ @ManyToOne publisher   │
                    │ @ManyToMany authors    │
                    │ @OneToOne review       │
                    └────────────────────────┘
                              │ *                    *
                              └──────────┬───────────┘
                                         │
                              ┌──────────▼──────────┐
                              │   tb_book_author    │  ← join table
                              ├─────────────────────┤    gerada pelo @JoinTable
                              │ book_id (FK)        │
                              │ author_id (FK)      │
                              └─────────────────────┘

                    ┌────────────────────────┐
                    │      ReviewModel       │
                    │      (TB_REVIEW)       │
                    ├────────────────────────┤
                    │ - id: UUID             │
                    │ - comment: String      │
                    │ - book_id (FK) ────────┼──── @OneToOne → BookModel
                    └────────────────────────┘
                       ↑ FK fica aqui,
                         não em TB_BOOK
```

> **Em palavras simples:** No PostgreSQL, cada tipo de informação fica numa tabela separada, como gavetas
> diferentes de um arquivo. A tabela de livros guarda um "código da editora" dentro dela mesma para saber
> a qual editora pertence. Já para os autores, existe uma terceira tabela intermediária (`tb_book_author`)
> que funciona como uma lista de pares: "livro X foi escrito pelo autor Y". A avaliação também tem sua
> própria tabela, e ela guarda o "código do livro" para indicar a qual livro pertence.
> Diferente do MongoDB, aqui tudo é separado e interligado por esses códigos (chaves estrangeiras).

---

## Comparação Geral

| Relação | MongoDB (02) | JPA/PostgreSQL (03) |
|---|---|---|
| **Book → Publisher** | `@DBRef` referência no documento Book | `@ManyToOne` + `@JoinColumn(publisher_id)` FK em TB_BOOK |
| **Book ↔ Author** | `@DBRef` lista no documento Book | `@ManyToMany` com join table `tb_book_author` |
| **Book → Review** | Embedded diretamente no BookDocument | `@OneToOne` com FK `book_id` em TB_REVIEW |
| **Publisher → Books** | Sem mapeamento inverso (busca via repository) | `@OneToMany(mappedBy = "publisher")` |
| **Author ↔ Books** | Sem mapeamento inverso | `@ManyToMany(mappedBy = "authors")` |

### Diferença central do Review

| | MongoDB | JPA |
|---|---|---|
| Review | Embutido dentro do Book (mesmo documento) | Tabela separada `TB_REVIEW` com FK `book_id` apontando para `TB_BOOK` |