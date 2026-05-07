package com.bookstore.mongodb.documents;

// Classe simples sem @Document — a review é embutida dentro do BookDocument.
// Não possui ID próprio nem collection separada no banco.
public class ReviewDocument {

    private String comment;

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
