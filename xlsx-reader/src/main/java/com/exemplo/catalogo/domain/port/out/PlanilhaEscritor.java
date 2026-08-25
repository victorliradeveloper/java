package com.exemplo.catalogo.domain.port.out;

import com.exemplo.catalogo.domain.model.Produto;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface PlanilhaEscritor {

    void escrever(List<Produto> produtos, OutputStream destino) throws IOException;
}
