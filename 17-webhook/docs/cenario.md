# O que esse projeto simula

Simulação em miniatura do fluxo **checkout + gateway de pagamento** de um
e-commerce. O foco é mostrar os padrões corretos pra integrar com webhooks de
forma resiliente e segura.

## O cenário

1. Cliente clica em "Finalizar compra" → site cria um **pedido**.
2. Site manda os dados pra uma **operadora de pagamento** (Stripe, Cielo…).
3. A operadora demora — análise antifraude, banco emissor, etc.
4. Site responde "Pedido recebido" sem esperar.
5. Quando a operadora decide, ela faz uma chamada HTTP **de volta** avisando:
   "Pagamento aprovado". → **isso é o webhook**.
6. Site atualiza o pedido pra "Pago".

## Mapeamento

| Mundo real                          | Projeto                                    |
|-------------------------------------|--------------------------------------------|
| Site da loja                        | `order-service`                            |
| Operadora (Stripe etc.)             | `payment-service` (simulando ser externa)  |
| "Analisando o pagamento…"           | `Thread.sleep(5s)` em `PaymentProcessor`   |
| Operadora avisando "aprovado!"      | `POST /webhooks/payment`                   |

## Por que webhook e não resposta síncrona

A operadora demora. Se o `order-service` ficasse esperando, a request do
cliente travava por minutos e qualquer falha derrubava o pedido. O padrão é
**"dispara e segue a vida"** — a operadora responde depois.

## Os 4 problemas que o projeto resolve

| # | Problema | Solução no código |
|---|----------|-------------------|
| 1 | **Autenticidade** — URL pública, qualquer um pode mandar `POST` | HMAC: `WebhookSigningInterceptor` (produtor) + `WebhookSignatureFilter` (consumidor) |
| 2 | **Entrega não confiável** — rede e serviços falham | `@Retryable(3x, backoff exp)` em `OrderServiceClient.notifyPayment` |
| 3 | **Duplicação** — retry pode entregar 2x | Tabela `processed_webhook_events` + checagem em `OrderService.processPaymentWebhook` |
| 4 | **Falha definitiva** — depois de N tentativas, desiste | `@Recover` em `OrderServiceClient` (em produção: DLQ) |

## Ver também

- [architecture.md](architecture.md) — diagrama do fluxo completo
- [webhook.md](webhook.md) — explicação do conceito de webhook
- [webhook-flow.md](webhook-flow.md) — fluxo função-por-função
