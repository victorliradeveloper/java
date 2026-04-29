package com.javanauta.todo_app.repository;

import com.javanauta.todo_app.model.Todo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TodoRepository extends JpaRepository<Todo, Long>, JpaSpecificationExecutor<Todo> {

    @Query("SELECT t FROM Todo t WHERE (:cursor IS NULL OR t.id > :cursor) ORDER BY t.id ASC")
    List<Todo> findWithCursor(@Param("cursor") Long cursor, Pageable pageable);
}
