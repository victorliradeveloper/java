package com.example.emailservice.infrastructure.template;

import com.example.emailservice.infrastructure.messaging.UserEventDTO;
import org.springframework.stereotype.Component;

@Component
public class UserRegisteredEmailTemplate implements EmailTemplate<UserEventDTO.Payload> {

    @Override
    public String subject(UserEventDTO.Payload p) {
        return "Bem-vindo ao sistema, " + p.name() + "!";
    }

    @Override
    public String body(UserEventDTO.Payload p) {
        return """
                Olá, %s!

                Sua conta foi criada com sucesso.
                Email: %s

                Estamos felizes em ter você aqui. Aproveite!

                Atenciosamente,
                Equipe User Service
                """.formatted(p.name(), p.email());
    }
}
