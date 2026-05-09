package com.example.emailservice.infrastructure.template;

import com.example.emailservice.infrastructure.messaging.UserEventDTO;
import org.springframework.stereotype.Component;

@Component
public class UserLoginEmailTemplate implements EmailTemplate<UserEventDTO.Payload> {

    @Override
    public String subject(UserEventDTO.Payload p) {
        return "Novo acesso detectado na sua conta";
    }

    @Override
    public String body(UserEventDTO.Payload p) {
        return """
                Olá, %s!

                Detectamos um novo acesso à sua conta com o email: %s

                Se não foi você, redefina sua senha imediatamente.

                Atenciosamente,
                Equipe User Service
                """.formatted(p.name(), p.email());
    }
}
