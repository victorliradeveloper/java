# Por que usar Keycloak?

## O que seria necessário sem o Keycloak

Sem o Keycloak, você precisaria implementar manualmente:

- Tabela de usuários no banco de dados
- Hash e verificação de senhas (bcrypt, argon2, etc.)
- Geração e validação de tokens JWT
- Lógica de expiração e refresh de tokens
- Controle de sessões
- Endpoints de login, logout e registro
- Proteção contra brute force
- Suporte a 2FA, caso necessário

## O que o Keycloak entrega pronto

- Gerenciamento completo de usuários (criar, editar, desativar)
- Geração e validação de tokens JWT
- Refresh token automático
- Controle de sessões
- Proteção contra brute force nativa
- Suporte a 2FA
- Login social (Google, GitHub, etc.) sem código adicional
- SSO (Single Sign-On): um único login serve para múltiplas aplicações
- Console de administração via interface web em `http://localhost:8080`

## O que ficou no nosso código

```
POST /auth/register  →  chama a Admin API do Keycloak para criar o usuário
POST /auth/login     →  chama o token endpoint do Keycloak e retorna o JWT
GET  /hello          →  valida o JWT gerado pelo Keycloak automaticamente
```

A aplicação não armazena senha, não gera token e não gerencia sessão.
Toda essa responsabilidade pertence ao Keycloak.

## Resumo

| Responsabilidade         | Sem Keycloak | Com Keycloak  |
|--------------------------|--------------|---------------|
| Armazenar senhas         | Você         | Keycloak      |
| Gerar tokens JWT         | Você         | Keycloak      |
| Validar tokens           | Você         | Keycloak      |
| Refresh token            | Você         | Keycloak      |
| Login social             | Você         | Keycloak      |
| SSO entre apps           | Você         | Keycloak      |
| Proteção contra brute force | Você      | Keycloak      |
| Interface de administração | Você       | Keycloak      |
