# Keycloak

Keycloak é um servidor de autenticação e autorização open source. Ele centraliza o controle de quem pode acessar o quê, emitindo tokens JWT para aplicações que precisam se comunicar de forma segura.

Neste projeto, o Keycloak atua como **Authorization Server** — é ele quem valida as credenciais do `client-service` e emite o token JWT que o `resource-service` vai aceitar.

---

## Conceitos fundamentais

### Realm
Um realm é um "domínio" isolado de configuração. Cada realm tem seus próprios usuários, clientes e regras. Por padrão existe o realm `master`. Em produção, você criaria um realm separado (ex: `minha-empresa`).

### Client
Representa uma aplicação que vai se autenticar no Keycloak. Neste projeto, o client se chama `cliente` e representa o `client-service`.

### Grant Type
Define como a autenticação acontece. Os principais:

| Grant Type | Quando usar |
|---|---|
| `client_credentials` | Comunicação entre serviços (sem usuário) |
| `authorization_code` | Login de usuário via navegador |
| `password` | Login direto com usuário e senha (não recomendado) |

Neste projeto usamos **client_credentials** — o `client-service` se autentica usando apenas `client_id` e `client_secret`, sem envolver nenhum usuário.

### JWT (JSON Web Token)
O token emitido pelo Keycloak. É uma string codificada em Base64 com três partes:
```
header.payload.signature
```
- **header**: algoritmo usado (ex: RS256)
- **payload**: claims — informações como `sub`, `iss`, `exp`, `client_id`
- **signature**: assinatura digital que garante que o token não foi adulterado

O `resource-service` valida a assinatura consultando a chave pública do Keycloak.

---

## Como o fluxo funciona neste projeto

```
client-service                  Keycloak                  resource-service
     │                              │                              │
     │  POST /token                 │                              │
     │  client_id=cliente           │                              │
     │  client_secret=***           │                              │
     │  grant_type=client_creds     │                              │
     │─────────────────────────────>│                              │
     │                              │                              │
     │  { access_token: "eyJ..." }  │                              │
     │<─────────────────────────────│                              │
     │                              │                              │
     │  GET /hello                  │                              │
     │  Authorization: Bearer eyJ..─────────────────────────────> │
     │                              │                              │
     │                              │  valida token (issuer-uri)   │
     │                              │<─────────────────────────────│
     │                              │  chave pública OK            │
     │                              │─────────────────────────────>│
     │                              │                              │
     │          "Olá, Mundo! eyJ..." │                              │
     │<──────────────────────────────────────────────────────────── │
```

---

## Subindo o Keycloak com Docker

```bash
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:22.0.5 start-dev
```

Acesse: http://localhost:8080 — login com `admin` / `admin`

---

## Configurando o Keycloak para este projeto

### 1. Criar o Client

1. No menu lateral, clique em **Clients** > **Create client**
2. Preencha:
   - **Client ID**: `cliente`
   - **Client type**: `OpenID Connect`
3. Clique em **Next**
4. Em **Capability config**:
   - Desative **Standard flow** (login de usuário)
   - Ative **Service accounts roles** (habilita client_credentials)
5. Clique em **Save**

### 2. Gerar o Client Secret

1. Abra o client `cliente` > aba **Credentials**
2. O campo **Client secret** mostra o secret gerado automaticamente
3. Copie e coloque no `application.yml` do `client-service`

### 3. Verificar o Token URI

O endpoint para obter tokens segue o padrão:
```
http://localhost:8080/realms/{realm}/protocol/openid-connect/token
```

Para o realm `master`:
```
http://localhost:8080/realms/master/protocol/openid-connect/token
```

---

## Testando manualmente

Você pode obter um token diretamente via curl:

```bash
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=cliente" \
  -d "client_secret=cBfBupncSZGm13AB6YIbwMUvrCaxddBf"
```

Resposta:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6...",
  "expires_in": 300,
  "token_type": "Bearer"
}
```

Use o `access_token` para chamar o resource-service:

```bash
curl http://localhost:8000/hello \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6..."
```

---

## Como o resource-service valida o token

O `resource-service` não precisa do `client_secret` — ele apenas verifica se o token foi assinado pelo Keycloak usando a chave pública.

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/master
```

Com essa configuração, o Spring Security:
1. Baixa automaticamente a chave pública do Keycloak em `/.well-known/openid-configuration`
2. Usa essa chave para verificar a assinatura do JWT em cada requisição
3. Rejeita o token se estiver expirado ou adulterado

---

## Inspecionando um JWT

Cole qualquer token em https://jwt.io para ver o conteúdo. O payload de um token gerado por este projeto terá algo como:

```json
{
  "iss": "http://localhost:8080/realms/master",
  "sub": "a1b2c3d4-...",
  "azp": "cliente",
  "exp": 1700000000,
  "iat": 1699999700
}
```

- `iss` — quem emitiu (o Keycloak)
- `sub` — subject (o client, neste caso)
- `azp` — authorized party (client ID)
- `exp` — quando expira (Unix timestamp)
- `iat` — quando foi emitido
