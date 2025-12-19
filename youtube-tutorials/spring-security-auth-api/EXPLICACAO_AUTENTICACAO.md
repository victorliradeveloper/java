# 🔐 Explicação do Sistema de Autenticação

Este documento explica como funciona o sistema de autenticação e autorização neste projeto Spring Boot.

## 📋 Visão Geral

O projeto implementa autenticação baseada em **JWT (JSON Web Token)** usando Spring Security. O fluxo funciona assim:

1. **Usuário se registra** → cria conta no sistema
2. **Usuário faz login** → recebe um token JWT
3. **Usuário usa o token** → em requisições para acessar recursos protegidos
4. **Sistema valida o token** → verifica se o usuário tem permissão

---

## 🏗️ Arquitetura e Componentes

### 1. **SecurityConfigurations.java** - Configuração de Segurança
📍 Localização: `infra/security/SecurityConfigurations.java`

**O que faz:**
- Configura quais rotas são públicas e quais precisam de autenticação
- Define que o sistema usa **STATELESS** (sem sessão, apenas tokens)
- Registra o filtro de segurança personalizado

**Configurações importantes:**
```java
// Rotas públicas (qualquer um pode acessar)
.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
.requestMatchers(HttpMethod.POST, "/auth/register").permitAll()

// Rotas protegidas (precisa de autenticação)
.requestMatchers(HttpMethod.POST, "/product").hasRole("ADMIN")  // Só ADMIN
.anyRequest().authenticated()  // Outras rotas precisam estar autenticado
```

**Por que STATELESS?**
- Não guarda sessão no servidor
- Cada requisição traz o token JWT
- Mais escalável (funciona bem com múltiplos servidores)

---

### 2. **TokenService.java** - Geração e Validação de Tokens
📍 Localização: `infra/security/TokenService.java`

**O que faz:**
- **Gera tokens JWT** quando o usuário faz login
- **Valida tokens JWT** quando o usuário faz requisições

**Como funciona:**

#### Gerar Token (no login):
```java
public String generateToken(User user) {
    // Cria um token JWT com:
    // - Login do usuário (subject)
    // - Expira em 2 horas
    // - Assinado com uma chave secreta
}
```

#### Validar Token (em cada requisição):
```java
public String validateToken(String token) {
    // Verifica se o token:
    // - É válido (não foi alterado)
    // - Não expirou
    // - Foi emitido por este sistema
    // Retorna o login do usuário se válido
}
```

**O que é JWT?**
- É como um "passaporte digital"
- Contém informações do usuário (login)
- É assinado digitalmente (não pode ser falsificado)
- Expira após um tempo (2 horas neste projeto)

---

### 3. **SecurityFilter.java** - Filtro de Segurança
📍 Localização: `infra/security/SecurityFilter.java`

**O que faz:**
- Intercepta **TODAS** as requisições HTTP
- Extrai o token JWT do cabeçalho `Authorization`
- Valida o token e autentica o usuário
- Permite que a requisição continue

**Fluxo:**
```
Requisição chega → SecurityFilter intercepta
    ↓
Extrai token do header "Authorization: Bearer <token>"
    ↓
Valida o token usando TokenService
    ↓
Busca o usuário no banco de dados
    ↓
Autentica o usuário no Spring Security
    ↓
Requisição continua para o controller
```

**Código chave:**
```java
var token = this.recoverToken(request);  // Pega o token
if(token != null){
    var login = tokenService.validateToken(token);  // Valida
    UserDetails user = userRepository.findByLogin(login);  // Busca usuário
    
    // Autentica no Spring Security
    var authentication = new UsernamePasswordAuthenticationToken(...);
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
```

---

### 4. **AuthenticationController.java** - Endpoints de Autenticação
📍 Localização: `controllers/AuthenticationController.java`

#### **POST /auth/register** - Registrar Usuário
```java
@PostMapping("/register")
public ResponseEntity register(@RequestBody RegisterDTO data) {
    // 1. Verifica se o login já existe
    // 2. Criptografa a senha com BCrypt
    // 3. Salva o usuário no banco
    // 4. Retorna sucesso
}
```

**Exemplo de requisição:**
```json
POST /auth/register
{
  "login": "joao",
  "password": "senha123",
  "role": "USER"  // ou "ADMIN"
}
```

#### **POST /auth/login** - Fazer Login
```java
@PostMapping("/login")
public ResponseEntity login(@RequestBody AuthenticationDTO data) {
    // 1. Autentica usuário e senha
    // 2. Gera um token JWT
    // 3. Retorna o token
}
```

**Exemplo de requisição:**
```json
POST /auth/login
{
  "login": "joao",
  "password": "senha123"
}
```

**Resposta:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

