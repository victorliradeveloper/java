# Plano de Ação — Sistema de Emails Transacionais com RabbitMQ

Sistema de emails orientado a eventos com dois microserviços Spring Boot se comunicando via RabbitMQ.
Baseado nas boas práticas do projeto `09-api-good-practices`.

---

## Visão Geral da Arquitetura

```
[user-service]  →  RabbitMQ  →  [email-service]

Ação do usuário        Evento publicado       Email enviado
─────────────────      ────────────────       ─────────────
Cadastro           →   user.registered    →   "Bem-vindo!"
Login              →   user.login         →   "Novo acesso detectado"
Compra             →   order.created      →   "Pedido confirmado"
Reset de senha     →   user.password      →   "Redefinir sua senha"
```

- **user-service** publica eventos no RabbitMQ quando o usuário faz uma ação
- **email-service** consome os eventos e envia o email correspondente
- Os dois serviços não se conhecem — só se comunicam pelo broker

---

## Stack

| Tecnologia | Uso |
|---|---|
| Java 21 | Linguagem |
| Spring Boot 3 | Framework |
| Spring Security | Autenticação e autorização |
| java-jwt 4.4.0 (Auth0) | Geração e validação de JWT (mesma lib do projeto de referência) |
| Spring AMQP | Integração com RabbitMQ |
| RabbitMQ | Message broker |
| Spring Data JPA + PostgreSQL | Persistência |
| Flyway | Migrations de banco |
| Lombok | Redução de boilerplate |
| spring-boot-starter-validation | Validação de DTOs |
| springdoc-openapi | Documentação Swagger |
| Mailtrap | Sandbox de email (ver emails sem SMTP real) |
| Docker | Subir RabbitMQ e PostgreSQL localmente |
| Maven | Build |

---

## Arquitetura de Pacotes (Hexagonal)

Seguindo o mesmo padrão do `09-api-good-practices`:

```
domain/        → Modelos, exceções, ports abstratos (independente de frameworks)
application/   → Use cases (serviços que orquestram a lógica)
infrastructure → Implementações técnicas (BD, security, RabbitMQ, config)
interfaces/    → Controllers REST, DTOs, mappers, exception handlers
```

---

## Estrutura de Pastas

```
10-event-driven-development/
│
├── user-service/
│   └── src/main/java/com/example/userservice/
│       ├── UserServiceApplication.java
│       ├── domain/
│       │   ├── model/
│       │   │   ├── User.java
│       │   │   └── Order.java
│       │   ├── exception/
│       │   │   ├── UserAlreadyExistsException.java
│       │   │   ├── InvalidCredentialsException.java
│       │   │   └── UserNotFoundException.java
│       │   └── port/
│       │       ├── in/
│       │       │   ├── AuthUseCase.java
│       │       │   └── OrderUseCase.java
│       │       └── out/
│       │           └── UserRepositoryPort.java
│       ├── application/
│       │   ├── auth/
│       │   │   └── AuthService.java
│       │   └── order/
│       │       └── OrderService.java
│       ├── infrastructure/
│       │   ├── config/
│       │   │   └── OpenApiConfig.java
│       │   ├── messaging/
│       │   │   ├── RabbitMQConfig.java         # exchange, filas, bindings
│       │   │   └── UserEventPublisher.java     # publica eventos no RabbitMQ
│       │   ├── persistence/
│       │   │   ├── adapter/
│       │   │   │   └── UserRepositoryAdapter.java
│       │   │   └── repository/
│       │   │       └── UserJpaRepository.java
│       │   └── security/
│       │       ├── SecurityConfig.java
│       │       ├── JwtService.java
│       │       └── JwtAuthenticationFilter.java
│       └── interfaces/
│           ├── dto/
│           │   ├── request/
│           │   │   ├── RegisterRequestDTO.java
│           │   │   ├── LoginRequestDTO.java
│           │   │   └── OrderRequestDTO.java
│           │   └── response/
│           │       ├── AuthResponseDTO.java
│           │       ├── OrderResponseDTO.java
│           │       └── ErrorResponseDTO.java
│           ├── exception/
│           │   └── GlobalExceptionHandler.java
│           ├── mapper/
│           │   ├── AuthMapper.java
│           │   └── OrderMapper.java
│           └── rest/
│               ├── AuthController.java
│               └── OrderController.java
│
└── email-service/
    └── src/main/java/com/example/emailservice/
        ├── EmailServiceApplication.java
        ├── domain/
        │   └── model/
        │       └── EmailEvent.java              # representa o evento recebido
        ├── application/
        │   └── email/
        │       └── EmailService.java            # monta e envia o email
        ├── infrastructure/
        │   ├── config/
        │   │   └── RabbitMQConfig.java
        │   ├── messaging/
        │   │   └── EmailConsumer.java           # @RabbitListener por evento
        │   └── template/
        │       └── EmailTemplateFactory.java    # seleciona assunto/corpo por tipo
        └── interfaces/
            └── dto/
                └── EmailEventDTO.java
```

