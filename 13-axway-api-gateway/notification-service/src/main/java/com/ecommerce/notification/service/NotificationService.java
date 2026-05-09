package com.ecommerce.notification.service;

import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.entity.Notification;
import com.ecommerce.notification.entity.NotificationStatus;
import com.ecommerce.notification.entity.NotificationType;
import com.ecommerce.notification.event.PaymentResultEvent;
import com.ecommerce.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    @Transactional
    public void handlePaymentSuccess(PaymentResultEvent event) {
        String subject = "Pagamento confirmado - Pedido #" + event.orderId();
        String message = buildPaymentSuccessMessage(event);

        String recipient = resolveEmail(event.userId());

        var notification = Notification.builder()
                .userId(event.userId())
                .orderId(event.orderId())
                .type(NotificationType.PAYMENT_SUCCESS)
                .recipient(recipient)
                .subject(subject)
                .message(message)
                .build();

        notificationRepository.save(notification);

        try {
            emailService.send(recipient, subject, message);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
        }

        notificationRepository.save(notification);
    }

    @Transactional
    public void handlePaymentFailed(PaymentResultEvent event) {
        String subject = "Falha no pagamento - Pedido #" + event.orderId();
        String message = buildPaymentFailedMessage(event);

        String recipient = resolveEmail(event.userId());

        var notification = Notification.builder()
                .userId(event.userId())
                .orderId(event.orderId())
                .type(NotificationType.PAYMENT_FAILED)
                .recipient(recipient)
                .subject(subject)
                .message(message)
                .build();

        notificationRepository.save(notification);

        try {
            emailService.send(recipient, subject, message);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
        }

        notificationRepository.save(notification);
    }

    public List<NotificationResponse> findByUserId(Long userId) {
        return notificationRepository.findByUserId(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    private String resolveEmail(Long userId) {
        // Em produção, chamaria o user-service para obter o email
        // Por ora, usa um placeholder com o userId
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
