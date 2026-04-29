package com.javanauta.todo_app.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "todo")
@Entity
public class Todo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "concluido", nullable = false)
    private boolean concluido;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_limite")
    private LocalDateTime dataLimite;

    @PrePersist
    public void prePersist() {
        this.dataCriacao = LocalDateTime.now();
        this.concluido = false;
    }
}
