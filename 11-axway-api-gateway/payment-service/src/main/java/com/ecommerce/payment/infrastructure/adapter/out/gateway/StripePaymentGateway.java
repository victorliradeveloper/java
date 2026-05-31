package com.ecommerce.payment.infrastructure.adapter.out.gateway;

import com.ecommerce.payment.domain.model.GatewayPaymentIntent;
import com.ecommerce.payment.domain.port.out.PaymentGateway;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StripePaymentGateway implements PaymentGateway {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @PostConstruct
    void init() {
        Stripe.apiKey = secretKey;
    }

    @Override
    public GatewayPaymentIntent createPaymentIntent(BigDecimal amount, String currency, Long orderId) {
        try {
            var params = PaymentIntentCreateParams.builder()
                    .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                    .setCurrency(currency.toLowerCase())
                    .putMetadata("orderId", String.valueOf(orderId))
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            return new GatewayPaymentIntent(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
