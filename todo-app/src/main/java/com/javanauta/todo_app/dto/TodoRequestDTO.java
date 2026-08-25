package com.javanauta.todo_app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TodoRequestDTO {

    @NotBlank(message = "Título é obrigatório")
    private String titulo;

    private String descricao;

    private LocalDateTime dataLimite;
}
