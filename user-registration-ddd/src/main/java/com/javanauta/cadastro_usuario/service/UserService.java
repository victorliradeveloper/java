package com.javanauta.cadastro_usuario.service;

import com.javanauta.cadastro_usuario.dto.UsuarioRequestDTO;
import com.javanauta.cadastro_usuario.dto.UsuarioResponseDTO;

/**
 * Interface do serviço de usuário
 */
public interface UserService {

    /**
     * Salva um novo usuário no banco de dados
     */
    UsuarioResponseDTO salvarUsuario(UsuarioRequestDTO usuarioRequest);

    /**
     * Busca um usuário pelo e-mail
     */
    UsuarioResponseDTO buscarUsuarioPorEmail(String email);

    /**
     * Deleta um usuário com base no e-mail
     */
    void deletarUsuarioPorEmail(String email);

    /**
     * Atualiza um usuário com base no ID informado
     */
    UsuarioResponseDTO atualizarUsuarioPorId(Integer id, UsuarioRequestDTO usuarioRequest);
}

