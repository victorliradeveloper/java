package com.ecommerce.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

@Configuration
public class HttpClientConfig {

    @Bean
    @Profile("!mtls")
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @Profile("mtls")
    public RestTemplate mTlsRestTemplate(
            @Value("${payment-service.ssl.key-store}") Resource keyStore,
            @Value("${payment-service.ssl.key-store-password}") String keyStorePassword,
            @Value("${payment-service.ssl.trust-store}") Resource trustStore,
            @Value("${payment-service.ssl.trust-store-password}") String trustStorePassword
    ) throws Exception {

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(keyStore.getInputStream(), keyStorePassword.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyStorePassword.toCharArray());

        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(trustStore.getInputStream(), trustStorePassword.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        var httpClient = org.apache.hc.client5.http.impl.classic.HttpClients.custom()
                .setConnectionManager(
                        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                                .setSSLSocketFactory(
                                        org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder.create()
                                                .setSslContext(sslContext)
                                                .build()
                                )
                                .build()
                )
                .build();

        var requestFactory = new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(requestFactory);
    }
}
