package com.exemplo.catalogo.adapter.out.csv;

import com.exemplo.catalogo.domain.model.LinhaInvalida;
import com.exemplo.catalogo.domain.port.out.RelatorioErroEscritor;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class CsvErroEscritor implements RelatorioErroEscritor {

    // BOM UTF-8 — sem isso, Excel abre o CSV com acentos quebrados.
    private static final String BOM = "﻿";

    @Override
    public void escrever(List<LinhaInvalida> erros, OutputStream destino) throws IOException {
        try (Writer w = new OutputStreamWriter(destino, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(w);
             PrintWriter pw = new PrintWriter(bw)) {

            pw.print(BOM);
            pw.println("linha;motivo;dados");

            for (LinhaInvalida erro : erros) {
                pw.printf("%d;%s;%s%n",
                        erro.getNumeroLinha(),
                        escapar(erro.getMotivo()),
                        escapar(erro.getDadosOriginais()));
            }
        }
    }

    private String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace(";", ",").replace("\n", " ").replace("\r", " ");
    }
}
