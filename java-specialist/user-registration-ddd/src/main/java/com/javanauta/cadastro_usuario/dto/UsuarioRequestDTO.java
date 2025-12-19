package com.javanauta.cadastro_usuario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para receber dados de entrada do usuário
 * Usado para desacoplar a camada de apresentação da camada de domínio
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRequestDTO {
    
    private String email;
    private String nome;
}

