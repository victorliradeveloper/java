package com.javanauta.cadastro_usuario.service.impl;

import com.javanauta.cadastro_usuario.dto.UsuarioRequestDTO;
import com.javanauta.cadastro_usuario.dto.UsuarioResponseDTO;
import com.javanauta.cadastro_usuario.model.User;
import com.javanauta.cadastro_usuario.repository.UserRepository;
import com.javanauta.cadastro_usuario.service.UserService;
import org.springframework.stereotype.Service;

/**
 * Implementação do serviço de usuário
 * Contém a lógica de negócio e conversão entre DTOs e entidades
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repository;

    public UserServiceImpl(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * Salva um novo usuário no banco de dados
     * Converte DTO de entrada para entidade, salva e retorna DTO de resposta
     */
    @Override
    public UsuarioResponseDTO salvarUsuario(UsuarioRequestDTO usuarioRequest) {
        User user = converterParaEntidade(usuarioRequest);
        User userSalvo = repository.saveAndFlush(user);
        return converterParaResponseDTO(userSalvo);
    }

    /**
     * Busca um usuário pelo e-mail
     * Retorna DTO de resposta
     */
    @Override
    public UsuarioResponseDTO buscarUsuarioPorEmail(String email) {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email não encontrado"));
        return converterParaResponseDTO(user);
    }

    /**
     * Deleta um usuário com base no e-mail
     */
    @Override
    public void deletarUsuarioPorEmail(String email) {
        repository.deleteByEmail(email);
    }

    /**
     * Atualiza um usuário com base no ID informado
     * Converte DTO de entrada para entidade, atualiza e retorna DTO de resposta
     */
    @Override
    public UsuarioResponseDTO atualizarUsuarioPorId(Integer id, UsuarioRequestDTO usuarioRequest) {
        User userEntity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario não encontrado"));

        // Atualiza apenas os campos não nulos do DTO
        User userAtualizado = User.builder()
                .id(userEntity.getId())
                .email(usuarioRequest.getEmail() != null ? usuarioRequest.getEmail() : userEntity.getEmail())
                .nome(usuarioRequest.getNome() != null ? usuarioRequest.getNome() : userEntity.getNome())
                .build();

        User userSalvo = repository.saveAndFlush(userAtualizado);
        return converterParaResponseDTO(userSalvo);
    }

    /**
     * Converte DTO de requisição para entidade
     */
    private User converterParaEntidade(UsuarioRequestDTO dto) {
        return User.builder()
                .email(dto.getEmail())
                .nome(dto.getNome())
                .build();
    }

    /**
     * Converte entidade para DTO de resposta
     */
    private UsuarioResponseDTO converterParaResponseDTO(User user) {
        return UsuarioResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nome(user.getNome())
                .build();
    }
}

