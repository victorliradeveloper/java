package com.webhook.payment.client;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

public class WebhookSigningInterceptor implements ClientHttpRequestInterceptor {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final String secret;

    public WebhookSigningInterceptor(String secret) {
        this.secret = secret;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().add(SIGNATURE_HEADER, SIGNATURE_PREFIX + sign(body));
        return execution.execute(request, body);
    }

    private String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }
}
