package com.exemplo.catalogo.adapter.out.persistence;

import com.exemplo.catalogo.domain.model.Produto;
import com.exemplo.catalogo.domain.port.out.ProdutoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ProdutoRepositoryAdapter implements ProdutoRepository {

    private final ProdutoJpaRepository jpa;

    public ProdutoRepositoryAdapter(ProdutoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void salvarTodos(List<Produto> produtos) {
        for (Produto produto : produtos) {
            ProdutoEntity entity = jpa.findBySku(produto.getSku())
                    .orElseGet(() -> new ProdutoEntity(
                            produto.getSku(),
                            produto.getNome(),
                            produto.getPreco(),
                            produto.getEstoque(),
                            produto.getCategoria()));
            entity.atualizarComDadosDe(produto);
            jpa.save(entity);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Produto> listarTodos() {
        return jpa.findAll().stream()
                .map(ProdutoEntity::paraDominio)
                .toList();
    }
}
