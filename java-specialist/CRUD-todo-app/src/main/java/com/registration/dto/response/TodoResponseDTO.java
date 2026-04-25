package com.registration.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TodoResponseDTO {

    private String id;
    private String title;
    private String description;
    private boolean completed;
    private LocalDateTime createdAt;
}
