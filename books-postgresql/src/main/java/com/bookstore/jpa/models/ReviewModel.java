package com.bookstore.jpa.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "TB_REVIEW")
@Check(constraints = "rating BETWEEN 0 AND 5")
@Getter
@Setter
public class ReviewModel implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String comment;

    @Column
    private Integer rating;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @OneToOne
    @JoinColumn(name = "book_id")
    private BookModel book;
}
