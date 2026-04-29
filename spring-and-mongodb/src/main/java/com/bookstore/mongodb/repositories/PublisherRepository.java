package com.bookstore.mongodb.repositories;

import com.bookstore.mongodb.documents.PublisherDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PublisherRepository extends MongoRepository<PublisherDocument, String> {
}
