package com.exemplo.catalogo.domain.port.in;

import com.exemplo.catalogo.domain.model.Importacao;

import java.io.IOException;
import java.io.InputStream;

public interface ImportarCatalogoUseCase {

    Importacao importar(InputStream planilha) throws IOException;
}
