package com.exemplo.catalogo.domain.model;

import java.math.BigDecimal;

public record LinhaPlanilha(int numero,
                            String sku,
                            String nome,
                            BigDecimal preco,
                            Integer estoque,
                            String categoria) {

    public Produto paraProduto() {
        if (sku == null || sku.isBlank()) {
            throw new ValidacaoException("SKU obrigatorio");
        }
        if (nome == null || nome.isBlank()) {
            throw new ValidacaoException("Nome obrigatorio");
        }
        if (preco == null) {
            throw new ValidacaoException("Preco obrigatorio");
        }
        if (preco.signum() < 0) {
            throw new ValidacaoException("Preco negativo");
        }
        if (estoque == null) {
            throw new ValidacaoException("Estoque obrigatorio");
        }
        if (estoque < 0) {
            throw new ValidacaoException("Estoque negativo");
        }
        if (categoria == null || categoria.isBlank()) {
            throw new ValidacaoException("Categoria obrigatoria");
        }
        return new Produto(sku, nome, preco, estoque, categoria);
    }

    public String bruto() {
        return String.format("%s | %s | %s | %s | %s",
                sku, nome, preco, estoque, categoria);
    }
}
