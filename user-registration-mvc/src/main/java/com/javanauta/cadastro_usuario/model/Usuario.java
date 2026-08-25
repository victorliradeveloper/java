package com.javanauta.cadastro_usuario.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entidade JPA que representa a tabela usuario no banco de dados
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "usuario")
@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "nome")
    private String nome;
}

