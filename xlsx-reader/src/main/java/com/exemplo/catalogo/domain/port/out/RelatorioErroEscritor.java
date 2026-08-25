package com.exemplo.catalogo.domain.port.out;

import com.exemplo.catalogo.domain.model.LinhaInvalida;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface RelatorioErroEscritor {

    void escrever(List<LinhaInvalida> erros, OutputStream destino) throws IOException;
}
