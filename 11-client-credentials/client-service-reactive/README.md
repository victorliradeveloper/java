# Client Service (Reactive)

Microsserviço cliente que consome o `resource-service` usando autenticação OAuth 2.0 com o grant type **Client Credentials**, de forma **reativa** via `WebClient` (Spring WebFlux).

## O que faz

Expõe um endpoint público. Quando chamado, obtém automaticamente um token JWT do Keycloak e o usa para autenticar a requisição ao `resource-service`, tudo de forma não-bloqueante.

## Endpoint

```
GET http://localhost:9000/client/hello
```

Não requer autenticação — o serviço busca o token internamente.

## Como funciona

1. Você chama `GET /client/hello`
2. O `WebClient` (configurado com `ServerOAuth2AuthorizedClientExchangeFilterFunction`) detecta que precisa de um token
3. Faz uma requisição reativa ao Keycloak com `client_id` e `client_secret` para obter um JWT
4. Adiciona o token no header `Authorization: Bearer <JWT>` automaticamente
5. Chama `GET http://localhost:8000/hello` no `resource-service` de forma não-bloqueante
6. Retorna a resposta como `Mono<String>`

## Diferença em relação ao client-service-restclient

Esta implementação usa **`WebClient`** (assíncrono/não-bloqueante) com Spring WebFlux. É a abordagem reativa, ideal para aplicações de alta concorrência que já usam o stack reativo do Spring.

| | client-service-restclient | client-service-reactive |
|---|---|---|
| Stack | Spring Web (Servlet) | Spring WebFlux (Reactive) |
| Cliente HTTP | `RestClient` | `WebClient` |
| Modelo | Síncrono/bloqueante | Assíncrono/não-bloqueante |
| Retorno do controller | `String` | `Mono<String>` |

## Configuração

```yaml
server:
  port: 9000

spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: cliente
            client-secret: cBfBupncSZGm13AB6YIbwMUvrCaxddBf
            authorization-grant-type: client_credentials
        provider:
          keycloak:
            token-uri: http://localhost:8080/realms/master/protocol/openid-connect/token
```

## Pré-requisitos

- Keycloak rodando na porta `8080` com client `cliente` configurado (grant type: client_credentials)
- `resource-service` rodando na porta `8000`

## Como executar

```bash
./mvnw spring-boot:run
```

## Teste

```
GET http://localhost:9000/client/hello
```
