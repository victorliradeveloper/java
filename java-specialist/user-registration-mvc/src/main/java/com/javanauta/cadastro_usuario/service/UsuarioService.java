package com.javanauta.cadastro_usuario.service;

import com.javanauta.cadastro_usuario.dto.UsuarioRequestDTO;
import com.javanauta.cadastro_usuario.dto.UsuarioResponseDTO;
import com.javanauta.cadastro_usuario.model.Usuario;
import com.javanauta.cadastro_usuario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

/**
 * Service - Camada de negócio (Model)
 * Contém a lógica de negócio e conversão entre DTOs e entidades
 */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;

    public UsuarioService(UsuarioRepository repository) {
        this.repository = repository;
    }

    /**
     * Salva um novo usuário no banco de dados
     * Converte DTO de entrada para entidade, salva e retorna DTO de resposta
     */
    public UsuarioResponseDTO salvarUsuario(UsuarioRequestDTO usuarioRequest) {
        Usuario usuario = converterParaEntidade(usuarioRequest);
        Usuario usuarioSalvo = repository.saveAndFlush(usuario);
        return converterParaResponseDTO(usuarioSalvo);
    }

    /**
     * Busca um usuário pelo e-mail
     * Retorna DTO de resposta
     */
    public UsuarioResponseDTO buscarUsuarioPorEmail(String email) {
        Usuario usuario = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email não encontrado"));
        return converterParaResponseDTO(usuario);
    }

    /**
     * Deleta um usuário com base no e-mail
     */
    public void deletarUsuarioPorEmail(String email) {
        repository.deleteByEmail(email);
    }

    /**
     * Atualiza um usuário com base no ID informado
     * Converte DTO de entrada para entidade, atualiza e retorna DTO de resposta
     */
    public UsuarioResponseDTO atualizarUsuarioPorId(Integer id, UsuarioRequestDTO usuarioRequest) {
        Usuario usuarioEntity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario não encontrado"));

        // Atualiza apenas os campos não nulos do DTO
        Usuario usuarioAtualizado = Usuario.builder()
                .id(usuarioEntity.getId())
                .email(usuarioRequest.getEmail() != null ? usuarioRequest.getEmail() : usuarioEntity.getEmail())
                .nome(usuarioRequest.getNome() != null ? usuarioRequest.getNome() : usuarioEntity.getNome())
                .build();

        Usuario usuarioSalvo = repository.saveAndFlush(usuarioAtualizado);
        return converterParaResponseDTO(usuarioSalvo);
    }

    /**
     * Converte DTO de requisição para entidade
     */
    private Usuario converterParaEntidade(UsuarioRequestDTO dto) {
        return Usuario.builder()
                .email(dto.getEmail())
                .nome(dto.getNome())
                .build();
    }

    /**
     * Converte entidade para DTO de resposta
     */
    private UsuarioResponseDTO converterParaResponseDTO(Usuario usuario) {
        return UsuarioResponseDTO.builder()
                .id(usuario.getId())
                .email(usuario.getEmail())
                .nome(usuario.getNome())
                .build();
    }
}

