package com.exemplo.datastructure.product;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;

public record Product(
        Long id,
        String name,
        BigDecimal price,
        String category,
        int stock
) {

    @JsonIgnore
    public boolean isOutOfStock() {
        return stock == 0;
    }

    public boolean hasStock(){
        return stock > 0;
    }
}