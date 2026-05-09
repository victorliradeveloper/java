package com.example.emailservice.infrastructure.template;

import com.example.emailservice.infrastructure.messaging.OrderEventDTO;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedEmailTemplate implements EmailTemplate<OrderEventDTO.Payload> {

    private static final int SHORT_ID_LENGTH = 8;

    @Override
    public String subject(OrderEventDTO.Payload p) {
        return "Pedido confirmado! #" + shortId(p.orderId());
    }

    @Override
    public String body(OrderEventDTO.Payload p) {
        return """
                Olá, %s!

                Seu pedido foi recebido com sucesso.

                Detalhes:
                  Pedido: #%s
                  Descrição: %s
                  Valor: R$ %.2f

                Em breve entraremos em contato.

                Atenciosamente,
                Equipe User Service
                """.formatted(p.name(), shortId(p.orderId()), p.description(), p.amount());
    }

    private String shortId(String orderId) {
        int end = Math.min(orderId.length(), SHORT_ID_LENGTH);
        return orderId.substring(0, end).toUpperCase();
    }
}
