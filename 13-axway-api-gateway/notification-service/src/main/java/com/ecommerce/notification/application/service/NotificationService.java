package com.ecommerce.notification.application.service;

import com.ecommerce.notification.domain.model.Notification;
import com.ecommerce.notification.domain.model.NotificationType;
import com.ecommerce.notification.domain.model.event.PaymentResultEvent;
import com.ecommerce.notification.domain.model.event.UserProfileCreatedEvent;
import com.ecommerce.notification.domain.port.in.FindNotificationsUseCase;
import com.ecommerce.notification.domain.port.in.ProcessPaymentResultUseCase;
import com.ecommerce.notification.domain.port.in.SendWelcomeEmailUseCase;
import com.ecommerce.notification.domain.port.out.EmailSender;
import com.ecommerce.notification.domain.port.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements
        ProcessPaymentResultUseCase,
        SendWelcomeEmailUseCase,
        FindNotificationsUseCase {

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;

    @Override
    @Transactional
    public void onPaymentSuccess(PaymentResultEvent event) {
        var subject = "Pagamento confirmado - Pedido #" + event.orderId();
        var body = buildPaymentSuccessMessage(event);
        deliverPayment(event, NotificationType.PAYMENT_SUCCESS, subject, body);
    }

    @Override
    @Transactional
    public void onPaymentFailed(PaymentResultEvent event) {
        var subject = "Falha no pagamento - Pedido #" + event.orderId();
        var body = buildPaymentFailedMessage(event);
        deliverPayment(event, NotificationType.PAYMENT_FAILED, subject, body);
    }

    @Override
    @Transactional
    public void onProfileCreated(UserProfileCreatedEvent event) {
        var subject = "Bem-vindo(a) à E-commerce!";
        var body = buildWelcomeMessage(event);
        deliver(event.userId(), null, NotificationType.WELCOME, event.email(), subject, body);
    }

    @Override
    public List<Notification> findByUserId(Long userId) {
        return notificationRepository.findByUserId(userId);
    }

    private void deliverPayment(PaymentResultEvent event, NotificationType type, String subject, String body) {
        deliver(event.userId(), event.orderId(), type, resolveEmail(event.userId()), subject, body);
    }

    private void deliver(Long userId, Long orderId, NotificationType type, String recipient, String subject, String body) {
        var notification = Notification.newPending(userId, orderId, type, recipient, subject, body);
        var saved = notificationRepository.save(notification);

        try {
            emailSender.send(recipient, subject, body);
            saved.markSent();
        } catch (Exception e) {
            log.error("Failed to send notification email to {}: {}", recipient, e.getMessage());
            saved.markFailed(e.getMessage());
        }
        notificationRepository.save(saved);
    }

    private String resolveEmail(Long userId) {
        return "user" + userId + "@ecommerce.com";
    }

    private String buildPaymentSuccessMessage(PaymentResultEvent event) {
        return """
                Olá!

                Seu pagamento foi confirmado com sucesso.

                Pedido: #%d
                Valor: %s %s
                ID do pagamento: %s

                Em breve seu pedido será processado.

                Equipe E-commerce
                """.formatted(event.orderId(), event.currency(), event.amount(), event.stripePaymentIntentId());
    }

    private String buildPaymentFailedMessage(PaymentResultEvent event) {
        return """
                Olá!

                Infelizmente seu pagamento não foi aprovado.

                Pedido: #%d
                Valor: %s %s
                Motivo: %s

                Por favor, tente novamente ou entre em contato com nosso suporte.

                Equipe E-commerce
                """.formatted(event.orderId(), event.currency(), event.amount(),
                event.failureReason() != null ? event.failureReason() : "Não especificado");
    }

    private String buildWelcomeMessage(UserProfileCreatedEvent event) {
        return """
                Olá, %s!

                Seja muito bem-vindo(a) à E-commerce. Seu cadastro foi concluído com sucesso.

                Você já pode explorar nosso catálogo e fazer seu primeiro pedido.

                Qualquer dúvida, conte com a gente.

                Equipe E-commerce
                """.formatted(event.name());
    }
}
