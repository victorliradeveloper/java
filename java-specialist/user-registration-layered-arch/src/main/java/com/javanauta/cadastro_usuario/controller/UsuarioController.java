package com.javanauta.cadastro_usuario.controller;

import com.javanauta.cadastro_usuario.business.UsuarioService;
import com.javanauta.cadastro_usuario.dto.request.UsuarioRequestDTO;
import com.javanauta.cadastro_usuario.dto.request.UsuarioUpdateRequestDTO;
import com.javanauta.cadastro_usuario.dto.response.UsuarioResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> salvarUsuario(@Valid @RequestBody UsuarioRequestDTO usuarioRequest) {
        // Chama o serviço para salvar o usuário recebido no corpo da requisição
        // O serviço retorna o DTO de resposta com os dados do usuário criado
        UsuarioResponseDTO usuarioResponse = usuarioService.salvarUsuario(usuarioRequest);
        // Retorna uma resposta 200 OK com os dados do usuário criado
        return ResponseEntity.ok(usuarioResponse);
    }

    @GetMapping
    public ResponseEntity<UsuarioResponseDTO> buscarUsuarioPorEmail(@RequestParam String email) {
        // Chama o serviço para buscar o usuário pelo email passado como parâmetro na URL
        // O serviço retorna o DTO de resposta
        return ResponseEntity.ok(usuarioService.buscarUsuarioPorEmail(email));
    }

    @DeleteMapping
    public ResponseEntity<Void> deletarUsuarioPorEmail(@RequestParam String email) {
        usuarioService.deletarUsuarioPorEmail(email);
        return ResponseEntity.ok().build();
    }

    @PutMapping
    public ResponseEntity<UsuarioResponseDTO> atualizarUsuarioPorId(@RequestParam Integer id,
                                                                     @Valid @RequestBody UsuarioUpdateRequestDTO usuarioUpdateRequest) {
        // Chama o serviço para atualizar o usuário com o ID informado
        // O serviço retorna o DTO de resposta com os dados atualizados
        UsuarioResponseDTO usuarioResponse = usuarioService.atualizarUsuarioPorId(id, usuarioUpdateRequest);
        // Retorna uma resposta 200 OK com os dados do usuário atualizado
        return ResponseEntity.ok(usuarioResponse);
    }
}
