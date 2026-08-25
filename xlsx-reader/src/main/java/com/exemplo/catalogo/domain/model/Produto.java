package com.exemplo.catalogo.domain.model;

import java.math.BigDecimal;

public class Produto {

    private final String sku;
    private final String nome;
    private final BigDecimal preco;
    private final Integer estoque;
    private final String categoria;

    public Produto(String sku, String nome, BigDecimal preco, Integer estoque, String categoria) {
        this.sku = sku;
        this.nome = nome;
        this.preco = preco;
        this.estoque = estoque;
        this.categoria = categoria;
    }

    public String getSku() {
        return sku;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public Integer getEstoque() {
        return estoque;
    }

    public String getCategoria() {
        return categoria;
    }
}
