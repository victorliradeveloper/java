# Plano: Migração para MongoDB (Spring Data MongoDB)

Este documento descreve como recriar o projeto **bookstore-jpa** usando MongoDB no lugar do PostgreSQL, mantendo os mesmos conceitos de relacionamento entre entidades, mas adaptando-os ao modelo de documentos.

---

## 1. Contexto: Relacional vs Documental

No projeto JPA, os relacionamentos são gerenciados por chaves estrangeiras e tabelas intermediárias:

| Relacionamento | JPA (Relacional) | MongoDB (Documental) |
|---|---|---|
| Book → Publisher (N:1) | Chave estrangeira `publisher_id` | Referência por `ObjectId` ou embedding |
| Book ↔ Author (N:M) | Tabela intermediária `tb_book_author` | Array de referências ou array de objetos embutidos |
| Book → Review (1:1) | Tabela `TB_REVIEW` com `book_id` | Documento `review` embutido dentro de `book` |
| Publisher → Books (1:N) | `@OneToMany(mappedBy)` | Resolvido por query (sem campo inverso) |

A principal decisão de design no MongoDB é: **embedar ou referenciar**.

---

## 2. Decisões de Modelagem

### Book (documento principal)

`Book` é o agregado central. A regra geral do MongoDB é: se os dados são sempre lidos/gravados juntos, embede; se são entidades independentes com ciclo de vida próprio, referencie.

| Entidade | Decisão | Justificativa |
|---|---|---|
| `Review` | **Embedar** | Review não existe sem o Book; são sempre lidos juntos; cascade é natural |
| `Publisher` | **Referenciar** (`ObjectId`) | Publisher tem vida própria; um Publisher pode ser editado sem alterar todos os seus Books |
| `Author` | **Referenciar** (array de `ObjectId`) | Authors existem independentemente; evita duplicação de dados |

---

## 3. Estrutura de Documentos (Collections)

### Collection: `books`

```json
{
  "_id": ObjectId("..."),
  "title": "Domain-Driven Design",
  "publisher": ObjectId("..."),
  "authors": [
    ObjectId("..."),
    ObjectId("...")
  ],
  "review": {
    "comment": "Leitura obrigatória para qualquer desenvolvedor."
  }
}
```

### Collection: `authors`

```json
{
  "_id": ObjectId("..."),
  "name": "Eric Evans"
}
```

### Collection: `publishers`

```json
{
  "_id": ObjectId("..."),
  "name": "Addison-Wesley"
}
```

> Não existe mais uma collection `reviews` separada — a review vive dentro do documento `book`.

---

## 4. Dependências (pom.xml)

Remover a dependência JPA e o driver PostgreSQL; adicionar Spring Data MongoDB.

**Remover:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

**Adicionar:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

---

## 5. Modelos de Documento

### ReviewDocument (classe interna / embedded)

Não é mais uma entidade separada com ID próprio. Vira uma classe simples sem `@Document`:

```java
public class ReviewDocument {
    private String comment;
    // getters e setters
}
```

### AuthorDocument

```java
@Document(collection = "authors")
public class AuthorDocument {

    @Id
    private String id; // MongoDB usa String (hex do ObjectId) por convenção

    @Indexed(unique = true)
    private String name;

    // getters e setters
}
```

> Sem `@ManyToMany(mappedBy)` — Author não precisa conhecer seus Books. Se necessário, busca-se via query no BookRepository.

### PublisherDocument

```java
@Document(collection = "publishers")
public class PublisherDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    // getters e setters
}
```

> Sem `@OneToMany` — Publisher não carrega lista de Books. Se necessário, usa-se `bookRepository.findByPublisherId(publisherId)`.

### BookDocument

```java
@Document(collection = "books")
public class BookDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String title;

    @DBRef
    private PublisherDocument publisher;

    @DBRef
    private List<AuthorDocument> authors;

    private ReviewDocument review; // embedded, sem @DBRef

    // getters e setters
}
```

> `@DBRef` instrui o Spring Data MongoDB a armazenar a referência como `{ "$ref": "publishers", "$id": ObjectId("...") }` e resolver em tempo de leitura. Alternativa mais performática: guardar apenas o `String id` e resolver manualmente no service.

---

## 6. Repositories

A interface muda de `JpaRepository` para `MongoRepository`. A assinatura é idêntica para os métodos CRUD padrão:

```java
// Antes
public interface BookRepository extends JpaRepository<BookModel, UUID> {}

// Depois
public interface BookRepository extends MongoRepository<BookDocument, String> {
    // Exemplo de query derivada útil:
    List<BookDocument> findByPublisherId(String publisherId);
    List<BookDocument> findByAuthorsId(String authorId);
}
```

O mesmo padrão se aplica a `AuthorRepository` e `PublisherRepository`.  
`ReviewRepository` deixa de existir — reviews são gerenciadas pelo `BookRepository`.

---

## 7. DTO

O `BookRecordDto` sofre uma mudança de tipo: os IDs deixam de ser `UUID` e passam a ser `String` (ObjectId do MongoDB em formato hex):

```java
// Antes
public record BookRecordDto(
    @NotBlank String title,
    @NotNull UUID publisherId,
    @NotNull Set<UUID> authorIds,
    @NotBlank String reviewComment
) {}

// Depois
public record BookRecordDto(
    @NotBlank String title,
    @NotNull String publisherId,
    @NotNull Set<String> authorIds,
    @NotBlank String reviewComment
) {}
```

---

## 8. Service

A lógica de negócio muda pouco. As principais diferenças:

