package com.example.emailservice.infrastructure.template;

import com.example.emailservice.interfaces.dto.OrderEventDTO;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedEmailTemplate implements EmailTemplate<OrderEventDTO.Payload> {

    @Override
    public String subject(OrderEventDTO.Payload p) {
        return "Pedido confirmado! #" + p.orderId().substring(0, 8).toUpperCase();
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
                """.formatted(p.name(), p.orderId().substring(0, 8).toUpperCase(), p.description(), p.amount());
    }
}
