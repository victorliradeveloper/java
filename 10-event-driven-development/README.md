# Event-Driven Development

Documentacao sobre arquitetura orientada a eventos em aplicacoes Java com Spring Boot.

---

## O que e Event-Driven

Em vez de acoes sincronas e diretas entre componentes, a arquitetura orientada a eventos faz com que cada acao relevante gere um **evento** que e publicado e consumido de forma desacoplada.

```
Sem eventos (acoplado):
  ServiceA.metodo() → chama diretamente → ServiceB.enviarEmail()

Com eventos (desacoplado):
  ServiceA.metodo() → publica evento "PedidoCriado"
                            └→ ServiceB consome → envia email
                            └→ ServiceC consome → atualiza estoque
```

---

## Tipos de Eventos

### 1. Evento de Aplicacao (Spring Events)

Evento interno, dentro da mesma JVM. Sincrono por padrao.

```java
// Definir o evento
public class PedidoCriadoEvent extends ApplicationEvent {
    private final Pedido pedido;

    public PedidoCriadoEvent(Object source, Pedido pedido) {
        super(source);
        this.pedido = pedido;
    }
}

// Publicar
@Service
public class PedidoService {

    private final ApplicationEventPublisher publisher;

    public Pedido criar(PedidoRequest request) {
        Pedido pedido = repository.save(new Pedido(request));
        publisher.publishEvent(new PedidoCriadoEvent(this, pedido));
        return pedido;
    }
}

// Consumir
@Component
public class EmailListener {

    @EventListener
    public void onPedidoCriado(PedidoCriadoEvent event) {
        emailService.enviarConfirmacao(event.getPedido());
    }
}
```

**Quando usar**: comunicacao interna entre componentes da mesma aplicacao, fluxos simples sem necessidade de durabilidade.

---

### 2. Evento de Mensageria (Message Broker)

Evento externo, publicado em um broker (RabbitMQ, Kafka). Assincrono e distribuido.

```java
// Publicar no RabbitMQ
@Service
public class PedidoService {

    private final RabbitTemplate rabbitTemplate;

    public Pedido criar(PedidoRequest request) {
        Pedido pedido = repository.save(new Pedido(request));
        rabbitTemplate.convertAndSend("pedidos.exchange", "pedido.criado", pedido);
        return pedido;
    }
}

// Consumir do RabbitMQ
@Component
public class EmailConsumer {

    @RabbitListener(queues = "pedidos.email.queue")
    public void onPedidoCriado(Pedido pedido) {
        emailService.enviarConfirmacao(pedido);
    }
}
```

**Quando usar**: comunicacao entre microservicos, processamento assincrono, alta escala, resiliencia.

---

### 3. Evento de Banco de Dados

Trigger no banco que executa logica apos insert/update. Menos comum para regras de negocio.

```sql
CREATE OR REPLACE FUNCTION notificar_pedido_criado()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO event_log (type, payload, created_at)
    VALUES ('PEDIDO_CRIADO', row_to_json(NEW), NOW());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER after_pedido_insert
AFTER INSERT ON pedidos
FOR EACH ROW EXECUTE FUNCTION notificar_pedido_criado();
```

**Quando usar**: auditoria de banco, captura de mudancas (CDC), raramente para logica de negocio.

---

## Outbox Pattern

Garante que nenhum evento se perca entre salvar no banco e publicar no broker.

### Problema sem Outbox

```
1. Salva pedido no banco     ✓
2. Publica no RabbitMQ       ✗ (aplicacao cai aqui)
→ Pedido salvo, evento perdido
```

### Solucao com Outbox

```
1. Salva pedido + evento na tabela outbox  (mesma transacao) ✓
2. Job polling publica o evento no broker                     ✓
3. Marca evento como consumido                                ✓
→ Evento nunca se perde
```

### Implementacao

```sql
-- Tabela outbox
CREATE TABLE event_outbox (
    id         UUID PRIMARY KEY,
    type       VARCHAR NOT NULL,
    payload    JSONB   NOT NULL,
    consumed   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

```java
// Salva pedido + evento na mesma transacao
@Transactional
public Pedido criar(PedidoRequest request) {
    Pedido pedido = repository.save(new Pedido(request));

    eventOutboxRepository.save(EventOutbox.builder()
        .id(UUID.randomUUID())
        .type("PEDIDO_CRIADO")
        .payload(toJson(pedido))
        .consumed(false)
        .build());

    return pedido;
}
```

```java
// Job que faz polling e publica no broker
@Scheduled(fixedDelay = 5000)
@Transactional
public void processarOutbox() {
    List<EventOutbox> eventos = outboxRepository.findByConsumedFalse();

    for (EventOutbox evento : eventos) {
        rabbitTemplate.convertAndSend("pedidos.exchange", evento.getType(), evento.getPayload());
        evento.setConsumed(true);
        outboxRepository.save(evento);
    }
}
```

---

## Fluxo Real: wine-message

Exemplo de sistema de emails transacionais orientado a eventos usando Spring Events + Outbox Pattern + Apache Camel.

```
[1] POST /api/v1/messages
        │
        ▼
[2] MessageCreateCommandHandler
    Valida schema e payload
    Persiste Message (status=RECEIVED)
        │
        ▼
[3] Spring Event publicado
    MessageReceivedEvent via ApplicationEventPublisher
        │
        ▼
[4] Listener persiste no banco (Outbox)
    DbEventMessage { consumed: false }
        │
        ▼ (a cada 5 segundos)
[5] Quartz Job faz polling
    Busca DbEventMessage onde consumed = false
        │
        ▼
[6] Apache Camel SEDA (in-memory broker)
    seda:event.exchange.MESSAGE_RECEIVED
        │
        ▼
[7] Consumer Camel chama MessageDistributeCommand
    Seleciona rotas por prioridade
    Renderiza template Handlebars
    Chama plugin (AWS SES / SendGrid / SMTP)
        │
        ▼
[8] Email enviado
    DbEventMessage.consumed = true
    Message.status = DISTRIBUTED
```

---

## Comparativo de Abordagens

| | Spring Events | RabbitMQ | Kafka |
|--|--|--|--|
| **Escopo** | Intra-JVM | Multi-servico | Multi-servico |
| **Assincrono** | Nao (por padrao) | Sim | Sim |
| **Durabilidade** | Nao | Sim (com persistencia) | Sim |
| **Escala** | Baixa | Media | Alta |
| **Complexidade** | Baixa | Media | Alta |
| **Quando usar** | Eventos internos simples | Microservicos, filas de trabalho | Streaming, alto volume |

---

## Configuracao RabbitMQ com Spring AMQP

### Dependencia

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### Configuracao

```java
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "pedidos.exchange";
    public static final String QUEUE    = "pedidos.email.queue";
    public static final String ROUTING  = "pedido.criado";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING);
    }

    @Bean
    public MessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

### application.properties

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

---

## Conceitos-Chave

| Conceito | Descricao |
|----------|-----------|
| **Event** | Representacao de algo que aconteceu no sistema |
| **Publisher** | Quem gera e publica o evento |
| **Consumer / Listener** | Quem reage ao evento |
| **Exchange** | Ponto de entrada no RabbitMQ, roteia para filas |
| **Queue** | Fila onde os eventos ficam ate ser consumidos |
| **Routing Key** | Chave que define para qual fila o evento vai |
| **Outbox Pattern** | Tecnica de persistir eventos no banco antes de enviar ao broker |
| **CDC** | Change Data Capture: captura mudancas no banco e gera eventos automaticamente (ex: Debezium) |
| **SEDA** | Staged Event-Driven Architecture: broker in-memory (Apache Camel) |
