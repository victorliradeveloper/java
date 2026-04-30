# Cache com Redis

## Visão geral

O projeto usa **Spring Cache** como abstração e **Redis** como backend. A ideia é evitar consultas desnecessárias ao banco de dados para buscas por ID de todo, que são operações frequentes e cujo resultado raramente muda entre duas requisições consecutivas.

## Arquitetura

```
Cliente → Controller → Service
                          ↓
                    Cache hit? ──→ Redis → resposta imediata
                          ↓
                    Cache miss → Banco de dados → salva no Redis → resposta
```

## Configuração

**`CacheConfig.java`** define o `RedisCacheManager` com:

- TTL de **10 minutos** por entrada
- Serialização em **JSON** via `GenericJackson2JsonRedisSerializer` (legível e sem acoplamento a `Serializable`)

**`application.properties`** expõe as propriedades de conexão com fallback para desenvolvimento local:

```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
```

## Comportamento por operação

| Método | Anotação | Efeito no cache |
|---|---|---|
| `getById` | `@Cacheable` | Retorna do Redis se existir; caso contrário consulta o banco e armazena |
| `update` | `@CacheEvict` | Remove a entrada do cache ao atualizar |
| `complete` | `@CacheEvict` | Remove a entrada do cache ao marcar como concluído |
| `delete` | `@CacheEvict` | Remove a entrada do cache ao deletar |

## Chave do cache

```
todos::{userId}:{todoId}

Exemplo: todos::42:7
```

O `userId` faz parte da chave por segurança: garante que o cache de um usuário nunca seja acessado por outro, mesmo que dois usuários tentem buscar um todo com o mesmo ID.

## Infraestrutura

O Redis sobe junto com a aplicação via Docker Compose:

```bash
docker compose up --build
```

O serviço `app` só inicia após o Redis passar no healthcheck (`redis-cli ping`).

## Adicionando cache em outros endpoints

Para cachear outro método, o padrão é o mesmo:

```java
// Leitura — armazena no cache
@Cacheable(cacheNames = "nome-do-cache", key = "#user.id + ':' + #parametro")
public AlgumDTO buscar(User user, Long parametro) { ... }

// Escrita — invalida a entrada correspondente
@CacheEvict(cacheNames = "nome-do-cache", key = "#user.id + ':' + #parametro")
public AlgumDTO atualizar(User user, Long parametro, ...) { ... }
```
