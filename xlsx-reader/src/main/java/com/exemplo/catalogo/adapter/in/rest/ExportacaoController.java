package com.exemplo.catalogo.adapter.in.rest;

import com.exemplo.catalogo.domain.port.in.ExportarCatalogoUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/catalogos")
public class ExportacaoController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportarCatalogoUseCase exportar;

    public ExportacaoController(ExportarCatalogoUseCase exportar) {
        this.exportar = exportar;
    }

    @GetMapping("/exportacoes/produtos.xlsx")
    public ResponseEntity<StreamingResponseBody> exportar() {
        StreamingResponseBody body = exportar::exportar;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=produtos.xlsx")
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(body);
    }
}
