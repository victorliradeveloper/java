# Event-Driven Development — Sistema de Emails Transacionais

Sistema de emails orientado a eventos com dois microserviços Spring Boot se comunicando via RabbitMQ.

---

## Arquitetura

```
[user-service]  →  RabbitMQ  →  [email-service]

Ação do usuário        Evento publicado       Email enviado
─────────────────      ────────────────       ─────────────
Cadastro           →   user.registered    →   "Bem-vindo!"
Login              →   user.login         →   "Novo acesso detectado"
Compra             →   order.created      →   "Pedido confirmado"
Reset de senha     →   user.password      →   "Redefinir sua senha"
```

---

## Pré-requisitos

- Docker e Docker Compose instalados
- Java 21 (apenas se quiser rodar sem Docker)
- Maven (apenas se quiser rodar sem Docker)

---

## Como rodar com Docker

### 1. Clone o repositório e entre na pasta

```bash
cd 10-event-driven-development
```

### 2. Suba tudo com um único comando

```bash
docker compose up --build
```

O `--build` compila os serviços antes de subir. Na primeira vez leva alguns minutos.

A ordem de inicialização é controlada automaticamente:

```
postgres    → fica healthy
rabbitmq    → fica healthy
               ↓
user-service  (aguarda postgres + rabbitmq)
email-service (aguarda rabbitmq)
```

### 3. Acesse os serviços

| Serviço | URL |
|---|---|
| Swagger (user-service) | http://localhost:8080/swagger-ui.html |
| Painel RabbitMQ | http://localhost:15672 |
| user-service API | http://localhost:8080 |
| email-service | http://localhost:8081 |

Credenciais do RabbitMQ: `guest` / `guest`

### 4. Para encerrar

```bash
docker compose down
```

---

## Como testar o fluxo

Com tudo rodando, abra o Swagger em `http://localhost:8080/swagger-ui.html`.

### Cadastro (dispara email de boas-vindas)

```
POST /api/v1/auth/register
```
```json
{
  "name": "Victor",
  "email": "victor@teste.com",
  "password": "123456"
}
```

A resposta vai retornar um `token`. Guarde-o para as próximas chamadas.

### Login (dispara email de novo acesso)

```
POST /api/v1/auth/login
```
```json
{
  "email": "victor@teste.com",
  "password": "123456"
}
```

### Criar pedido (dispara email de confirmação)

Clique em **Authorize** no Swagger e cole o token recebido no login.

```
POST /api/v1/orders
```
```json
{
  "description": "Notebook Dell XPS",
  "amount": 8500.00
}
```

### Reset de senha (dispara email de redefinição)

```
POST /api/v1/users/password-reset
```

Sem body — usa o JWT do usuário autenticado.

---

## Verificar os emails

Os emails chegam no **Mailtrap** (sandbox de email para desenvolvimento).

Acesse `https://mailtrap.io` → **Email Testing** → **Inboxes** e veja os emails chegando em tempo real.

---

## Verificar as mensagens no RabbitMQ

Acesse o painel em `http://localhost:15672` (guest / guest).

Em **Queues** você vê as filas:

| Fila | Evento |
|---|---|
| `email.registered.queue` | Cadastro de usuário |
| `email.login.queue` | Login de usuário |
| `email.order.queue` | Criação de pedido |
| `email.password.queue` | Reset de senha |

---

## Estrutura do Projeto

```
10-event-driven-development/
├── docker-compose.yml
├── user-service/          # Publica eventos no RabbitMQ (porta 8080)
│   ├── Dockerfile
│   └── src/
└── email-service/         # Consome eventos e envia emails (porta 8081)
    ├── Dockerfile
    └── src/
```

### user-service

- **POST** `/api/v1/auth/register` — cadastra usuário e publica `user.registered`
- **POST** `/api/v1/auth/login` — autentica e publica `user.login`
- **POST** `/api/v1/orders` — cria pedido e publica `order.created` *(requer JWT)*
- **POST** `/api/v1/users/password-reset` — publica `user.password` *(requer JWT)*

### email-service

Consome as filas do RabbitMQ e envia o email correspondente via Mailtrap.
