package com.example.emailservice.infrastructure.template;

import com.example.emailservice.infrastructure.messaging.UserEventDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetEmailTemplate implements EmailTemplate<UserEventDTO.Payload> {

    @Value("${password-reset.url}")
    private String resetUrl;

    @Override
    public String subject(UserEventDTO.Payload p) {
        return "Redefinição de senha solicitada";
    }

    @Override
    public String body(UserEventDTO.Payload p) {
        return """
                Olá, %s!

                Recebemos uma solicitação para redefinir a senha da conta: %s

                Clique no link abaixo para criar uma nova senha:
                %s

                Este link expira em 1 hora.

                Se você não fez essa solicitação, ignore este email.

                Atenciosamente,
                Equipe User Service
                """.formatted(p.name(), p.email(), resetUrl);
    }
}
