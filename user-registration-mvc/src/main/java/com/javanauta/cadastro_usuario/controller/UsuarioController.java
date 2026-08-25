package com.javanauta.cadastro_usuario.controller;

import com.javanauta.cadastro_usuario.dto.UsuarioRequestDTO;
import com.javanauta.cadastro_usuario.dto.UsuarioResponseDTO;
import com.javanauta.cadastro_usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> salvarUsuario(@RequestBody UsuarioRequestDTO usuarioRequest) {
        return ResponseEntity.ok(usuarioService.salvarUsuario(usuarioRequest));
    }

    @GetMapping
    public ResponseEntity<UsuarioResponseDTO> buscarUsuarioPorEmail(@RequestParam String email) {
        return ResponseEntity.ok(usuarioService.buscarUsuarioPorEmail(email));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> atualizarUsuario(@PathVariable Integer id,
                                                               @RequestBody UsuarioRequestDTO usuarioRequest) {
        return ResponseEntity.ok(usuarioService.atualizarUsuarioPorId(id, usuarioRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(@PathVariable Integer id) {
        usuarioService.deletarUsuarioPorId(id);
        return ResponseEntity.noContent().build();
    }
}
