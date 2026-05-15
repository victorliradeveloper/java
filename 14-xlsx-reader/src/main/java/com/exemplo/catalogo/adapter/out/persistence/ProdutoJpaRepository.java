package com.exemplo.catalogo.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProdutoJpaRepository extends JpaRepository<ProdutoEntity, Long> {

    Optional<ProdutoEntity> findBySku(String sku);
}
