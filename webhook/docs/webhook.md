# Webhook

## O que é um webhook

Um **webhook** é um mecanismo de integração HTTP no qual um sistema (o *produtor*)
notifica outro sistema (o *consumidor*) de forma assíncrona quando algum evento
acontece, fazendo uma chamada HTTP (geralmente `POST`) para uma URL previamente
combinada pelo consumidor.

A diferença para uma chamada REST tradicional está na direção e no gatilho:

| Modelo                | Quem inicia a chamada | Quando acontece                         |
|-----------------------|-----------------------|-----------------------------------------|
| Polling / REST normal | Consumidor            | Sempre que o consumidor decide perguntar |
| Webhook               | Produtor              | Quando o evento de fato acontece         |

Webhooks são úteis quando o resultado de uma operação não fica pronto na hora
(processamento assíncrono, integrações externas, eventos de domínio) e o
consumidor precisa saber sem ficar fazendo polling.

### Características importantes

- **Assíncrono**: o produtor dispara e segue a vida; o consumidor processa quando
  recebe.
- **Entrega não confiável por natureza**: a rede pode falhar, o consumidor pode
  estar fora do ar. O produtor precisa de **retry com backoff** e idealmente uma
  estratégia de *dead-letter*.
- **Idempotência**: como retries podem entregar o mesmo evento mais de uma vez,
  o consumidor precisa identificar duplicatas (normalmente por um `eventId`).
- **Autenticidade**: a URL do webhook é pública. Sem autenticação, qualquer um
  pode chamar. O padrão de mercado é assinar o corpo com **HMAC** usando um
  segredo compartilhado.

---

## Como funciona neste projeto

O projeto simula um fluxo `order → payment → webhook de confirmação`. O
`payment-service` processa o pagamento de forma assíncrona (sleep de 30s
simulando uma operadora externa) e, quando termina, **chama de volta** o
`order-service` via webhook para atualizar o status da `Order`.

```
1. POST /orders                              (cliente → order-service)
2. POST /payments         (síncrono)         (order-service → payment-service)
3. processAsync (30s sleep, @Async)          (payment-service, em background)
4. POST /webhooks/payment (assinado + retry) (payment-service → order-service)
5. order atualizada para APPROVED            (order-service)
```

Ver o diagrama completo em [architecture.md](architecture.md).

### Lado produtor — `payment-service`

| Arquivo | Papel |
|---|---|
| `service/PaymentProcessor.java` | Executa em `@Async`, dorme 30s, aprova o pagamento e dispara o webhook |
| `client/OrderServiceClient.java` | `RestClient` com `@Retryable(3x, backoff exponencial)` e `@Recover` que loga a falha final |
| `client/WebhookSigningInterceptor.java` | Interceptor que assina o corpo com HMAC-SHA256 e adiciona o header `X-Signature: sha256=<hex>` |
| `client/dto/PaymentWebhookPayload.java` | Payload (`eventId`, `orderId`, `status`) |

Pontos de produção aplicados:

- **`eventId` único** por entrega (gerado no `PaymentProcessor`) — permite que o
  consumidor desduplique.
- **Retry com backoff exponencial** (`500ms`, `1s`, `2s`) para sobreviver a
  falhas transitórias do consumidor.
- **`@Recover`** registra o evento perdido após esgotar tentativas — em
  produção esse seria o gancho para gravar em DLQ ou tabela de outbox para
  reenvio posterior.
- **Assinatura HMAC** com segredo compartilhado, calculada sobre o corpo bruto.

### Lado consumidor — `order-service`

