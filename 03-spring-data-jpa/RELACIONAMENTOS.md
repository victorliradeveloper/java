# Relacionamentos entre Tabelas no Banco de Dados

## O que é um relacionamento?

Imagina que você tem uma planilha de livros e outra de editoras. Em algum momento você precisa saber **qual editora publicou qual livro**. Para isso, você cria uma ligação entre as duas planilhas. Isso é um relacionamento.

No banco de dados relacional, relacionamentos são feitos através de **chaves estrangeiras** — colunas que guardam o ID de outra tabela.

---

## As tabelas desse projeto

Este projeto tem 4 tabelas:

```
TB_PUBLISHER  (Editoras)
TB_BOOK       (Livros)
TB_AUTHOR     (Autores)
TB_REVIEW     (Avaliações)
TB_BOOK_AUTHOR (tabela intermediária criada automaticamente pelo JPA)
```

---

## Tipos de relacionamento

Existem 3 tipos principais:

| Tipo | Significado | Exemplo real |
|---|---|---|
| **OneToOne** | Um para Um | Um livro tem uma avaliação |
| **ManyToOne / OneToMany** | Muitos para Um / Um para Muitos | Muitos livros pertencem a uma editora |
| **ManyToMany** | Muitos para Muitos | Um livro tem vários autores, um autor tem vários livros |

---

## 1. ManyToOne — Book e Publisher (Muitos para Um)

### O que significa?
- **Muitos livros** podem pertencer a **uma editora**
- Uma editora pode ter vários livros, mas cada livro tem apenas uma editora

### Como fica no código?

**BookModel.java** — o lado "Muitos" (Many)
```java
@ManyToOne
@JoinColumn(name = "publisher_id")
private PublisherModel publisher;
```

**PublisherModel.java** — o lado "Um" (One)
```java
@OneToMany(mappedBy = "publisher")
private Set<BookModel> books = new HashSet<>();
```

### Como fica no banco de dados?

**TB_PUBLISHER**
| id | name |
|---|---|
| `aaa-111` | Editora Abril |
| `bbb-222` | Alta Books |

**TB_BOOK**
| id | title | publisher_id |
|---|---|---|
| `xxx-001` | Clean Code | `aaa-111` ← ID da Editora Abril |
| `xxx-002` | Design Patterns | `aaa-111` ← ID da Editora Abril |
| `xxx-003` | Java Efetivo | `bbb-222` ← ID da Alta Books |

> A coluna `publisher_id` em TB_BOOK é a **chave estrangeira**. Ela aponta para o ID do Publisher.
> O JPA preenche ela automaticamente quando você faz `book.setPublisher(publisher)`.

### Quem é o "dono" do relacionamento?

O **Book** é o dono, pois é ele que tem a coluna `publisher_id` na sua tabela.
O `mappedBy = "publisher"` no Publisher significa: "quem manda aqui é o campo `publisher` lá no Book".

---

## 2. ManyToMany — Book e Author (Muitos para Muitos)

### O que significa?
- **Um livro pode ter vários autores**
- **Um autor pode ter vários livros**

### Por que precisamos de uma terceira tabela?

Você não consegue representar isso com uma coluna simples. Imagine:

**TB_BOOK** com coluna `author_id`
| id | title | author_id |
|---|---|---|
| `xxx-001` | Clean Code | ??? |

Se um livro tem 3 autores, qual ID você coloca? Não dá. Por isso o JPA cria uma **tabela intermediária**.

### Como fica no código?

**BookModel.java** — o lado dono (quem define a tabela intermediária)
```java
@ManyToMany
@JoinTable(
    name = "tb_book_author",               // nome da tabela intermediária
    joinColumns = @JoinColumn(name = "book_id"),        // coluna que aponta para TB_BOOK
    inverseJoinColumns = @JoinColumn(name = "author_id") // coluna que aponta para TB_AUTHOR
)
private Set<AuthorModel> authors = new HashSet<>();
```

