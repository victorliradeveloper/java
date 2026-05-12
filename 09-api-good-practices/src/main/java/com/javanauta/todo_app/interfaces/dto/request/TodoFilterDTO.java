package com.javanauta.todo_app.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TodoFilterDTO {

    @Schema(description = "Filter by title (partial match)", example = "groceries")
    private String title;

    @Schema(description = "Filter by completion status", example = "false")
    private Boolean completed;

    @Schema(description = "Filter todos due after this date (ISO 8601)", example = "2026-05-01T00:00:00")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime dueDateFrom;

    @Schema(description = "Filter todos due before this date (ISO 8601)", example = "2026-05-31T23:59:59")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime dueDateTo;
}
