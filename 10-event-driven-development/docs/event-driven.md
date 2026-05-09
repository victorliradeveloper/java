# Event-Driven: Notificação vs Transferência de Dados

## O objetivo de um evento

O objetivo principal de um evento é **notificar** que algo aconteceu no sistema. O evento é um fato do domínio:

- `USER_REGISTERED` — um usuário se cadastrou
- `USER_LOGIN` — um usuário fez login
- `ORDER_CREATED` — um pedido foi criado
- `USER_PASSWORD_RESET` — um reset de senha foi solicitado

O produtor não sabe quem vai consumir o evento nem o que vai fazer com ele. Essa é a essência do desacoplamento: o `user-service` publica e esquece.

## Por que o payload existe

A notificação sozinha raramente é suficiente. Se o `email-service` recebe `USER_REGISTERED` mas não sabe o nome e o email do usuário, ele precisaria fazer uma chamada HTTP de volta ao `user-service` para buscar esses dados.

Isso criaria:
- **Acoplamento** entre os serviços (email-service dependeria da API do user-service)
- **Latência** extra em cada processamento
- **Ponto de falha** — se o user-service estiver fora, o email não é enviado

O payload resolve isso carregando o mínimo necessário para o consumidor agir de forma autônoma.

## A regra prática

> Leve no payload **apenas o que o consumidor precisa para agir**, nada além disso.

## Como está implementado aqui

Cada evento carrega um payload enxuto e focado:

**Eventos de usuário** (`UserEventDTO`):
```json
{
  "eventType": "USER_REGISTERED",
  "timestamp": "2025-01-01T10:00:00Z",
  "payload": {
    "userId": "abc-123",
    "name": "Victor Lira",
    "email": "victor@teste.com"
  }
}
```

**Evento de pedido** (`OrderEventDTO`):
```json
{
  "eventType": "ORDER_CREATED",
  "timestamp": "2025-01-01T10:00:00Z",
  "payload": {
    "orderId": "xyz-456",
    "userId": "abc-123",
    "name": "Victor Lira",
    "email": "victor@teste.com",
    "description": "Notebook Dell XPS",
    "amount": 8500.00
  }
}
```

O `email-service` recebe esses dados e envia o email sem precisar consultar nenhum outro serviço.

## Fluxo completo

```
user-service                RabbitMQ                 email-service
────────────                ────────                 ─────────────
Ação acontece
      │
EventPublisher.publish()
      │
      └──► user.exchange ──► email.registered.queue ──► EmailConsumer
           (routing key:                                       │
            user.registered)                            EmailService
                                                               │
                                                        Template (subject + body)
                                                               │
                                                        JavaMailSender
                                                               │
                                                           Mailtrap
```

## Resumo

| Aspecto | Detalhe |
|---|---|
| Objetivo do evento | Notificar que um fato ocorreu |
| Objetivo do payload | Dar autonomia ao consumidor para agir sem buscar dados em outro lugar |
| O que NÃO fazer | Colocar dados desnecessários no payload (não é uma API REST) |
| Benefício | Serviços totalmente desacoplados — produtor e consumidor evoluem independentemente |
