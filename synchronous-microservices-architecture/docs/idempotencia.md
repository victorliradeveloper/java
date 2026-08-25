# Idempotencia

Dois niveis diferentes neste projeto. Importante nao confundir.

## 1. Idempotency-Key no POST /todos (cliente externo)

Padrao Stripe. O cliente externo (frontend, API consumer) manda um header
`Idempotency-Key` no POST de criar Todo. Se o cliente retentar a mesma
request (timeout, network blip), o servico:

- Detecta a key ja vista,
- Valida que o payload eh o mesmo (hash SHA-256),
- Retorna a resposta cacheada da primeira chamada — sem criar Todo duplicado.

### Como funciona

```
Cliente: POST /todos
         Idempotency-Key: 6e1c... (UUID gerado pelo cliente)
         { "title": "X" }

todo-service:
  1. Calcula hash(fingerprint + payload) -> request_hash
  2. INSERT INTO idempotency_keys (key, request_hash, ...) -> tenta atomicamente

  Caso A — primeira chamada (INSERT teve sucesso):
    3. Executa operacao (cria Todo, notifica downstreams)
    4. Atualiza linha com response_body
    5. Retorna 201 com o body

  Caso B — duplicata (DataIntegrityViolationException):
    3. Busca linha existente
    4a. request_hash bate -> retorna response_body cacheado
    4b. request_hash NAO bate -> 409 Conflict (payload mismatch)
    4c. response_body ainda null -> 409 Conflict (in progress)
```

### Tabela

```sql
CREATE TABLE idempotency_keys (
    key             VARCHAR(255) PRIMARY KEY,  -- valor mandado pelo cliente
    request_hash    VARCHAR(64)  NOT NULL,      -- SHA-256 do payload
    response_status INTEGER,                    -- preenchido apos sucesso
    response_body   TEXT,                       -- response cacheado
    created_at      TIMESTAMP    NOT NULL,
    expires_at      TIMESTAMP    NOT NULL       -- TTL (24h por padrao)
);
```

TTL eh limpo periodicamente pelo `IdempotencyKeyCleanupJob` (Postgres nao
tem expiracao automatica como Redis/Mongo).

### Por que precisa de bean separado pro INSERT

`@Transactional` so' funciona via proxy do Spring. Por isso o
`IdempotencyClaimWriter` eh outra classe — chamada cross-bean garante que o
interceptor transacional roda e a violacao de PK vira `DataIntegrityViolationException`.

## 2. eventId nos downstreams (audit + notification)

Quando o `todo-service` chama `audit-service` ou `notification-service` via
HTTP, gera um UUID por chamada (`eventId`) e o inclui no body.

Se o **Resilience4j Retry** retenta (porque o primeiro request teve timeout
mas o downstream ja processou), o mesmo `eventId` chega no segundo request.
Os downstreams detectam e nao duplicam o trabalho.

### Audit-service

`event_id` eh PK em `todo_audit_log`. O insert usa:

```sql
INSERT INTO todo_audit_log (event_id, ...)
VALUES (...)
ON CONFLICT (event_id) DO NOTHING
```

Retorno do `executeUpdate()`:
- `1` = inseriu (primeira vez visto)
- `0` = duplicata (ja existia)

Em ambos os casos o controller responde 202 — pro caller a operacao foi aceita.

### Notification-service

Tabela `processed_events` (separada do envio do email):

```sql
INSERT INTO processed_events (event_id, processed_at)
VALUES (...)
ON CONFLICT (event_id) DO NOTHING
```

O dedupe acontece **antes** do envio do email (check via `existsById`), e
o registro do `processed_event` eh gravado **depois** do email sair.

#### Por que dedupar depois e nao antes do trabalho

Se gravarmos `processed_events` antes de mandar o email e o SMTP falhar, o
evento fica marcado como "processado" mas sem efeito visivel. Resultado:
**perde raro**.

Marcando depois, no pior caso (crash entre enviar email e gravar) retentamos
e o email sai 2x. **Duplica raro**.

Duplicar eh quase sempre menos pior que perder. Especialmente pra audit/email
de notificacao.

## Resumo

| Camada | Chave | Onde fica | Estrategia |
|---|---|---|---|
| Cliente -> todo-service | `Idempotency-Key` (header HTTP) | tabela `idempotency_keys` | INSERT puro + replay de response cacheada |
| todo-service -> audit | `eventId` (no body) | PK em `todo_audit_log` | `INSERT ... ON CONFLICT DO NOTHING` |
| todo-service -> notification | `eventId` (no body) | PK em `processed_events` | check antes do email + insert depois |

As tres independem entre si. A chave do cliente nao deve vazar pros downstreams
(ela representa a request do cliente, nao o evento de dominio).