---

## Configuração RabbitMQ

```
Exchange: user.exchange  (Topic Exchange)

Routing Keys e Filas:
  user.registered  →  email.registered.queue
  user.login       →  email.login.queue
  order.created    →  email.order.queue
  user.password    →  email.password.queue
```

**Por que Topic Exchange?**
Permite filtrar mensagens por padrão de routing key. Futuramente um serviço de notificação push pode escutar `user.*` e receber todos os eventos sem mudar o user-service.

---

## Payload dos Eventos

Todos os eventos seguem o mesmo envelope:

```json
{
  "eventType": "USER_REGISTERED",
  "timestamp": "2026-05-01T10:00:00",
  "payload": {
    "userId": "abc-123",
    "name": "Victor",
    "email": "victor@email.com"
  }
}
```

---

## Padrões de Código (baseados no projeto de referência)

### DTOs como Records
```java
// Request com validação
public record RegisterRequestDTO(
    @NotBlank String name,
    @Email @NotBlank String email,
    @Size(min = 6) @NotBlank String password
) {}

// Response limpo
public record AuthResponseDTO(String name, String token) {}
```

### ErrorResponseDTO com factory methods
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(
    int status,
    String error,
    Map<String, String> fieldErrors,
    Instant timestamp,
    String path
) {
    public static ErrorResponseDTO of(...) { ... }
    public static ErrorResponseDTO ofValidation(...) { ... }
}
```

### GlobalExceptionHandler centralizado
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // UserAlreadyExistsException → 409
    // InvalidCredentialsException → 401
    // MethodArgumentNotValidException → 400 com fieldErrors
    // Exception genérica → 500
}
```

### Mappers separados
```java
public class AuthMapper {
    public static User toEntity(RegisterRequestDTO dto) { ... }
    public static AuthResponseDTO toResponse(User user, String token) { ... }
}
```

---

## Etapas de Implementação

### Etapa 1 — Infraestrutura local

- [ ] Subir RabbitMQ com Docker
- [ ] Subir PostgreSQL com Docker
- [ ] Criar conta gratuita no Mailtrap e pegar credenciais SMTP

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management

docker run -d --name postgres \
  -e POSTGRES_DB=userdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:15
