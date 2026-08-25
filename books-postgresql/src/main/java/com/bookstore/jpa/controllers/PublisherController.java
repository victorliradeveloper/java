package com.bookstore.jpa.controllers;

import com.bookstore.jpa.models.PublisherModel;
import com.bookstore.jpa.repositories.PublisherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookstore/publishers")
public class PublisherController {

    private final PublisherRepository publisherRepository;

    public PublisherController(PublisherRepository publisherRepository) {
        this.publisherRepository = publisherRepository;
    }

    @GetMapping
    public ResponseEntity<List<PublisherModel>> getAllPublishers() {
        return ResponseEntity.status(HttpStatus.OK).body(publisherRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<PublisherModel> savePublisher(@RequestBody PublisherModel publisher) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publisherRepository.save(publisher));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePublisher(@PathVariable UUID id) {
        publisherRepository.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK).body("Publisher deleted successfully.");
    }
}
