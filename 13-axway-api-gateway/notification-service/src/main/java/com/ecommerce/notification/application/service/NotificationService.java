package com.ecommerce.notification.application.service;

import com.ecommerce.notification.domain.model.Notification;
import com.ecommerce.notification.domain.model.NotificationType;
import com.ecommerce.notification.domain.model.event.PaymentResultEvent;
import com.ecommerce.notification.domain.port.in.FindNotificationsUseCase;
import com.ecommerce.notification.domain.port.in.ProcessPaymentResultUseCase;
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
public class NotificationService implements ProcessPaymentResultUseCase, FindNotificationsUseCase {

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;

    @Override
    @Transactional
    public void onPaymentSuccess(PaymentResultEvent event) {
        var subject = "Pagamento confirmado - Pedido #" + event.orderId();
        var body = buildPaymentSuccessMessage(event);
        deliver(event, NotificationType.PAYMENT_SUCCESS, subject, body);
    }

    @Override
    @Transactional
    public void onPaymentFailed(PaymentResultEvent event) {
        var subject = "Falha no pagamento - Pedido #" + event.orderId();
        var body = buildPaymentFailedMessage(event);
        deliver(event, NotificationType.PAYMENT_FAILED, subject, body);
    }

    @Override
    public List<Notification> findByUserId(Long userId) {
        return notificationRepository.findByUserId(userId);
    }

    private void deliver(PaymentResultEvent event, NotificationType type, String subject, String body) {
        var recipient = resolveEmail(event.userId());
        var notification = Notification.newPending(event.userId(), event.orderId(), type, recipient, subject, body);
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
}
