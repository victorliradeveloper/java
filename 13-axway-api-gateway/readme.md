# Plano de Execução — E-commerce em Microsserviços com Axway API Gateway

---

## Objetivo

Construir um sistema de e-commerce baseado em microsserviços para aprender na prática como um **API Gateway enterprise** funciona em ambientes reais, cobrindo segurança, roteamento, eventos assíncronos e observabilidade.

---

## Arquitetura

```
Frontend
   ↓
Axway API Gateway
   ↓
┌─────────────────────────────┐
│ auth-service                │
│ user-service                │
│ product-service             │
│ order-service               │
│ payment-service             │
│ notification-service        │
└─────────────────────────────┘
        ↓           ↓
   RabbitMQ    Oracle Database
```

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot + Spring Security |
| Gateway | Axway API Gateway |
| Mensageria | RabbitMQ |
| Banco de dados | Oracle Database |
| Pagamento | Stripe |
| Infraestrutura | Docker + Docker Compose |

---

## Serviços do Axway API Gateway

| Serviço Axway | Responsabilidade no projeto |
|---|---|
| **Policy Studio** | Criação de políticas e filtros para todos os serviços |
| **API Manager** | Catálogo, publicação e versionamento das APIs |
| **Admin Node Manager** | Administração central do gateway (porta 8090) |
| **OAuth 2.0 Authorization Server** | auth-service — emissão e validação de tokens |
| **JWT Validation Filter** | Todos os serviços — validação do token em cada requisição |
| **API Key Authentication** | product-service, user-service — APIs públicas com chave |
| **Rate Limiting Filter** | product-service, order-service — controle de tráfego |
| **Load Balancing** | Distribuição de carga entre instâncias dos serviços |
| **Circuit Breaker** | order-service, payment-service — resiliência entre serviços |
| **Traffic Monitor** | Observabilidade: logs e rastreamento de requisições |
| **Alert Manager** | Monitoramento de falhas e anomalias em tempo real |

Fluxo de requisições pelo gateway:

```
POST /auth/login     → OAuth 2.0 Authorization Server
GET  /products       → JWT Validation + Rate Limiting + API Key
POST /orders         → JWT Validation + Circuit Breaker
POST /payments       → JWT Validation + OAuth2 + mTLS
```

---

## Microsserviços

### auth-service
- Registro e login de usuários
- Emissão de JWT e refresh token
- OAuth2 (Authorization Code Flow)
- RBAC (roles: ADMIN, CUSTOMER)

### user-service
- CRUD de perfil do usuário
- Endereços de entrega
- Protegido por JWT + API Key

### product-service
- Catálogo de produtos
- Estoque
- Rate limiting via Axway
- API pública com API Key

### order-service
- Criação e gerenciamento de pedidos
- Publica evento `OrderCreated` no RabbitMQ
- Circuit Breaker para chamada ao payment-service

### payment-service
- Integração com **Stripe** (Payment Intents API)
- Consome evento `OrderCreated`
- Cria e confirma pagamentos via Stripe
- Publica evento `PaymentProcessed` ou `PaymentFailed`
- mTLS + OAuth2 via Axway

### notification-service
- Consome eventos `PaymentProcessed`
- Envio de e-mail/notificação ao usuário

---

## Fluxo de Eventos (Event-Driven)

```
order-service
   ↓ publica
OrderCreated (RabbitMQ)
   ↓ consome
payment-service
   ↓ publica
PaymentProcessed (RabbitMQ)
   ↓ consome
notification-service
```

---

## Licença Axway (pré-requisito)

O Axway API Gateway é uma solução enterprise. Para uso em estudos, solicite uma **trial license** gratuitamente:

1. Crie uma conta em [community.axway.com](https://community.axway.com)
2. Abra um support case solicitando uma **trial license for API Gateway**
3. Ou acesse o [Amplify Community Plan](https://go2.axway.com/platform-community-plan.html) para créditos gratuitos
4. Após receber a licença, coloque o arquivo `.lic` em `axway/license/`

> A trial cobre todos os serviços usados neste projeto: Policy Studio, API Manager, OAuth 2.0, JWT, Rate Limiting, Traffic Monitor e Alert Manager.

---

## Fases de Execução

### Fase 1 — Fundação
- [ ] Configurar Docker Compose (Oracle, RabbitMQ, Axway)
- [ ] Subir Axway API Gateway + Admin Node Manager
- [ ] Implementar `auth-service` (JWT + OAuth2)
- [ ] Configurar OAuth 2.0 no Policy Studio
- [ ] Implementar `product-service` (CRUD básico)
- [ ] Configurar JWT Validation Filter + API Key no Axway

### Fase 2 — Núcleo do negócio
- [ ] Implementar `user-service`
- [ ] Implementar `order-service`
- [ ] Configurar RabbitMQ e publicar evento `OrderCreated`
- [ ] Implementar `payment-service` consumindo o evento
- [ ] Configurar Circuit Breaker no Axway para payment-service

### Fase 3 — Eventos e notificações
- [ ] Implementar `notification-service`
- [ ] Configurar fluxo completo de eventos no RabbitMQ
- [ ] Configurar Rate Limiting no Axway
- [ ] Configurar Load Balancing no Axway

### Fase 4 — Observabilidade
- [ ] Ativar Traffic Monitor no Axway
- [ ] Configurar Alert Manager
- [ ] Logs centralizados de todos os serviços
- [ ] Métricas e tracing distribuído

### Fase 5 — Produção
- [ ] Configurar mTLS no payment-service
- [ ] RBAC completo via Axway + Spring Security
- [ ] CI/CD com GitHub Actions
- [ ] Deploy com Docker Compose em ambiente cloud

---

## Estrutura de Pastas

```
13-axway-api-gateway/
├── docker-compose.yml
├── axway/
│   └── policies/          # políticas exportadas do Policy Studio
├── auth-service/
├── user-service/
├── product-service/
├── order-service/
├── payment-service/
└── notification-service/
```

---

## APIs principais

| Método | Endpoint | Serviço | Segurança |
|---|---|---|---|
| POST | /auth/register | auth-service | público |
| POST | /auth/login | auth-service | público |
| GET | /products | product-service | API Key |
| GET | /users/{id} | user-service | JWT |
| POST | /orders | order-service | JWT |
| POST | /payments | payment-service | JWT + mTLS |
