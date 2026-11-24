package com.jornal.requests.dto;

import java.time.Instant;
import java.util.UUID;

public record RatingResponse(UUID id, UUID requestId, String direction, UUID raterId, UUID rateeId, int score,
                             String comment, Instant createdAt) {
}
