# RabbitMQ — Nomes de Filas e Sincronismo entre Serviços

## O que precisa ser idêntico

O RabbitMQ identifica filas pelo **nome (string)**. O nome da constante Java não importa — o que importa é o valor que ela carrega.

Os dois serviços precisam referenciar exatamente a mesma string:

```
user-service   REGISTERED_QUEUE = "email.registered.queue"
email-service  REGISTERED_QUEUE = "email.registered.queue"  ✅
```

Se um caractere sequer for diferente, o consumer escuta uma fila que nunca recebe mensagem. Nenhum erro aparece — os emails simplesmente não chegam.

## Por que cada serviço declara as filas

O `user-service` declara as filas e cria os bindings com o exchange. O `email-service` redeclara as mesmas filas como medida de resiliência: garante que elas existam antes do consumer tentar se conectar, independente da ordem de inicialização dos serviços.

Se o `email-service` subir antes do `user-service`, as filas já estarão criadas e nenhuma mensagem será perdida.

## O papel do Exchange

O produtor nunca envia uma mensagem diretamente para uma fila. Ele envia para o **exchange**, que é o roteador — decide para qual fila a mensagem vai com base na routing key.

Existem três tipos de exchange no RabbitMQ:

| Tipo | Comportamento |
|---|---|
| `DirectExchange` | Roteia para a fila cujo binding tem a routing key **exata** |
| `FanoutExchange` | Ignora a routing key e envia para **todas** as filas vinculadas |
| `TopicExchange` | Roteia por **padrão** na routing key, suportando curingas `*` e `#` |

Este projeto usa `TopicExchange` (`user.exchange`). Cada evento tem uma routing key específica e cada fila tem um binding com aquela chave — então cada mensagem chega apenas na fila certa.

O `TopicExchange` foi escolhido porque permite escalar: amanhã um novo serviço pode escutar `user.*` e receber todos os eventos de usuário sem alterar nada no `user-service`.

## Fluxo de uma mensagem

```
EventPublisher (user-service)
      │
      └──► user.exchange
                │
           routing key: "user.registered"
                │
                ▼
      email.registered.queue  ◄── declarada em ambos os serviços
                │
                ▼
      EmailConsumer (email-service)
      @RabbitListener(queues = "email.registered.queue")
```

## Mapeamento completo das filas

| Fila | Routing Key | Publicado por | Consumido por |
|---|---|---|---|
| `email.registered.queue` | `user.registered` | `EventPublisher` | `EmailConsumer.onUserRegistered` |
| `email.login.queue` | `user.login` | `EventPublisher` | `EmailConsumer.onUserLogin` |
| `email.order.queue` | `order.created` | `EventPublisher` | `EmailConsumer.onOrderCreated` |
| `email.password.queue` | `user.password` | `EventPublisher` | `EmailConsumer.onPasswordReset` |

Todas as mensagens passam pelo exchange `user.exchange` (Topic Exchange).
