# Migrações de banco com Flyway

## O problema

Durante o desenvolvimento, o schema do banco muda com frequência — novas colunas, novas tabelas, novas constraints. Com `ddl-auto=update`, o Hibernate tenta aplicar essas mudanças automaticamente, mas falha em casos comuns:

**Exemplo real que aconteceu neste projeto:**

A coluna `completed` foi adicionada à entidade `Todo` com `nullable = false`. O Hibernate tentou executar:

```sql
ALTER TABLE todo ADD COLUMN completed boolean NOT NULL
```

O PostgreSQL rejeitou porque a tabela já tinha dados — sem um valor `DEFAULT`, as linhas existentes ficariam com `null` numa coluna `NOT NULL`. O Hibernate registrou o erro no log e continuou. A coluna nunca foi criada. Toda query na tabela passou a falhar com:

```
ERROR: column t1_0.completed does not exist
```

A única saída na época era recriar o banco inteiro com `docker compose down -v`.

---

## Por que `ddl-auto=update` não é confiável

| Operação | Comportamento |
|----------|--------------|
| Criar tabela nova | Funciona |
| Adicionar coluna nullable | Funciona |
| Adicionar coluna `NOT NULL` sem default | Falha silenciosamente |
| Remover coluna | Nunca remove |
| Alterar tipo de coluna | Não faz |
| Controle do histórico de mudanças | Nenhum |

Em produção, `ddl-auto=update` é proibido — qualquer alteração errada pode corromper dados ou derrubar a aplicação.

---

## A solução: Flyway

O Flyway é uma ferramenta de migração de banco. Cada alteração no schema vira um arquivo SQL versionado e imutável. O Flyway controla o que já foi executado e roda apenas o que é novo.

### Como funciona

O Flyway mantém uma tabela `flyway_schema_history` no banco:

```
version | description       | installed_on        | success
--------|-------------------|---------------------|--------
1       | create tables     | 2026-04-29 10:00:00 | true
2       | seed data         | 2026-04-29 10:00:01 | true
```

Na próxima subida da aplicação, ele vê que V1 e V2 já rodaram e não faz nada. Se existir um V3, ele executa apenas o V3.

### Convenção de nomenclatura

```
V{versão}__{descrição}.sql
```

- `V` maiúsculo obrigatório
- Dois underscores separando versão da descrição
- Versão pode ser `1`, `1.1`, `2`, etc.

---

## Como foi configurado neste projeto

### 1. Dependências no `pom.xml`

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

### 2. `application.properties`

```properties
# Hibernate não mexe mais no schema — só valida se está correto
spring.jpa.hibernate.ddl-auto=validate
```

O `validate` faz o Hibernate checar se as tabelas e colunas batem com as entidades. Se algo estiver errado, a aplicação não sobe — o que é melhor do que subir e falhar nas queries.

### 3. Testes desativam o Flyway

Em `src/test/resources/application.properties`:

```properties
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=create-drop
```

Os testes usam H2 em memória. O Flyway usa SQL específico de PostgreSQL que o H2 não entende. Por isso o Flyway é desativado nos testes e o Hibernate recria o schema do zero a cada execução.

### 4. Scripts de migração

Ficam em `src/main/resources/db/migration/`:

```
db/migration/
├── V1__create_tables.sql   — cria users, todo, sequência e índices
└── V2__seed_data.sql       — insere usuário admin e 51 todos de exemplo
```

---

## Como adicionar uma nova migração

Sempre que o schema precisar mudar, crie um novo arquivo com a próxima versão:

```
V3__add_priority_to_todo.sql
```

```sql
ALTER TABLE todo ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM';
```

Na próxima subida, o Flyway executa apenas esse arquivo. O banco é atualizado sem perder dados, sem recriar nada.

**Regra importante:** nunca edite um arquivo de migração que já foi executado. O Flyway guarda o checksum de cada arquivo. Se o conteúdo mudar, a aplicação falha na subida com erro de checksum. Migrações são imutáveis — correções entram em um arquivo novo.

---

## Fluxo completo ao mudar o schema

```
1. Adicionar campo na entidade Java
2. Criar V{n}__descricao.sql com o ALTER TABLE correspondente
3. Reiniciar a aplicação
4. Flyway detecta o arquivo novo e executa
5. Hibernate valida — schema bate com entidade → OK
```

Sem `docker compose down -v`. Sem perda de dados.
