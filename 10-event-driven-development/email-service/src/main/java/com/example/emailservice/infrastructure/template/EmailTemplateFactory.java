package com.example.emailservice.infrastructure.template;

import com.example.emailservice.interfaces.dto.OrderEventDTO;
import com.example.emailservice.interfaces.dto.UserEventDTO;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplateFactory {

    // ── USER REGISTERED ────────────────────────────────────────────────────────

    public String registeredSubject(UserEventDTO.Payload p) {
        return "Bem-vindo ao sistema, " + p.name() + "!";
    }

    public String registeredBody(UserEventDTO.Payload p) {
        return """
                Olá, %s!

                Sua conta foi criada com sucesso.
                Email: %s

                Estamos felizes em ter você aqui. Aproveite!

                Atenciosamente,
                Equipe User Service
                """.formatted(p.name(), p.email());
    }

    // ── USER LOGIN ─────────────────────────────────────────────────────────────

    public String loginSubject(UserEventDTO.Payload p) {
        return "Novo acesso detectado na sua conta";
    }

    public String loginBody(UserEventDTO.Payload p) {
        return """
                Olá, %s!

                Detectamos um novo acesso à sua conta com o email: %s

                Se não foi você, redefina sua senha imediatamente.

                Atenciosamente,
                Equipe User Service
                """.formatted(p.name(), p.email());
    }

    // ── ORDER CREATED ──────────────────────────────────────────────────────────

    public String orderSubject(OrderEventDTO.Payload p) {
        return "Pedido confirmado! #" + p.orderId().substring(0, 8).toUpperCase();
    }

    public String orderBody(OrderEventDTO.Payload p) {
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

    // ── PASSWORD RESET ─────────────────────────────────────────────────────────

    public String passwordSubject(UserEventDTO.Payload p) {
        return "Redefinição de senha solicitada";
    }

    public String passwordBody(UserEventDTO.Payload p) {
        return """
                Olá, %s!

                Recebemos uma solicitação para redefinir a senha da conta: %s

                Clique no link abaixo para criar uma nova senha:
                https://userservice.com/reset-password?token=SIMULADO

                Este link expira em 1 hora.

                Se você não fez essa solicitação, ignore este email.

                Atenciosamente,
                Equipe User Service
                """.formatted(p.name(), p.email());
    }
}
