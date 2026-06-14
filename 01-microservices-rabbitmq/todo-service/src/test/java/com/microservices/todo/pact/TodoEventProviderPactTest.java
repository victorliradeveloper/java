package com.microservices.todo.pact;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.microservices.todo.config.RabbitMQConfig;
import com.microservices.todo.event.TodoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Verificacao Pact do lado <b>provider</b> (todo-service).
 *
 * <p>Le os contratos gerados pelos consumers em {@code ../pacts} (ver
 * {@link PactFolder}) e, pra cada mensagem declarada, chama o
 * {@code @PactVerifyProvider} de mesma descricao. O metodo devolve o corpo JSON
 * que ESTE servico realmente publicaria; o Pact compara contra os matchers do
 * contrato e falha se algum campo sumir, mudar de tipo ou de formato.
 *
 * <p><b>Serializacao fiel a producao:</b> o body eh gerado pelo proprio
 * {@link RabbitMQConfig#messageConverter()} — o mesmo {@code Jackson2JsonMessageConverter}
 * que o {@code OutboxPublisher} usa em runtime. Assim o contrato verifica a
 * serializacao de verdade, nao um JSON montado a mao.
 */
@Provider("todo-service")
@PactFolder("../pacts")
class TodoEventProviderPactTest {

    // Exatamente o converter declarado na config de producao. Se a serializacao
    // do TodoEvent mudar la, este teste reflete a mudanca automaticamente.
    private final MessageConverter converter = new RabbitMQConfig().messageConverter();

    @BeforeEach
    void setTarget(PactVerificationContext context) {
        // MessageTestTarget: o alvo eh uma mensagem assincrona, nao um endpoint HTTP.
        context.setTarget(new MessageTestTarget());
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyContract(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @PactVerifyProvider("a todo created event")
    String createdEvent() {
        return serialize(TodoEvent.of("11111111-1111-1111-1111-111111111111", "Comprar leite", "CREATED"));
    }

    @PactVerifyProvider("a todo updated event")
    String updatedEvent() {
        return serialize(TodoEvent.of("11111111-1111-1111-1111-111111111111", "Comprar leite", "UPDATED"));
    }

    @PactVerifyProvider("a todo deleted event")
    String deletedEvent() {
        // occurredAt fixo so pra exercitar tambem o caminho sem LocalDateTime.now().
        return serialize(new TodoEvent("11111111-1111-1111-1111-111111111111", "Comprar leite",
                "DELETED", LocalDateTime.parse("2026-06-13T10:15:30")));
    }

    private String serialize(TodoEvent event) {
        Message message = converter.toMessage(event, new MessageProperties());
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }
}
