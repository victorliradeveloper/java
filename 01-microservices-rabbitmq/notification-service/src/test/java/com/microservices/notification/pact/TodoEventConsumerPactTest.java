package com.microservices.notification.pact;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.annotations.PactDirectory;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microservices.notification.event.TodoEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato Pact do lado <b>consumer</b> (notification-service).
 *
 * <p>Cada {@link Pact} descreve uma mensagem {@code TodoEvent} que este servico
 * espera receber do {@code todo-service}. O Pact grava esses contratos em
 * {@code ../pacts/} (ver {@link PactDirectory}); o provider (todo-service)
 * depois verifica que o que ele publica bate com o que esta declarado aqui.
 *
 * <p><b>Por que async/message pact?</b> A comunicacao entre os servicos eh por
 * RabbitMQ, entao o contrato eh sobre o <i>corpo JSON</i> da mensagem — nao sobre
 * uma request HTTP. Por isso {@link ProviderType#ASYNCH}.
 *
 * <p><b>Matchers, nao valores fixos:</b> {@code todoId}/{@code title} usam
 * type matchers (qualquer string serve, so o tipo importa) e {@code occurredAt}
 * um regex de ISO-8601. So {@code action} eh pinado no valor exato, porque eh
 * ele que diferencia os tres eventos e o consumer ramifica nele.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "todo-service", providerType = ProviderType.ASYNCH)
@PactDirectory("../pacts")
class TodoEventConsumerPactTest {

    private static final String PROVIDER = "todo-service";
    private static final String CONSUMER = "notification-service";

    // ISO-8601 local date-time, com fracao de segundo opcional (LocalDateTime.now()
    // inclui nanos). Evita acoplar o contrato a uma precisao especifica.
    private static final String ISO_DATETIME_REGEX =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?";

    // Mapper que espelha como o consumer le a mensagem: precisa do JavaTimeModule
    // pra desserializar occurredAt (LocalDateTime) a partir da string ISO.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private PactDslJsonBody todoEventBody(String action) {
        return new PactDslJsonBody()
                .stringType("todoId", "11111111-1111-1111-1111-111111111111")
                .stringType("title", "Comprar leite")
                .stringValue("action", action)
                .stringMatcher("occurredAt", ISO_DATETIME_REGEX, "2026-06-13T10:15:30");
    }

    @Pact(provider = PROVIDER, consumer = CONSUMER)
    public MessagePact todoCreated(MessagePactBuilder builder) {
        return builder
                .expectsToReceive("a todo created event")
                .withContent(todoEventBody("CREATED"))
                .toPact();
    }

    @Pact(provider = PROVIDER, consumer = CONSUMER)
    public MessagePact todoUpdated(MessagePactBuilder builder) {
        return builder
                .expectsToReceive("a todo updated event")
                .withContent(todoEventBody("UPDATED"))
                .toPact();
    }

    @Pact(provider = PROVIDER, consumer = CONSUMER)
    public MessagePact todoDeleted(MessagePactBuilder builder) {
        return builder
                .expectsToReceive("a todo deleted event")
                .withContent(todoEventBody("DELETED"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "todoCreated", pactVersion = PactSpecVersion.V3)
    void consumesCreatedEvent(List<Message> messages) throws Exception {
        assertCanConsume(messages, "CREATED");
    }

    @Test
    @PactTestFor(pactMethod = "todoUpdated", pactVersion = PactSpecVersion.V3)
    void consumesUpdatedEvent(List<Message> messages) throws Exception {
        assertCanConsume(messages, "UPDATED");
    }

    @Test
    @PactTestFor(pactMethod = "todoDeleted", pactVersion = PactSpecVersion.V3)
    void consumesDeletedEvent(List<Message> messages) throws Exception {
        assertCanConsume(messages, "DELETED");
    }

    /**
     * Prova que o {@link TodoEvent} deste servico consegue desserializar o corpo
     * declarado no contrato e que os campos chegam preenchidos. Se o produtor
     * mudar a forma do payload de um jeito que este record nao entenda, o teste
     * do provider quebra — que eh exatamente o ponto do contract testing.
     */
    private void assertCanConsume(List<Message> messages, String expectedAction) throws Exception {
        assertThat(messages).hasSize(1);

        TodoEvent event = objectMapper.readValue(messages.get(0).contentsAsString(), TodoEvent.class);

        assertThat(event.todoId()).isNotBlank();
        assertThat(event.title()).isNotBlank();
        assertThat(event.action()).isEqualTo(expectedAction);
        assertThat(event.occurredAt()).isNotNull();
    }
}
