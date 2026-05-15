package com.exemplo.catalogo.domain.port.out;

import com.exemplo.catalogo.domain.model.Produto;

import java.util.List;

public interface ProdutoRepository {

    void salvarTodos(List<Produto> produtos);

    List<Produto> listarTodos();
}
