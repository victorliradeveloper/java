package com.exemplo.catalogo.application;

import com.exemplo.catalogo.domain.model.Produto;
import com.exemplo.catalogo.domain.port.in.ExportarCatalogoUseCase;
import com.exemplo.catalogo.domain.port.out.PlanilhaEscritor;
import com.exemplo.catalogo.domain.port.out.ProdutoRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

@Service
public class ExportarCatalogoService implements ExportarCatalogoUseCase {

    private final ProdutoRepository produtoRepository;
    private final PlanilhaEscritor escritor;

    public ExportarCatalogoService(ProdutoRepository produtoRepository,
                                   PlanilhaEscritor escritor) {
        this.produtoRepository = produtoRepository;
        this.escritor = escritor;
    }

    @Override
    public void exportar(OutputStream destino) throws IOException {
        List<Produto> produtos = produtoRepository.listarTodos();
        escritor.escrever(produtos, destino);
    }
}
