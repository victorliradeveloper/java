package com.bookstore.mongodb.repositories;

import com.bookstore.mongodb.documents.AuthorDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuthorRepository extends MongoRepository<AuthorDocument, String> {
}
