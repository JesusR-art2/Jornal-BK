package com.jornal.requests;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RequestRatingRepository extends JpaRepository<RequestRating, UUID> {

    boolean existsByRequestIdAndDirection(UUID requestId, RequestRating.Direction direction);

    List<RequestRating> findByRequestIdOrderByCreatedAt(UUID requestId);
}
