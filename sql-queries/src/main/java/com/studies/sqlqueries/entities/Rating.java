package com.studies.sqlqueries.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;

// -------------------------------------------------------
// @Embeddable: each Rating is stored as a row in the
// "product_ratings" table (via @ElementCollection on Product).
//
// Table schema:
//   product_id | user_id | score | comment | rated_at
//
// MongoDB equivalent: an element of the embedded ratings array.
// -------------------------------------------------------
@Embeddable
public class Rating {

    @Column(name = "user_id")
    private Long userId;

    private Integer score;
    private String comment;

    @Column(name = "rated_at")
    private LocalDateTime createdAt;

    public Rating() {}

    public Rating(Long userId, Integer score, String comment, LocalDateTime createdAt) {
        this.userId    = userId;
        this.score     = score;
        this.comment   = comment;
        this.createdAt = createdAt;
    }

    public Long getUserId()         { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getScore()       { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getComment()      { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
