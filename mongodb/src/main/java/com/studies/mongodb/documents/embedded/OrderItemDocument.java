package com.studies.mongodb.documents.embedded;

public record OrderItemDocument(
        String productId,
        String productName,
        Double unitPrice,
        Integer quantity
) {}