```

Painel RabbitMQ: http://localhost:15672 (guest / guest)

---

### Etapa 2 — user-service base

- [ ] Criar projeto Spring Boot com dependências: `web`, `amqp`, `data-jpa`, `validation`, `lombok`, `flyway`, `postgresql`, `springdoc`
- [ ] Configurar `application.properties` com variáveis de ambiente e fallback (padrão do projeto referência)
- [ ] Criar migration Flyway para tabela `users` e `orders`
- [ ] Criar domain models `User` e `Order`
- [ ] Criar ports (interfaces) em `domain/port/in` e `domain/port/out`
- [ ] Criar `UserRepositoryAdapter` implementando o port
- [ ] Configurar `RabbitMQConfig` com exchange, filas e bindings
- [ ] Criar `UserEventPublisher` que monta o payload e publica com a routing key correta

---

### Etapa 3 — Autenticação com Spring Security + JWT

```
POST /api/v1/auth/register  →  salva usuário + publica user.registered + retorna JWT
POST /api/v1/auth/login     →  valida credenciais + publica user.login + retorna JWT
POST /api/v1/orders         →  rota protegida (exige JWT válido)
POST /api/v1/users/password-reset → rota protegida + publica user.password
```

- [ ] Adicionar dependências: `spring-boot-starter-security`, `java-jwt 4.4.0`
- [ ] Criar `JwtService` com `generateToken(User)` e `validateToken(String)` usando Auth0 HMAC-256
- [ ] Criar `JwtAuthenticationFilter` estendendo `OncePerRequestFilter`
- [ ] Criar `SecurityConfig`:
  - Sem CSRF, sessão stateless
  - Rotas públicas: `POST /api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`
  - Rotas protegidas: todo o resto
  - Filtro JWT antes de `UsernamePasswordAuthenticationFilter`
  - `BCryptPasswordEncoder`
- [ ] Criar `AuthService` que registra, autentica e publica eventos
- [ ] Criar `AuthController` com endpoints de registro e login

**Fluxo de autenticação:**

```
POST /api/v1/auth/login
  │
  ▼
AuthService valida email + senha (BCrypt)
  │
  ▼
JwtService.generateToken() → JWT assinado com HMAC-256 (exp: 2h)
  │
  ▼
UserEventPublisher publica "user.login" no RabbitMQ
  │
  ▼
Retorna AuthResponseDTO { name, token }

---

Próxima requisição protegida:
  Authorization: Bearer eyJ...
        │
        ▼
JwtAuthenticationFilter.doFilterInternal()
        │
        ▼
JwtService.validateToken() → extrai claims (userId, name, email)
        │
        ▼
SecurityContextHolder ← UsernamePasswordAuthenticationToken
        │
        ▼
Requisição autorizada
```

---

### Etapa 4 — email-service

- [ ] Criar projeto Spring Boot com dependências: `amqp`, `mail`, `lombok`
- [ ] Configurar `RabbitMQConfig` com as mesmas filas do user-service
- [ ] Criar `EmailConsumer` com `@RabbitListener` para cada fila:
  - `email.registered.queue` → email de boas-vindas
  - `email.login.queue` → notificação de acesso
  - `email.order.queue` → confirmação de pedido
  - `email.password.queue` → link de reset
- [ ] Criar `EmailTemplateFactory` que retorna assunto + corpo por tipo de evento
- [ ] Criar `EmailService` que usa `JavaMailSender` para enviar via Mailtrap
- [ ] Configurar Mailtrap em `application.properties`

---

### Etapa 5 — Teste do Fluxo

- [ ] `POST /api/v1/auth/register` → ver email de boas-vindas no Mailtrap
- [ ] `POST /api/v1/auth/login` → ver email de acesso no Mailtrap
- [ ] Verificar mensagens chegando no painel do RabbitMQ (http://localhost:15672)
- [ ] `POST /api/v1/orders` com JWT → ver email de confirmação no Mailtrap
- [ ] `POST /api/v1/users/password-reset` com JWT → ver email de reset no Mailtrap
- [ ] Tentar acessar rota protegida sem JWT → deve retornar 401 com `ErrorResponseDTO`

---

### Etapa 6 — Extras (opcional)

- [ ] Dead Letter Queue — redirecionar mensagens que falharam para uma fila separada
- [ ] Retry automático — retentar o envio X vezes antes de mover para DLQ
- [ ] Documentação OpenAPI com `@Tag`, `@Operation`, `@ApiResponse` (padrão do projeto referência)
- [ ] Testes com H2 em memória para o user-service

---

## Resultado Esperado

Ao chamar `POST /api/v1/auth/register`, o user-service:
1. Salva o usuário no PostgreSQL
2. Gera um JWT e retorna ao cliente
3. Publica `user.registered` no RabbitMQ

O email-service, sem nenhuma chamada direta, recebe o evento e o email de boas-vindas aparece no Mailtrap.
