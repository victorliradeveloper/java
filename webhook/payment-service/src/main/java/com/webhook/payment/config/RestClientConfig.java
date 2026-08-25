package com.webhook.payment.config;

import com.webhook.payment.client.WebhookSigningInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient orderRestClient(
            @Value("${order-service.url}") String baseUrl,
            @Value("${webhook.secret}") String webhookSecret
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(new WebhookSigningInterceptor(webhookSecret))
                .build();
    }
}
