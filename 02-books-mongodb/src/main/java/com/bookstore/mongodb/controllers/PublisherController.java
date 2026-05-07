package com.bookstore.mongodb.controllers;

import com.bookstore.mongodb.documents.PublisherDocument;
import com.bookstore.mongodb.repositories.PublisherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bookstore/publishers")
public class PublisherController {

    private final PublisherRepository publisherRepository;

    public PublisherController(PublisherRepository publisherRepository) {
        this.publisherRepository = publisherRepository;
    }

    @GetMapping
    public ResponseEntity<List<PublisherDocument>> getAllPublishers() {
        return ResponseEntity.status(HttpStatus.OK).body(publisherRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<PublisherDocument> savePublisher(@RequestBody PublisherDocument publisher) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publisherRepository.save(publisher));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePublisher(@PathVariable String id) {
        publisherRepository.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK).body("Publisher deleted successfully.");
    }
}
