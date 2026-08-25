package com.javanauta.cadastro_usuario.service;

import com.javanauta.cadastro_usuario.dto.UsuarioRequestDTO;
import com.javanauta.cadastro_usuario.dto.UsuarioResponseDTO;
import com.javanauta.cadastro_usuario.model.Usuario;
import com.javanauta.cadastro_usuario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;

    public UsuarioService(UsuarioRepository repository) {
        this.repository = repository;
    }

    public UsuarioResponseDTO salvarUsuario(UsuarioRequestDTO usuarioRequest) {
        Usuario usuarioSalvo = repository.saveAndFlush(converterParaEntidade(usuarioRequest));
        return converterParaResponseDTO(usuarioSalvo);
    }

    public UsuarioResponseDTO buscarUsuarioPorEmail(String email) {
        Usuario usuario = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email não encontrado"));
        return converterParaResponseDTO(usuario);
    }

    public UsuarioResponseDTO buscarUsuarioPorId(Integer id) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        return converterParaResponseDTO(usuario);
    }

    public UsuarioResponseDTO atualizarUsuarioPorId(Integer id, UsuarioRequestDTO usuarioRequest) {
        Usuario usuarioEntity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        Usuario usuarioAtualizado = Usuario.builder()
                .id(usuarioEntity.getId())
                .email(usuarioRequest.getEmail() != null ? usuarioRequest.getEmail() : usuarioEntity.getEmail())
                .nome(usuarioRequest.getNome() != null ? usuarioRequest.getNome() : usuarioEntity.getNome())
                .build();

        return converterParaResponseDTO(repository.saveAndFlush(usuarioAtualizado));
    }

    public void deletarUsuarioPorId(Integer id) {
        repository.deleteById(id);
    }

    private Usuario converterParaEntidade(UsuarioRequestDTO dto) {
        return Usuario.builder()
                .email(dto.getEmail())
                .nome(dto.getNome())
                .build();
    }

    private UsuarioResponseDTO converterParaResponseDTO(Usuario usuario) {
        return UsuarioResponseDTO.builder()
                .id(usuario.getId())
                .email(usuario.getEmail())
                .nome(usuario.getNome())
                .build();
    }
}
