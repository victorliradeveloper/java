package com.exemplo.catalogo.application;

import com.exemplo.catalogo.domain.model.Importacao;
import com.exemplo.catalogo.domain.model.LinhaInvalida;
import com.exemplo.catalogo.domain.model.LinhaPlanilha;
import com.exemplo.catalogo.domain.model.Produto;
import com.exemplo.catalogo.domain.model.ValidacaoException;
import com.exemplo.catalogo.domain.port.in.ImportarCatalogoUseCase;
import com.exemplo.catalogo.domain.port.out.ImportacaoRepository;
import com.exemplo.catalogo.domain.port.out.PlanilhaLeitor;
import com.exemplo.catalogo.domain.port.out.ProdutoRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class ImportarCatalogoService implements ImportarCatalogoUseCase {

    private final PlanilhaLeitor leitor;
    private final ProdutoRepository produtoRepository;
    private final ImportacaoRepository importacaoRepository;

    public ImportarCatalogoService(PlanilhaLeitor leitor,
                                   ProdutoRepository produtoRepository,
                                   ImportacaoRepository importacaoRepository) {
        this.leitor = leitor;
        this.produtoRepository = produtoRepository;
        this.importacaoRepository = importacaoRepository;
    }

    @Override
    public Importacao importar(InputStream planilha) throws IOException {
        LocalDateTime inicio = LocalDateTime.now();
        List<LinhaInvalida> erros = new ArrayList<>();
        List<Produto> validos = new ArrayList<>();

        try (Stream<LinhaPlanilha> linhas = leitor.ler(planilha)) {
            linhas.forEach(linha -> {
                try {
                    validos.add(linha.paraProduto());
                } catch (ValidacaoException e) {
                    erros.add(new LinhaInvalida(linha.numero(), e.getMessage(), linha.bruto()));
                }
            });
        }

        produtoRepository.salvarTodos(validos);

        Importacao resultado = new Importacao(
                null,
                inicio,
                LocalDateTime.now(),
                validos.size() + erros.size(),
                erros);
        return importacaoRepository.salvar(resultado);
    }
}
