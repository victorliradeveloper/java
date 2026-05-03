# Client Service RestClient — Explicação dos Arquivos

Este projeto implementa o fluxo **OAuth2 Client Credentials** usando Spring Boot, Spring Security OAuth2 Client e o `RestClient` do Spring 6. O serviço atua como **cliente OAuth2**: ele obtém um token de acesso do Keycloak e o anexa automaticamente nas requisições feitas a outros serviços protegidos.

---

## Visão geral do fluxo

```
[client-service :9000]
        |
        |-- GET /client/hello
        |
        v
[RestClient] --> interceptor adiciona Bearer token automaticamente
        |
        v
[resource-service :8000/hello]  (serviço protegido)
        ^
        |
[Keycloak :8080]  <-- emite o token via client_credentials
```

---

## 1. `ClientServiceRestclientApplication.java`

**Papel:** Ponto de entrada da aplicação.

```java
@SpringBootApplication
public class ClientServiceRestclientApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClientServiceRestclientApplication.class, args);
    }
}
```

- A anotação `@SpringBootApplication` habilita auto-configuração, escaneamento de componentes e configuração do Spring Boot.
- `SpringApplication.run(...)` inicializa o contexto da aplicação e sobe o servidor embutido (Tomcat) na porta **9000** (configurada no `application.yml`).
- Não possui nenhuma lógica de negócio; é o bootstrap padrão do Spring Boot.

---

## 2. `SecurityConfig.java`

**Papel:** Configuração de segurança HTTP do próprio serviço cliente.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll());
        return http.build();
    }
}
```

- `@EnableWebSecurity` ativa o Spring Security na aplicação.
- A regra `.anyRequest().permitAll()` libera **todos os endpoints** do próprio cliente sem exigir autenticação de quem o chama.
- Isso é intencional: o `client-service` não protege suas rotas — ele **consome** um serviço protegido. A segurança está no lado do `resource-service`.
- Sem essa configuração explícita, o Spring Security bloquearia as requisições recebidas por padrão.

---

## 3. `RestClientOauthConfig.java`

**Papel:** Configura o `RestClient` com um interceptor que injeta o token OAuth2 automaticamente em cada requisição de saída.

```java
@Configuration
public class RestClientOauthConfig {

    @Bean
    RestClient keycloakRestClientOauth(RestClient.Builder builder,
        OAuth2AuthorizedClientManager authorizedClientManager) {

        OAuth2ClientHttpRequestInterceptor requestInterceptor =
            new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);

        requestInterceptor.setClientRegistrationIdResolver(
            (HttpRequest request) -> "keycloak");

        return builder
            .requestInterceptor(requestInterceptor)
            .build();
    }
}
```

### Componentes envolvidos

| Componente | Responsabilidade |
|---|---|
| `RestClient.Builder` | Builder padrão do Spring injetado automaticamente |
| `OAuth2AuthorizedClientManager` | Gerencia o ciclo de vida dos tokens OAuth2 (obtém, armazena e renova) |
| `OAuth2ClientHttpRequestInterceptor` | Intercepta cada requisição HTTP e adiciona o header `Authorization: Bearer <token>` |
| `setClientRegistrationIdResolver(...)` | Resolve qual registro de cliente OAuth2 usar — aqui sempre retorna `"keycloak"`, que corresponde à chave configurada no `application.yml` |

### O que acontece internamente

1. O interceptor é chamado antes de cada requisição HTTP.
2. Ele pede ao `OAuth2AuthorizedClientManager` um token válido para o client registration `"keycloak"`.
3. O manager verifica se já tem um token em cache e não expirado; caso contrário, faz uma chamada ao Keycloak (`token-uri`) usando `client_credentials`.
4. O token obtido é adicionado como header `Authorization: Bearer <token>` na requisição de saída.

---

## 4. `HelloController.java`

**Papel:** Expõe o endpoint `/client/hello` que delega a chamada ao serviço protegido usando o `RestClient` configurado com OAuth2.

```java
@RestController
@RequestMapping("client")
class HelloController {
    private final RestClient restClient;

    public HelloController(RestClient restClient) {
        this.restClient = restClient;
    }

    @GetMapping("hello")
    public String hello() {
        return restClient.get()
            .uri("http://localhost:8000/hello")
            .retrieve()
            .body(String.class);
    }
}
```

- O `RestClient` injetado é o bean criado em `RestClientOauthConfig` — já com o interceptor OAuth2 configurado.
- Ao chamar `GET /client/hello`, o controller dispara `GET http://localhost:8000/hello` com o token Bearer anexado automaticamente.
- `.retrieve().body(String.class)` desserializa a resposta como `String` e a retorna diretamente ao chamador.
- O controller não precisa saber nada sobre tokens — toda a lógica OAuth2 fica encapsulada no interceptor.

---

## Configuração relevante — `application.yml`

```yaml
server:
  port: 9000

spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            provider: keycloak
            client-id: client-service
            client-secret: 2p2ISSwR4YuaQaQJRg3WGu8F5stxz9dG
            authorization-grant-type: client_credentials
        provider:
          keycloak:
            token-uri: http://localhost:8080/realms/master/protocol/openid-connect/token
```

| Propriedade | Valor | Descrição |
|---|---|---|
| `server.port` | `9000` | Porta do cliente |
| `client-id` | `client-service` | ID do client registrado no Keycloak |
| `client-secret` | `2p2ISSwR...` | Segredo do client (nunca versionar em produção) |
| `authorization-grant-type` | `client_credentials` | Fluxo machine-to-machine sem usuário envolvido |
| `token-uri` | `http://localhost:8080/...` | Endpoint do Keycloak para emissão de tokens |

O nome `keycloak` em `registration.keycloak` é a chave que o `setClientRegistrationIdResolver` resolve em `RestClientOauthConfig`.

---

## Resumo das responsabilidades

| Arquivo | Responsabilidade |
|---|---|
| `ClientServiceRestclientApplication` | Bootstrap da aplicação |
| `SecurityConfig` | Libera os endpoints do cliente sem autenticação |
| `RestClientOauthConfig` | Monta o `RestClient` com injeção automática de token OAuth2 |
| `HelloController` | Consome o serviço protegido via `RestClient` configurado |
