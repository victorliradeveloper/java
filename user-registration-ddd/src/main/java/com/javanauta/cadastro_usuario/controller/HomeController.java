package com.javanauta.cadastro_usuario.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para endpoints da home/página inicial
 */
@RestController
@RequestMapping("/")
public class HomeController {

    /**
     * Endpoint de boas-vindas
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> home() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Bem-vindo ao sistema de cadastro de usuários");
        response.put("status", "OK");
        return ResponseEntity.ok(response);
    }
}

