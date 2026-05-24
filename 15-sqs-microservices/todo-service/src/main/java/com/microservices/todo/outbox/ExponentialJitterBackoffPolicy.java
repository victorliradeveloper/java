package com.microservices.todo.outbox;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Backoff exponencial com jitter pra evitar thundering herd.
 *
 * <p>Formula: {@code initialMs * 2^(attempts-1)}, capado em {@code maxMs}, com jitter
 * uniforme de {@code +/-JITTER_FRACTION} aplicado por chamada. Sequencia tipica com
 * defaults (initial=2s, max=60s):
 *
 * <pre>
 *   attempts=1 -> ~2s   (1.5s - 2.5s)
 *   attempts=2 -> ~4s   (3s   - 5s)
 *   attempts=3 -> ~8s   (6s   - 10s)
 *   attempts=4 -> ~16s  (12s  - 20s)
 *   attempts=5 -> ~32s  (24s  - 40s)
 *   attempts=6+ -> ~60s (cap) (45s  - 75s)
 * </pre>
 *
 * <p>O cap em {@link #MAX_EXPONENT} protege o shift de overflow em sequencias de falha
 * absurdamente longas.
 */
public class ExponentialJitterBackoffPolicy implements BackoffPolicy {

    /** Multiplicador exponencial fixo. Multiplicador configuravel seria scope creep. */
    static final int MULTIPLIER = 2;

    /** Limite seguro do expoente. 2^20 = 1M — suficiente pra cobrir qualquer cap configurado. */
    static final int MAX_EXPONENT = 20;

    /** Fracao do delay aplicada como jitter em cada direcao. 0.25 = +/-25%. */
    static final double JITTER_FRACTION = 0.25;

    private final long initialMs;
    private final long maxMs;

    public ExponentialJitterBackoffPolicy(OutboxProperties.Backoff backoff) {
        this.initialMs = backoff.initialMs();
        this.maxMs = backoff.maxMs();
    }

    @Override
    public LocalDateTime nextAttemptAt(int attempts) {
        long delayMs = computeDelayMs(attempts);
        return LocalDateTime.now().plus(Duration.ofMillis(delayMs));
    }

    long computeDelayMs(int attempts) {
        int safeExponent = Math.clamp(attempts - 1L, 0, MAX_EXPONENT);
        long exponential = initialMs * (1L << safeExponent);
        long capped = Math.min(exponential, maxMs);
        return applyJitter(capped);
    }

    private long applyJitter(long baseMs) {
        // Faixa: [baseMs * (1 - JITTER_FRACTION), baseMs * (1 + JITTER_FRACTION)]
        double factor = 1.0 - JITTER_FRACTION + ThreadLocalRandom.current().nextDouble() * (2 * JITTER_FRACTION);
        return (long) (baseMs * factor);
    }
}
