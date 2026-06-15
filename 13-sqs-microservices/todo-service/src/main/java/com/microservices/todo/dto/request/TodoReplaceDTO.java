package com.microservices.todo.dto.request;

import com.microservices.todo.infrastructure.entity.Priority;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload do PUT — substituicao total do recurso.
 *
 * Diferente do {@link TodoUpdateDTO} (usado no PATCH, todos os campos opcionais),
 * aqui o {@code title} e obrigatorio: PUT representa o recurso INTEIRO, entao um
 * title ausente nao faz sentido. Campos omitidos sao resetados pelo mapper
 * (description -> null; completed -> false; priority -> MEDIUM).
 */
public record TodoReplaceDTO(
        @NotBlank String title,
        String description,
        Boolean completed,
        Priority priority
) {}