- `@Transactional` ainda pode ser usada (MongoDB suporta transações multi-documento desde a versão 4.0, mas requer replica set configurado). Para simplicidade em ambiente de desenvolvimento, pode ser removida.
- `findById()` retorna `Optional<T>` igual ao JPA — a lógica permanece a mesma.
- Não há mais `reviewRepository.save()` — a review é salva junto com o Book automaticamente.

```java
@Service
public class BookService {

    @Autowired BookRepository bookRepository;
    @Autowired PublisherRepository publisherRepository;
    @Autowired AuthorRepository authorRepository;

    public List<BookDocument> getAllBooks() {
        return bookRepository.findAll();
    }

    public BookDocument saveBook(BookRecordDto dto) {
        var book = new BookDocument();
        book.setTitle(dto.title());

        var publisher = publisherRepository.findById(dto.publisherId()).orElseThrow();
        book.setPublisher(publisher);

        var authors = authorRepository.findAllById(dto.authorIds());
        book.setAuthors(authors);

        var review = new ReviewDocument();
        review.setComment(dto.reviewComment());
        book.setReview(review);

        return bookRepository.save(book);
    }

    public void deleteBook(String id) {
        bookRepository.deleteById(id);
    }
}
```

---

## 9. Controller

Apenas a mudança de tipo dos parâmetros `UUID` → `String`:

```java
@RestController
@RequestMapping("/bookstore/books")
public class BookController {

    @Autowired BookService bookService;

    @GetMapping
    public ResponseEntity<List<BookDocument>> getAllBooks() {
        return ResponseEntity.ok(bookService.getAllBooks());
    }

    @PostMapping
    public ResponseEntity<BookDocument> saveBook(@RequestBody @Valid BookRecordDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookService.saveBook(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteBook(@PathVariable String id) {
        bookService.deleteBook(id);
        return ResponseEntity.ok("Book deleted successfully.");
    }
}
```

---

## 10. Configuração (application.properties)

**Remover** toda configuração de datasource/JPA:

```properties
# Remover:
spring.datasource.url=...
spring.datasource.username=...
spring.datasource.password=...
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

**Adicionar** configuração do MongoDB:

```properties
spring.data.mongodb.uri=mongodb://root:mongo123@localhost:27017/bookstore-mongo?authSource=admin
spring.data.mongodb.database=bookstore-mongo
```

> No MongoDB não há DDL — as collections são criadas automaticamente na primeira inserção. O `@Indexed(unique = true)` é aplicado via `spring.data.mongodb.auto-index-creation=true` (adicionar nas properties).

---

## 11. Docker Compose

```yaml
version: "3.8"

services:
  mongodb:
    image: mongo:7
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: mongo123
      MONGO_INITDB_DATABASE: bookstore-mongo
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db
    healthcheck:
      test: [ "CMD", "mongosh", "--eval", "db.adminCommand('ping')" ]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build: ../spring-data-jpa
    ports:
      - "8080:8080"
    environment:
      SPRING_DATA_MONGODB_URI: mongodb://root:mongo123@mongodb:27017/bookstore-mongo?authSource=admin
    depends_on:
      mongodb:
        condition: service_healthy

volumes:
  mongo_data:
```

---

## 12. Diferenças Conceituais Importantes

### Schema-less vs Schema rígido

No JPA, o schema é obrigatório e validado pelo banco. No MongoDB, não há schema no banco — a validação fica 100% na aplicação (Bean Validation / `@NotBlank`, `@NotNull` no DTO continuam funcionando normalmente).

### Joins vs Lookups

MongoDB não tem `JOIN`. Para buscar um Book com todos os dados do Publisher e Authors resolvidos, o `@DBRef` faz múltiplas queries em background. Em escala, a alternativa é usar a `MongoTemplate` com `$lookup` no aggregation pipeline.

### Transações

Transações multi-documento no MongoDB requerem um **replica set** mesmo em desenvolvimento. Para habilitar:
1. Configurar MongoDB como replica set no Docker (ou usar `mongo:7` com `--replSet rs0`).
2. Manter `@Transactional` no service.
3. Ou remover `@Transactional` e aceitar que cada operação é atômica apenas em nível de documento único.

### IDs

- JPA: `UUID` gerado pelo Hibernate.
- MongoDB: `ObjectId` (12 bytes, gerado pelo driver), representado como `String` em Java. O Spring Data MongoDB cuida da geração automaticamente ao usar `@Id` em um campo `String`.

---

## 13. Resumo das Mudanças por Arquivo

| Arquivo | Mudança |
|---|---|
| `pom.xml` | Troca `data-jpa` + `postgresql` por `data-mongodb` |
| `application.properties` | Troca datasource/JPA por `spring.data.mongodb.*` |
| `docker-compose.yml` | Troca `postgres:16` por `mongo:7` |
| `BookModel` → `BookDocument` | `@Entity/@Table` → `@Document`; `@ManyToOne` → `@DBRef`; `@ManyToMany` → `@DBRef List<>`; `@OneToOne` → campo embutido |
| `AuthorModel` → `AuthorDocument` | Remove `@ManyToMany(mappedBy)`; `UUID` → `String` |
| `PublisherModel` → `PublisherDocument` | Remove `@OneToMany`; `UUID` → `String` |
| `ReviewModel` → `ReviewDocument` | Remove `@Entity/@Table/@OneToOne`; vira POJO simples |
| `*Repository` | `JpaRepository<T, UUID>` → `MongoRepository<T, String>` |
| `ReviewRepository` | **Deletado** — review é gerenciada via BookDocument |
| `BookService` | Remove `reviewRepository`; lógica similar; ajuste de tipos |
| `BookController` | `UUID` → `String` nos path variables |
| `BookRecordDto` | `UUID` → `String` nos campos de ID |
