package com.javanauta.todo_app.infrastructure.persistence.repository;

import com.javanauta.todo_app.domain.model.Todo;
import com.javanauta.todo_app.domain.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TodoJpaRepository extends JpaRepository<Todo, Long>, JpaSpecificationExecutor<Todo> {

    @Query("SELECT t FROM Todo t WHERE t.user = :user AND (:cursor IS NULL OR t.id > :cursor) ORDER BY t.id ASC")
    List<Todo> findWithCursor(@Param("user") User user, @Param("cursor") Long cursor, Pageable pageable);
}
