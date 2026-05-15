package com.exemplo.catalogo.adapter.out.persistence;

import com.exemplo.catalogo.domain.model.Produto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "produto")
public class ProdutoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sku;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private Integer estoque;

    @Column(nullable = false)
    private String categoria;

    protected ProdutoEntity() {
    }

    public ProdutoEntity(String sku, String nome, BigDecimal preco, Integer estoque, String categoria) {
        this.sku = sku;
        this.nome = nome;
        this.preco = preco;
        this.estoque = estoque;
        this.categoria = categoria;
    }

    public void atualizarComDadosDe(Produto produto) {
        this.nome = produto.getNome();
        this.preco = produto.getPreco();
        this.estoque = produto.getEstoque();
        this.categoria = produto.getCategoria();
    }

    public Produto paraDominio() {
        return new Produto(sku, nome, preco, estoque, categoria);
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }
}
