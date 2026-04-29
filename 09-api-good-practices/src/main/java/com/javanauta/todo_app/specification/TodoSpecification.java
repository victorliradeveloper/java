package com.javanauta.todo_app.specification;

import com.javanauta.todo_app.dto.TodoFilterDTO;
import com.javanauta.todo_app.model.Todo;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class TodoSpecification {

    private TodoSpecification() {}

    public static Specification<Todo> comFiltros(TodoFilterDTO filtro) {
        return Specification
                .where(tituloComo(filtro.getTitulo()))
                .and(concluidoIgual(filtro.getConcluido()))
                .and(dataLimiteDe(filtro.getDataLimiteDe()))
                .and(dataLimiteAte(filtro.getDataLimiteAte()));
    }

    private static Specification<Todo> tituloComo(String titulo) {
        return (root, query, cb) -> titulo == null || titulo.isBlank() ? null
                : cb.like(cb.lower(root.get("titulo")), "%" + titulo.toLowerCase() + "%");
    }

    private static Specification<Todo> concluidoIgual(Boolean concluido) {
        return (root, query, cb) -> concluido == null ? null
                : cb.equal(root.get("concluido"), concluido);
    }

    private static Specification<Todo> dataLimiteDe(LocalDateTime de) {
        return (root, query, cb) -> de == null ? null
                : cb.greaterThanOrEqualTo(root.get("dataLimite"), de);
    }

    private static Specification<Todo> dataLimiteAte(LocalDateTime ate) {
        return (root, query, cb) -> ate == null ? null
                : cb.lessThanOrEqualTo(root.get("dataLimite"), ate);
    }
}
