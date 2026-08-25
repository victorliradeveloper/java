# Hibernate ddl-auto=update com coluna NOT NULL em tabela existente

## O problema

Quando uma coluna `NOT NULL` é adicionada a uma entidade JPA e a tabela já existe no banco com dados, o Hibernate tenta executar:

```sql
ALTER TABLE todo ADD COLUMN completed boolean NOT NULL
```

O PostgreSQL rejeita esse comando porque as linhas existentes não teriam valor para a nova coluna, violando a constraint `NOT NULL`.

O Hibernate registra o erro no log mas não interrompe a aplicação. A coluna simplesmente não é adicionada. Nas próximas queries, o banco retorna:

```
ERROR: column t1_0.completed does not exist
```

## Por que acontece

O `ddl-auto=update` foi criado para ambientes de desenvolvimento. Ele adiciona colunas e tabelas que faltam, mas **não consegue** adicionar uma coluna `NOT NULL` em uma tabela que já tem dados sem um valor `DEFAULT` definido.

## A solução

Adicionar `columnDefinition` na anotação `@Column` para informar ao banco o valor padrão ao criar a coluna:

```java
// Antes — falha se a tabela já tiver dados
@Column(name = "completed", nullable = false)
private boolean completed;

// Depois — funciona mesmo com dados existentes
@Column(name = "completed", nullable = false, columnDefinition = "boolean not null default false")
private boolean completed;
```

Com isso, o PostgreSQL executa:

```sql
ALTER TABLE todo ADD COLUMN completed boolean NOT NULL DEFAULT false
```

E preenche as linhas existentes com `false` automaticamente.

## Solução correta em produção

Em produção nunca use `ddl-auto=update`. Use uma ferramenta de migração como **Flyway** ou **Liquibase**, que versiona cada alteração de schema:

```sql
-- V2__add_completed_to_todo.sql
ALTER TABLE todo ADD COLUMN completed boolean NOT NULL DEFAULT false;
```

Isso garante controle total sobre o que muda no banco, quando muda, e permite rollback.

## Comandos Docker úteis

```bash
# Para os containers sem perder os dados
docker compose down

# Para os containers e deleta os volumes (reset completo do banco)
docker compose down -v
```
