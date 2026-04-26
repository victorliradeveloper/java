package com.javanauta.cadastro_usuario.mapper;

import com.javanauta.cadastro_usuario.dto.request.UsuarioRequestDTO;
import com.javanauta.cadastro_usuario.dto.request.UsuarioUpdateRequestDTO;
import com.javanauta.cadastro_usuario.dto.response.UsuarioResponseDTO;
import com.javanauta.cadastro_usuario.infrastructure.entitys.Usuario;
import org.springframework.stereotype.Component;

/**
 * Classe responsável por converter entre Entity (Usuario) e DTOs
 * Centraliza a lógica de mapeamento, facilitando manutenção
 */
@Component
public class UsuarioMapper {

    /**
     * Converte UsuarioRequestDTO para Entity Usuario
     * Usado ao criar um novo usuário
     */
    public Usuario toEntity(UsuarioRequestDTO dto) {
        if (dto == null) {
            return null;
        }

        return Usuario.builder()
                .email(dto.getEmail())
                .nome(dto.getNome())
                .build();
    }

    /**
     * Converte UsuarioEntity para UsuarioResponseDTO
     * Usado ao retornar dados do usuário na API
     */
    public UsuarioResponseDTO toResponseDTO(Usuario usuario) {
        if (usuario == null) {
            return null;
        }

        return UsuarioResponseDTO.builder()
                .id(usuario.getId())
                .email(usuario.getEmail())
                .nome(usuario.getNome())
                .build();
    }

    /**
     * Atualiza uma Entity Usuario com dados do UsuarioUpdateRequestDTO
     * Mantém os valores originais se o campo no DTO for nulo
     */
    public Usuario updateEntityFromDTO(UsuarioUpdateRequestDTO dto, Usuario usuarioExistente) {
        if (dto == null || usuarioExistente == null) {
            return usuarioExistente;
        }

        return Usuario.builder()
                .id(usuarioExistente.getId())
                .email(dto.getEmail() != null ? dto.getEmail() : usuarioExistente.getEmail())
                .nome(dto.getNome() != null ? dto.getNome() : usuarioExistente.getNome())
                .build();
    }
}

