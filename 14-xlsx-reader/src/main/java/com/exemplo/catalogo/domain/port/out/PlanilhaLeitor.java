package com.exemplo.catalogo.domain.port.out;

import com.exemplo.catalogo.domain.model.LinhaPlanilha;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

public interface PlanilhaLeitor {

    Stream<LinhaPlanilha> ler(InputStream entrada) throws IOException;
}
