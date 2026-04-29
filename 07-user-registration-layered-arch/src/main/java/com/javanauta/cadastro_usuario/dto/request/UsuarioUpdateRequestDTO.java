package com.javanauta.cadastro_usuario.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * DTO para receber dados de atualização de usuário da API
 * Todos os campos são opcionais (permitem atualização parcial)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioUpdateRequestDTO {

    @Email(message = "Email deve ter um formato válido")
    private String email;

    @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
    private String nome;
}

