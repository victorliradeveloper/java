package com.javanauta.cadastro_usuario.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para retornar dados do usuário na resposta da API
 * Expõe apenas os campos necessários para o cliente
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

