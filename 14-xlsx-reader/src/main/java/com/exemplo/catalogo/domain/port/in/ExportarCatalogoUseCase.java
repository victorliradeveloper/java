package com.exemplo.catalogo.domain.port.in;

import java.io.IOException;
import java.io.OutputStream;

public interface ExportarCatalogoUseCase {

    void exportar(OutputStream destino) throws IOException;
}
