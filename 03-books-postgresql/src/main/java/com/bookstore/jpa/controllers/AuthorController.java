package com.bookstore.jpa.controllers;

import com.bookstore.jpa.models.AuthorModel;
import com.bookstore.jpa.repositories.AuthorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookstore/authors")
public class AuthorController {

    private final AuthorRepository authorRepository;

    public AuthorController(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @GetMapping
    public ResponseEntity<List<AuthorModel>> getAllAuthors() {
        return ResponseEntity.status(HttpStatus.OK).body(authorRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<AuthorModel> saveAuthor(@RequestBody AuthorModel author) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authorRepository.save(author));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAuthor(@PathVariable UUID id) {
        authorRepository.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK).body("Author deleted successfully.");
    }
}
