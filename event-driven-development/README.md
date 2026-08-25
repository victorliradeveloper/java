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

Ambos os serviços seguem **Arquitetura Hexagonal** (Ports & Adapters).

---

## Tecnologias

| Tecnologia | Uso |
|---|---|
| Java 21 + Spring Boot 3 | Base dos microserviços |
| Spring Security + JWT | Autenticação stateless |
| RabbitMQ (Topic Exchange) | Mensageria entre serviços |
| PostgreSQL + Flyway | Persistência e migrations |
| JavaMailSender + Mailtrap | Envio de emails em sandbox |
| Docker Compose | Orquestração local |
| Springdoc OpenAPI | Documentação Swagger |

---

## Pré-requisitos

- Docker e Docker Compose instalados
- Java 21 (apenas se quiser rodar sem Docker)
- Maven (apenas se quiser rodar sem Docker)

---

## Como rodar

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

A resposta retorna um `token`. Guarde-o para as próximas chamadas.

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

| Fila | Routing Key | Evento |
|---|---|---|
| `email.registered.queue` | `user.registered` | Cadastro de usuário |
| `email.login.queue` | `user.login` | Login de usuário |
| `email.order.queue` | `order.created` | Criação de pedido |
| `email.password.queue` | `user.password` | Reset de senha |

Todas as mensagens passam pelo exchange `user.exchange` (Topic Exchange).

---

## Estrutura do Projeto

```
10-event-driven-development/
├── docker-compose.yml
├── user-service/          # Publica eventos no RabbitMQ (porta 8080)
└── email-service/         # Consome eventos e envia emails (porta 8081)
```

### user-service — Arquitetura Hexagonal

```
domain/
├── EventType.java                  # Enum com os tipos de evento do domínio
├── model/                          # User, Order
├── exception/                      # Exceções de domínio
└── port/
    ├── in/                         # Interfaces de entrada (casos de uso)
    │   ├── AuthUseCase.java
    │   └── OrderUseCase.java
    └── out/                        # Interfaces de saída (repositórios)
        ├── UserRepositoryPort.java
        └── OrderRepositoryPort.java

application/
├── auth/  AuthService              # Implementa AuthUseCase
└── order/ OrderService             # Implementa OrderUseCase

interfaces/
├── rest/                           # Controllers injetam as interfaces port/in
│   ├── AuthController.java
│   ├── OrderController.java
│   └── UserController.java
├── dto/                            # Request e Response DTOs
├── mapper/                         # Conversão entre DTOs e entidades
└── exception/                      # GlobalExceptionHandler

infrastructure/
├── messaging/                      # RabbitMQ: config, publisher, DTOs de evento
├── persistence/                    # Adapters + repositórios JPA
├── security/                       # JWT, filtro, configuração Spring Security
└── config/                         # OpenAPI
```

**Endpoints:**

| Método | Rota | Auth | Evento publicado |
|---|---|---|---|
| POST | `/api/v1/auth/register` | — | `user.registered` |
| POST | `/api/v1/auth/login` | — | `user.login` |
| POST | `/api/v1/orders` | JWT | `order.created` |
| POST | `/api/v1/users/password-reset` | JWT | `user.password` |

---

### email-service — Arquitetura Hexagonal

```
application/
└── email/ EmailService             # Orquestra envio de emails

infrastructure/
├── messaging/                      # RabbitMQ: consumer, config e DTOs de evento
└── template/                       # Um template por tipo de email
    ├── EmailTemplate.java          # Interface genérica EmailTemplate<T>
    ├── UserRegisteredEmailTemplate.java
    ├── UserLoginEmailTemplate.java
    ├── OrderCreatedEmailTemplate.java
    └── PasswordResetEmailTemplate.java
```

O `EmailConsumer` escuta cada fila e delega ao `EmailService`, que usa o template correspondente para montar subject e body antes de enviar via JavaMailSender.
