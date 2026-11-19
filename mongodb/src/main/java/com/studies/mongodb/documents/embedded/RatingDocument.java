package com.studies.mongodb.documents.embedded;

import java.time.LocalDateTime;

public record RatingDocument(
        String userId,
        Integer score,
        String comment,
        LocalDateTime createdAt
) {}
