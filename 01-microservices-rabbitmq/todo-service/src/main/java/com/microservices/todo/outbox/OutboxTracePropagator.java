package com.microservices.todo.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Costura o trace atraves da fronteira assincrona do outbox.
 *
 * <p>O evento e' gravado dentro da request HTTP (que tem trace context) mas
 * publicado depois, num {@code @Scheduled} rodando em outra thread sem esse
 * contexto. {@link #capture()} serializa o trace corrente como W3C traceparent
 * pra persistir na linha; {@link #restore} reabre esse contexto na thread do
 * publisher, de modo que os logs da publicacao e os headers AMQP propagados ao
 * consumer fiquem todos sob o mesmo traceId da request original.
 */
@Component
@RequiredArgsConstructor
public class OutboxTracePropagator {

    private static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    /** Trace corrente como W3C traceparent, ou {@code null} se nao ha trace ativo. */
    public String capture() {
        if (tracer.currentTraceContext() == null || tracer.currentTraceContext().context() == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(tracer.currentTraceContext().context(), carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    /**
     * Executa {@code work} sob o trace reconstruido a partir do traceparent.
     * Se {@code traceParent} for null (evento sem contexto), executa direto —
     * o publisher segue com seu proprio trace do scheduler.
     */
    public <T> T restore(String traceParent, Supplier<T> work) {
        if (traceParent == null) {
            return work.get();
        }
        Span span = propagator.extract(Map.of(TRACEPARENT, traceParent), Map::get)
                .name("outbox-publish")
                .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return work.get();
        } finally {
            span.end();
        }
    }

    public void restore(String traceParent, Runnable work) {
        restore(traceParent, () -> {
            work.run();
            return null;
        });
    }
}