| Arquivo | Papel |
|---|---|
| `controller/PaymentWebhookController.java` | Expõe `POST /webhooks/payment` e delega para o `OrderService` |
| `security/WebhookSignatureFilter.java` | Filtro `OncePerRequestFilter` aplicado a `/webhooks/**` que valida o HMAC antes do controller |
| `security/CachedBodyHttpServletRequest.java` | Wrapper que cacheia o corpo da request — o filtro precisa ler o corpo pra assinar, e o controller precisa lê-lo de novo para desserializar |
| `service/OrderService#processPaymentWebhook` | Faz a checagem de idempotência via `ProcessedWebhookEvent` e atualiza a `Order` em uma única transação |
| `domain/ProcessedWebhookEvent.java` | Tabela `processed_webhook_events(event_id PK, received_at)` — registro de eventos já processados |

### Verificação de assinatura — passo a passo

O filtro `WebhookSignatureFilter` roda **antes** do controller para todo path
que começa com `/webhooks/`:

1. Lê o corpo da request e o armazena (`CachedBodyHttpServletRequest`) para que
   o controller consiga lê-lo depois.
2. Lê o header `X-Signature`. Se ausente ou sem o prefixo `sha256=`, devolve
   `401`.
3. Calcula `HMAC-SHA256(corpo, segredo)` em hex.
4. Compara com a assinatura recebida usando `MessageDigest.isEqual` — comparação
   *constant-time* para evitar **timing attack**.
5. Se bater, passa adiante. Caso contrário, `401`.

O segredo vem da propriedade `webhook.secret` (configurada via env var
`WEBHOOK_SECRET`) e precisa ser o **mesmo** dos dois lados.

### Idempotência

Como o produtor faz retry, o consumidor pode receber o mesmo webhook mais de
uma vez. O `OrderService` protege contra isso:

```java
if (processedEventRepository.existsById(eventId)) {
    log.info("Event {} already processed, skipping", eventId);
    return;
}
// ... atualiza Order ...
processedEventRepository.save(new ProcessedWebhookEvent(eventId, Instant.now()));
```

O `eventId` é a chave primária da tabela `processed_webhook_events`, e o
`save` da `Order` + o registro do evento processado acontecem na **mesma
transação** (`@Transactional`). Resultado: ou o evento foi processado e
marcado, ou nada aconteceu — sem chance de processar duas vezes ou esquecer de
marcar.

---

## Fluxo end-to-end com webhook

```
order-service                                          payment-service
─────────────                                          ───────────────
POST /orders ──────► OrderService.create()
                       └─► POST /payments ───────────► PaymentController
                                                          └─► PaymentService.create()
                                                                └─► PaymentProcessor.processAsync()  [thread @Async]
                                                                       │
                                                                       │   (sleep 30s)
                                                                       │
                                                                       ▼
                                                                  approve payment
                                                                       │
                                                                       │  WebhookSigningInterceptor
                                                                       │  adiciona X-Signature
                                                                       ▼
WebhookSignatureFilter ◄───────── POST /webhooks/payment ──────────────┘
  └─► valida HMAC                  (retry 3x se falhar)
       │
       ▼
PaymentWebhookController
  └─► OrderService.processPaymentWebhook(eventId, orderId, status)
        ├─► existsById(eventId)? sim → ignora (idempotência)
        └─► não → atualiza Order + salva ProcessedWebhookEvent  [mesma TX]
```

---

## O que falta para produção

Este projeto cobre o "esqueleto" do padrão webhook. Em um cenário real ainda
seriam interessantes:

- **Outbox pattern** no produtor — o webhook só é enfileirado para envio depois
  que a transação que aprovou o pagamento for commitada, evitando o cenário
  "notifiquei, mas a transação deu rollback".
- **Dead-letter / fila de reenvio** — hoje o `@Recover` só loga. Em produção
  você grava o evento em uma tabela/fila para retry manual ou agendado.
- **Replay protection com timestamp** — adicionar um header `X-Timestamp`
  assinado junto e rejeitar entregas muito antigas, dificultando ataques de
  replay caso uma assinatura vaze.
- **Rotação de segredo** — suportar mais de um secret ativo ao mesmo tempo para
  permitir rotacionar sem janela de downtime.
