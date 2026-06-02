package com.microservices.todo.service;

import com.microservices.todo.downstream.DownstreamNotifier;
import com.microservices.todo.downstream.TodoEventPayload;
import com.microservices.todo.dto.request.TodoRequestDTO;
import com.microservices.todo.dto.request.TodoUpdateDTO;
import com.microservices.todo.dto.response.TodoResponseDTO;
import com.microservices.todo.infrastructure.entity.Todo;
import com.microservices.todo.infrastructure.repository.TodoRepository;
import com.microservices.todo.mapper.TodoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orquestra "persistir + notificar downstreams" na versao SINCRONA.
 *
 * <h3>Fluxo</h3>
 * <ol>
 *   <li>{@link TodoPersistenceService} faz o INSERT/UPDATE/DELETE em transacao
 *       propria — commit acontece antes de qualquer chamada HTTP.</li>
 *   <li>Apos o commit, este service chama os downstreams via Feign + Resilience4j
 *       ({@link DownstreamNotifier}).</li>
 *   <li>Falha de downstream cai no fallback e NAO derruba o request principal.</li>
 * </ol>
 *
 * <h3>Por que persistencia em outro bean?</h3>
 * {@code @Transactional} so' funciona via proxy do Spring. Chamada do mesmo
 * bean ({@code this.persist...}) ignora o proxy e o interceptor transacional
 * nao roda. Por isso a persistencia vive no {@link TodoPersistenceService}.
 *
 * <h3>Trade-off vs versao com mensageria (01-microservices)</h3>
 * Sem outbox, a entrega de eventos pros downstreams nao tem garantia. Se o
 * audit-service esta caido por 5 minutos, todos os eventos desse periodo
 * sao perdidos (o fallback so' loga). Em troca, zero infraestrutura de
 * mensageria e simplicidade radical. Adequado quando os eventos sao
 * "nice to have" — pra eventos criticos, mantenha mensageria + outbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoPersistenceService persistence;
    private final DownstreamNotifier downstreamNotifier;
    private final TodoMapper mapper;
    private final TodoRepository repository;

    public TodoResponseDTO create(TodoRequestDTO dto) {
        log.info("create dto={}", dto);
        TodoResponseDTO response = persistence.create(dto);
        notifyDownstreams(response.id(), response.title(), "CREATED");
        return response;
    }

    public List<TodoResponseDTO> findAll() {
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    public TodoResponseDTO findById(String id) {
        return mapper.toResponse(persistence.getOrThrow(id));
    }

    public TodoResponseDTO update(String id, TodoUpdateDTO dto) {
        TodoResponseDTO response = persistence.update(id, dto);
        notifyDownstreams(response.id(), response.title(), "UPDATED");
        return response;
    }

    public void delete(String id) {
        Todo deleted = persistence.delete(id);
        notifyDownstreams(deleted.getId(), deleted.getTitle(), "DELETED");
    }

    private void notifyDownstreams(String todoId, String title, String action) {
        // eventId UUID por chamada — funciona como chave de idempotencia nos
        // downstreams (ON CONFLICT DO NOTHING). Se o Retry do Resilience4j
        // retentar, o mesmo eventId chega e e' dedupado.
        TodoEventPayload event = TodoEventPayload.of(
                UUID.randomUUID().toString(), todoId, title, action);
        // Cada notify tem seu proprio CircuitBreaker e fallback — falha de
        // um nao afeta o outro. Sequencial: o requisito eh sincrono total.
        downstreamNotifier.notifyAudit(event);
            downstreamNotifier.notifyNotification(event);
    }
}
