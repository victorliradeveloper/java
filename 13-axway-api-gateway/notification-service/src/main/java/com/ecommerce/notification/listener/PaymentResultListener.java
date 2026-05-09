package com.ecommerce.notification.listener;

import com.ecommerce.notification.event.PaymentResultEvent;
import com.ecommerce.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = "${rabbitmq.payment-processed.queue}")
    public void handlePaymentSuccess(PaymentResultEvent event) {
        log.info("Received payment success for order: {}", event.orderId());
        notificationService.handlePaymentSuccess(event);
    }

    @RabbitListener(queues = "${rabbitmq.payment-failed.queue}")
    public void handlePaymentFailed(PaymentResultEvent event) {
        log.info("Received payment failed for order: {}", event.orderId());
        notificationService.handlePaymentFailed(event);
    }
}
