package com.javanauta.cadastro_usuario.business;

import com.javanauta.cadastro_usuario.dto.request.UsuarioRequestDTO;
import com.javanauta.cadastro_usuario.dto.request.UsuarioUpdateRequestDTO;
import com.javanauta.cadastro_usuario.dto.response.UsuarioResponseDTO;
import com.javanauta.cadastro_usuario.infrastructure.entitys.Usuario;
import com.javanauta.cadastro_usuario.infrastructure.repository.UsuarioRepository;
import com.javanauta.cadastro_usuario.mapper.UsuarioMapper;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    
    // Mapper responsável por converter entre Entity e DTOs
    private final UsuarioMapper mapper;

    // Injeção de dependência do repositório e mapper via construtor
    public UsuarioService(UsuarioRepository repository, UsuarioMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    // Método para salvar um novo usuário no banco
    // Recebe um DTO de request e retorna um DTO de response
    public UsuarioResponseDTO salvarUsuario(UsuarioRequestDTO usuarioRequest){
        // Converte o DTO para Entity
        Usuario usuario = mapper.toEntity(usuarioRequest);
        // Salva e força a escrita imediata no banco
        Usuario usuarioSalvo = repository.saveAndFlush(usuario);
        // Converte a Entity para DTO de resposta e retorna
        return mapper.toResponseDTO(usuarioSalvo);
    }

    // Busca um usuário pelo e-mail
    // Retorna um DTO de response ao invés da Entity
    public UsuarioResponseDTO buscarUsuarioPorEmail(String email){
        // Caso não encontre o e-mail, lança uma exceção
        Usuario usuario = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email não encontrado"));
        // Converte a Entity para DTO de resposta
        return mapper.toResponseDTO(usuario);
    }

    public void deletarUsuarioPorEmail(String email){
        repository.deleteByEmail(email);
    }

    // Atualiza um usuário com base no ID informado
    // Recebe um DTO de update e retorna um DTO de response
    public UsuarioResponseDTO atualizarUsuarioPorId(Integer id, UsuarioUpdateRequestDTO usuarioUpdateRequest){
        // Busca o usuário existente no banco
        Usuario usuarioEntity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario não encontrado"));

        // Usa o mapper para atualizar a Entity com os dados do DTO
        // O mapper mantém valores originais se o campo no DTO for nulo
        Usuario usuarioAtualizado = mapper.updateEntityFromDTO(usuarioUpdateRequest, usuarioEntity);

        // Salva as alterações no banco
        Usuario usuarioSalvo = repository.saveAndFlush(usuarioAtualizado);
        
        // Converte a Entity atualizada para DTO de resposta
        return mapper.toResponseDTO(usuarioSalvo);
    }
}
