# Resource Service

Microsserviço que expõe um endpoint protegido por autenticação OAuth 2.0 com JWT.

## O que faz

Recebe requisições HTTP com um token JWT no header `Authorization`. O Spring Security valida o token consultando o Keycloak, e se for válido, o endpoint retorna uma mensagem junto com o próprio token recebido.

## Endpoint

```
GET http://localhost:8000/hello
```

Requer header:
```
Authorization: Bearer <token JWT>
```

Resposta:
```
Olá, Mundo!
eyJhbGciOiJSUzI1NiIsInR5cC...
```

## Como funciona

1. O cliente envia uma requisição com `Authorization: Bearer <JWT>`
2. O Spring Security intercepta e valida o JWT consultando o Keycloak (`issuer-uri`)
3. Se o token for válido, o controller recebe o objeto `Jwt` com as claims
4. Retorna a mensagem + o valor do token

## Configuração

```properties
server.port=8000
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/master
```

O `issuer-uri` aponta para o Keycloak, que é o responsável por emitir e validar os tokens.

## Pré-requisito

Keycloak rodando na porta `8080` com o realm `master` configurado.

```bash
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:22.0.5 start-dev
```

## Como executar

```bash
./mvnw spring-boot:run
```
