package com.microservices.todo.outbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracao tipada do outbox publisher.
 *
 * <p>Substitui um conjunto de {@code @Value} dispersos por um record imutavel
 * com validacao declarativa. Habilitado em {@code OutboxConfig}.
 *
 * <p>Campos:
 * <ul>
 *   <li>{@code pollIntervalMs} — intervalo entre ciclos do {@code @Scheduled}.
 *       Referenciado como SpEL na anotacao porque {@code fixedDelayString} eh resolvido
 *       no parse time da anotacao, nao em runtime.</li>
 *   <li>{@code batchSize} — limite de eventos por ciclo. Protege a TX de virar grande demais.</li>
 *   <li>{@code leaseDurationMs} — TTL do lease quando um worker reivindica um evento.
 *       Deve ser maior que o tempo plausivel de publish pra evitar dois workers no mesmo doc.</li>
 *   <li>{@code backoff} — politica de retry pra falhas no publish. Veja {@link Backoff}.</li>
 * </ul>
 */
@Validated
@ConfigurationProperties("outbox")
public record OutboxProperties(

        @DefaultValue("2000") @Min(100) long pollIntervalMs,

        @DefaultValue("50") @Min(1) int batchSize,

        @DefaultValue("30000") @Min(1000) long leaseDurationMs,

        @NotNull @Valid @DefaultValue Backoff backoff
) {

    /**
     * Parametros do backoff exponencial com jitter aplicado em {@code ExponentialJitterBackoffPolicy}.
     *
     * <p>Sequencia tipica com defaults (initial=2000, max=60000):
     * 2s, 4s, 8s, 16s, 32s, 60s (cap), 60s, ... com +/-25% de jitter por tentativa.
     */
    public record Backoff(

            @DefaultValue("2000") @Min(1) long initialMs,

            @DefaultValue("60000") @Min(1) long maxMs
    ) {
        public Backoff {
            if (maxMs < initialMs) {
                throw new IllegalArgumentException(
                        "outbox.backoff.max-ms (" + maxMs + ") deve ser >= initial-ms (" + initialMs + ")");
            }
        }
    }
}