**AuthorModel.java** — o lado inverso
```java
@ManyToMany(mappedBy = "authors")
private Set<BookModel> books = new HashSet<>();
```

### Como fica no banco de dados?

**TB_BOOK**
| id | title |
|---|---|
| `xxx-001` | Clean Code |
| `xxx-002` | Design Patterns |

**TB_AUTHOR**
| id | name |
|---|---|
| `yyy-001` | Robert Martin |
| `yyy-002` | Erich Gamma |
| `yyy-003` | John Vlissides |

**TB_BOOK_AUTHOR** (tabela intermediária — criada automaticamente)
| book_id | author_id |
|---|---|
| `xxx-001` | `yyy-001` | ← Clean Code foi escrito por Robert Martin |
| `xxx-002` | `yyy-002` | ← Design Patterns foi escrito por Erich Gamma |
| `xxx-002` | `yyy-003` | ← Design Patterns também foi escrito por John Vlissides |

> Cada linha da tabela intermediária representa **uma ligação** entre um livro e um autor.
> O JPA gerencia essa tabela automaticamente. Você nunca manipula ela diretamente.

---

## 3. OneToOne — Book e Review (Um para Um)

### O que significa?
- **Um livro tem no máximo uma avaliação**
- **Uma avaliação pertence a apenas um livro**

### Como fica no código?

**ReviewModel.java** — o lado dono (quem tem a chave estrangeira)
```java
@OneToOne
@JoinColumn(name = "book_id")
private BookModel book;
```

**BookModel.java** — o lado inverso
```java
@OneToOne(mappedBy = "book", cascade = CascadeType.ALL)
private ReviewModel review;
```

### Como fica no banco de dados?

**TB_BOOK**
| id | title |
|---|---|
| `xxx-001` | Clean Code |
| `xxx-002` | Design Patterns |

**TB_REVIEW**
| id | comment | book_id |
|---|---|---|
| `zzz-001` | Livro excelente! | `xxx-001` ← aponta para Clean Code |
| `zzz-002` | Muito denso... | `xxx-002` ← aponta para Design Patterns |

> Cada livro aparece no máximo uma vez na coluna `book_id` de TB_REVIEW.
> Isso garante o "um para um".

### O que é `cascade = CascadeType.ALL`?

Significa que as operações no Book se propagam para o Review automaticamente:
- Se você **salvar** um Book com Review → o Review também é salvo
- Se você **deletar** um Book → o Review também é deletado

---

## Resumo visual de todos os relacionamentos

```
TB_PUBLISHER
     |
     | (OneToMany) — uma editora tem muitos livros
     |
TB_BOOK ————————————— TB_BOOK_AUTHOR ————————————— TB_AUTHOR
     |        (ManyToMany via tabela intermediária)
     |
     | (OneToOne) — um livro tem uma avaliação
     |
TB_REVIEW
```

---

## Resumo das chaves estrangeiras

| Tabela | Coluna | Aponta para |
|---|---|---|
| TB_BOOK | `publisher_id` | TB_PUBLISHER.id |
| TB_BOOK_AUTHOR | `book_id` | TB_BOOK.id |
| TB_BOOK_AUTHOR | `author_id` | TB_AUTHOR.id |
| TB_REVIEW | `book_id` | TB_BOOK.id |

---

## Conceito importante: quem é o "dono" do relacionamento?

O **dono** é sempre o lado que tem a **chave estrangeira** na tabela, ou seja, quem tem o `@JoinColumn`.

O lado **inverso** usa `mappedBy` para dizer "vai lá no outro lado ver a configuração".

| Relacionamento | Dono (tem @JoinColumn) | Inverso (tem mappedBy) |
|---|---|---|
| Book x Publisher | BookModel | PublisherModel |
| Book x Author | BookModel | AuthorModel |
| Book x Review | ReviewModel | BookModel |
