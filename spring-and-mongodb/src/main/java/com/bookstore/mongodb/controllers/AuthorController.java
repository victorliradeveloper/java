package com.bookstore.mongodb.controllers;

import com.bookstore.mongodb.documents.AuthorDocument;
import com.bookstore.mongodb.repositories.AuthorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bookstore/authors")
public class AuthorController {

    private final AuthorRepository authorRepository;

    public AuthorController(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @GetMapping
    public ResponseEntity<List<AuthorDocument>> getAllAuthors() {
        return ResponseEntity.status(HttpStatus.OK).body(authorRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<AuthorDocument> saveAuthor(@RequestBody AuthorDocument author) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authorRepository.save(author));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAuthor(@PathVariable String id) {
        authorRepository.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK).body("Author deleted successfully.");
    }
}
