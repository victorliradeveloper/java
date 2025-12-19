package com.javanauta.cadastro_usuario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para retornar dados do usuário
 * Usado para controlar quais informações são expostas na API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponseDTO {
    
    private Integer id;
    private String email;
    private String nome;
}

