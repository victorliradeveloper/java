package com.javanauta.cadastro_usuario.controller;

import com.javanauta.cadastro_usuario.dto.UsuarioRequestDTO;
import com.javanauta.cadastro_usuario.dto.UsuarioResponseDTO;
import com.javanauta.cadastro_usuario.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST - Camada de apresentação
 * Responsável por receber requisições HTTP e retornar respostas
 * Usa DTOs para desacoplar a camada de apresentação da camada de domínio
 */
@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Endpoint para salvar um novo usuário (HTTP POST)
     */
    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> salvarUsuario(@RequestBody UsuarioRequestDTO usuarioRequest) {
        UsuarioResponseDTO usuarioSalvo = userService.salvarUsuario(usuarioRequest);
        return ResponseEntity.ok(usuarioSalvo);
    }

    /**
     * Endpoint para buscar um usuário pelo email (HTTP GET)
     */
    @GetMapping
    public ResponseEntity<UsuarioResponseDTO> buscarUsuarioPorEmail(@RequestParam String email) {
        UsuarioResponseDTO usuario = userService.buscarUsuarioPorEmail(email);
        return ResponseEntity.ok(usuario);
    }

    /**
     * Endpoint para deletar um usuário pelo email (HTTP DELETE)
     */
    @DeleteMapping
    public ResponseEntity<Void> deletarUsuarioPorEmail(@RequestParam String email) {
        userService.deletarUsuarioPorEmail(email);
        return ResponseEntity.ok().build();
    }

    /**
     * Endpoint para atualizar os dados de um usuário existente (HTTP PUT)
     */
    @PutMapping
    public ResponseEntity<UsuarioResponseDTO> atualizarUsuarioPorId(@RequestParam Integer id,
                                                                     @RequestBody UsuarioRequestDTO usuarioRequest) {
        UsuarioResponseDTO usuarioAtualizado = userService.atualizarUsuarioPorId(id, usuarioRequest);
        return ResponseEntity.ok(usuarioAtualizado);
    }
}

