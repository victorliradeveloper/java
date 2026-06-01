package com.microservices.audit.service;

import com.microservices.audit.dto.TodoAuditEventDTO;
import com.microservices.audit.infrastructure.entity.TodoAuditLog;
import com.microservices.audit.infrastructure.repository.TodoAuditLogRepository;
import com.microservices.audit.mapper.TodoAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Persiste eventos de auditoria recebidos via HTTP do todo-service.
 *
 * <p><b>Dedupe via PK natural</b>: o {@code eventId} (UUID gerado pelo
 * todo-service) eh chave primaria. {@code insertIfAbsent} usa
 * {@code ON CONFLICT DO NOTHING} — se a chamada foi retentada e ja foi
 * persistida antes, o INSERT eh ignorado. Mais simples que tabela
 * {@code processed_messages} separada: a propria insercao da auditoria
 * EH a verificacao atomica de "ja vi".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final TodoAuditLogRepository repository;
    private final TodoAuditLogMapper mapper;

    /**
     * Persiste o evento. Retorna {@code true} se inseriu de fato,
     * {@code false} se ja existia (dedupe). Em ambos os casos o caller
     * vê 200 OK — quem chama nao precisa distinguir.
     */
    public boolean record(TodoAuditEventDTO dto) {
        TodoAuditLog auditLog = mapper.toAuditLog(dto);
        boolean inserted = repository.insertIfAbsent(auditLog);
        if (inserted) {
            log.info("[AUDIT] registrado eventId={} todoId={} action={}",
                    dto.eventId(), dto.todoId(), dto.action());
        } else {
            log.info("[AUDIT][DEDUPE] evento duplicado descartado eventId={} todoId={} action={}",
                    dto.eventId(), dto.todoId(), dto.action());
        }
        return inserted;
    }
}
