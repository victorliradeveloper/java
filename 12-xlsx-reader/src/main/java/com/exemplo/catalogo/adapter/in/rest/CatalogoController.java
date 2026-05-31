package com.exemplo.catalogo.adapter.in.rest;

import com.exemplo.catalogo.adapter.in.rest.dto.ImportacaoResponse;
import com.exemplo.catalogo.domain.model.Importacao;
import com.exemplo.catalogo.domain.model.LinhaInvalida;
import com.exemplo.catalogo.domain.port.in.ImportarCatalogoUseCase;
import com.exemplo.catalogo.domain.port.out.ImportacaoRepository;
import com.exemplo.catalogo.domain.port.out.RelatorioErroEscritor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/catalogos")
public class CatalogoController {

    private final ImportarCatalogoUseCase importar;
    private final ImportacaoRepository importacaoRepository;
    private final RelatorioErroEscritor erroEscritor;

    public CatalogoController(ImportarCatalogoUseCase importar,
                              ImportacaoRepository importacaoRepository,
                              RelatorioErroEscritor erroEscritor) {
        this.importar = importar;
        this.importacaoRepository = importacaoRepository;
        this.erroEscritor = erroEscritor;
    }

    @PostMapping("/importacoes")
    public ResponseEntity<ImportacaoResponse> importar(
            @RequestParam("arquivo") MultipartFile arquivo) throws IOException {

        try (InputStream in = arquivo.getInputStream()) {
            Importacao resultado = importar.importar(in);
            return ResponseEntity.ok(ImportacaoResponse.from(resultado));
        }
    }

    @GetMapping("/importacoes/{id}/erros")
    public ResponseEntity<StreamingResponseBody> baixarErros(@PathVariable Long id) {
        List<LinhaInvalida> erros = importacaoRepository.buscarErrosPorId(id)
                .orElse(null);

        if (erros == null) {
            return ResponseEntity.notFound().build();
        }

        StreamingResponseBody body = out -> erroEscritor.escrever(erros, out);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=erros-" + id + ".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
