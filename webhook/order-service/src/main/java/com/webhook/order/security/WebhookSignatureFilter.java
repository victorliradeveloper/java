package com.webhook.order.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class WebhookSignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureFilter.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String WEBHOOK_PATH_PREFIX = "/webhooks/";

    private final String secret;

    public WebhookSignatureFilter(@Value("${webhook.secret}") String secret) {
        this.secret = secret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(WEBHOOK_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
        String header = cached.getHeader(SIGNATURE_HEADER);

        if (header == null || !header.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Webhook rejected: missing or malformed signature");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing or invalid signature");
            return;
        }

        String provided = header.substring(SIGNATURE_PREFIX.length());
        String expected = sign(cached.getCachedBody());

        if (!MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Webhook rejected: signature mismatch");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid signature");
            return;
        }

        chain.doFilter(cached, response);
    }

    private String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC verification failed", e);
        }
    }
}
