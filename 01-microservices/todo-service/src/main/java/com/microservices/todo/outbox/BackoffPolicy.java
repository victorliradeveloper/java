package com.microservices.todo.outbox;

import java.time.LocalDateTime;

/**
 * Politica de retry aplicada pelo {@code OutboxPublisher} apos uma falha no publish.
 *
 * <p>A implementacao decide quando o evento volta a ser elegivel pro proximo claim,
 * dado o numero de tentativas ja realizadas. Permite trocar a curva (linear, exponencial,
 * fixa) sem mexer na entidade nem no publisher.
 */
public interface BackoffPolicy {

    /**
     * @param attempts numero acumulado de tentativas (ja incrementado para a tentativa atual)
     * @return instante em que o evento volta a ser elegivel para o proximo claim
     */
    LocalDateTime nextAttemptAt(int attempts);
}
