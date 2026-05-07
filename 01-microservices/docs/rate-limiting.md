# Rate Limiting com Redis

## O que é Rate Limiting?

Rate limiting é uma técnica que **limita a quantidade de requisições** que um cliente pode fazer em um determinado período. Sem ele, um único cliente poderia sobrecarregar o sistema com milhares de requisições por segundo.

---

## Como está implementado neste projeto

O **API Gateway** intercepta cada requisição antes de enviá-la ao `todo-service` e consulta o **Redis** para decidir se a requisição pode passar ou deve ser bloqueada.

```
Cliente
   │
   ▼
API Gateway ──► pergunta ao Redis: "ainda tem tokens para esse IP?"
                      │
              ┌───────┴────────┐
              │ tem tokens      │ sem tokens
              ▼                 ▼
         deixa passar       bloqueia com
         desconta 1 token   429 Too Many Requests
```

---

## O Algoritmo: Token Bucket (Balde de Tokens)

O Spring Cloud Gateway usa o algoritmo **Token Bucket** para controlar as requisições.

```
[ Balde: máx. 20 tokens ]
       │
       │  +10 tokens por segundo (reabastecimento)
       │
       └── cada requisição consome 1 token
```

### Parâmetros configurados

| Parâmetro          | Valor | O que significa                                      |
|--------------------|-------|------------------------------------------------------|
| `replenishRate`    | 10    | Adiciona 10 tokens por segundo no balde              |
| `burstCapacity`    | 20    | Capacidade máxima do balde (permite picos)           |
| `requestedTokens`  | 1     | Cada requisição consome 1 token                      |

### Na prática

- Um cliente **inativo** acumula até 20 tokens (máximo do balde)
- Ele pode fazer **20 requisições em rajada** sem ser bloqueado
- Em ritmo contínuo, fica limitado a **10 requisições por segundo**
- Se ultrapassar, recebe `429 Too Many Requests` até o balde reabastecer

---

## Por que Redis e não memória do gateway?

### Sem Redis (memória local)

```
instância 1 do gateway  →  contador próprio: 18/20
instância 2 do gateway  →  contador próprio: 18/20

Resultado: cliente faz 20 req em cada instância = 40 req no total
           o limite foi burlado
```

### Com Redis (contador centralizado)

```
instância 1 do gateway ──┐
                          ├──► Redis: contador único = 18/20
instância 2 do gateway ──┘

Resultado: o limite de 20 é respeitado independentemente de
           quantas instâncias do gateway estiverem rodando
```

O Redis é um **banco de dados em memória** extremamente rápido — a consulta ao contador adiciona menos de 1ms na requisição.

---

## Como o Gateway identifica o cliente

O limite é aplicado **por IP**. O gateway lê o IP na seguinte ordem:

1. Header `X-Forwarded-For` (definido por proxies e load balancers)
2. IP remoto da conexão (fallback)

O header `X-Forwarded-For` é necessário porque dentro do Docker todos os clientes apareceriam com o mesmo IP interno da rede, fazendo o limite ser global em vez de por usuário.

---

## Arquivos relevantes

| Arquivo | O que faz |
|---|---|
| `api-gateway/src/main/java/.../config/RateLimiterConfig.java` | Define como o IP do cliente é extraído (`KeyResolver`) |
| `api-gateway/src/main/resources/application.yml` | Configura os parâmetros do rate limiter na rota |
| `docker-compose.yml` | Sobe o container Redis e passa o host para o gateway |