### 5. **User.java** - Modelo de Usuário
📍 Localização: `domain/user/User.java`

**O que faz:**
- Representa um usuário no sistema
- Implementa `UserDetails` (interface do Spring Security)
- Define as **permissões (roles)** do usuário

**Roles:**
- `USER` → Usuário comum (pode ver produtos)
- `ADMIN` → Administrador (pode criar produtos)

**Método importante:**
```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    // ADMIN tem 2 roles: ROLE_ADMIN e ROLE_USER
    // USER tem 1 role: ROLE_USER
}
```

---

### 6. **AuthorizationService.java** - Carregar Usuário
📍 Localização: `services/AuthorizationService.java`

**O que faz:**
- Implementa `UserDetailsService`
- Busca usuário no banco pelo login
- Usado pelo Spring Security para autenticação

---

## 🔄 Fluxo Completo de Autenticação

### Cenário 1: Usuário faz login

```
1. Cliente → POST /auth/login {login, password}
   ↓
2. AuthenticationController recebe
   ↓
3. AuthenticationManager valida credenciais
   ↓
4. TokenService gera token JWT
   ↓
5. Retorna token para o cliente
   ↓
6. Cliente guarda o token (localStorage, cookie, etc)
```

### Cenário 2: Usuário acessa recurso protegido

```
1. Cliente → GET /product
   Header: Authorization: Bearer <token>
   ↓
2. SecurityFilter intercepta
   ↓
3. Extrai token do header
   ↓
4. TokenService valida token
   ↓
5. Busca usuário no banco
   ↓
6. Autentica no Spring Security
   ↓
7. SecurityConfigurations verifica permissões
   ↓
8. Se autorizado → ProductController processa
   Se não autorizado → 403 Forbidden
```

---

## 🔑 Conceitos Importantes

### **Autenticação vs Autorização**

- **Autenticação (Authentication)**: "Quem é você?"
  - Verifica se o usuário é quem diz ser
  - Exemplo: Login com usuário e senha

- **Autorização (Authorization)**: "O que você pode fazer?"
  - Verifica se o usuário tem permissão
  - Exemplo: Só ADMIN pode criar produtos

### **BCrypt - Criptografia de Senhas**

```java
String encryptedPassword = new BCryptPasswordEncoder().encode("senha123");
// Resultado: "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
```

**Por que usar?**
- Senhas nunca são armazenadas em texto puro
- Mesma senha gera hash diferente a cada vez
- Muito difícil de reverter (one-way hash)

### **JWT - Estrutura**

Um token JWT tem 3 partes separadas por ponto:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.  ← Header (algoritmo)
eyJzdWIiOiJqb2FvIn0.                    ← Payload (dados do usuário)
SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV     ← Signature (assinatura)
```

---

## 📝 Exemplos Práticos

### 1. Registrar um usuário ADMIN

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "login": "admin",
    "password": "admin123",
    "role": "ADMIN"
  }'
```

### 2. Fazer login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "login": "admin",
    "password": "admin123"
  }'
```

**Resposta:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 3. Criar produto (precisa ser ADMIN)

```bash
curl -X POST http://localhost:8080/product \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d '{
    "name": "Notebook",
    "price": 3000.00
  }'
```

### 4. Listar produtos (qualquer usuário autenticado)

```bash
curl -X GET http://localhost:8080/product \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## 🎯 Resumo Rápido

1. **Registro**: Usuário cria conta → senha é criptografada → salvo no banco
2. **Login**: Usuário envia credenciais → sistema valida → retorna token JWT
3. **Requisições**: Cliente envia token no header → SecurityFilter valida → permite acesso
4. **Autorização**: Sistema verifica role do usuário → permite ou nega acesso

---

## ❓ Dúvidas Comuns

**P: O token expira?**
R: Sim, em 2 horas. Depois disso, o usuário precisa fazer login novamente.

**P: Onde guardar o token no frontend?**
R: localStorage, sessionStorage, ou cookies (depende da sua necessidade de segurança).

**P: Como invalidar um token?**
R: Neste projeto, tokens não são invalidados antes de expirar. Para isso, seria necessário uma blacklist de tokens.

**P: Por que STATELESS?**
R: Permite escalar horizontalmente (múltiplos servidores) sem precisar compartilhar sessões.

---

## 📚 Próximos Passos para Aprender

1. Entender como o Spring Security funciona internamente
2. Aprender sobre refresh tokens (renovar tokens sem fazer login)
3. Implementar logout (blacklist de tokens)
4. Adicionar rate limiting (limitar tentativas de login)
5. Implementar recuperação de senha

---

**Dúvidas?** Revise o código seguindo este guia e experimente fazer requisições usando Postman ou curl!

