package com.jornal.requests;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RequestStatusChangeRepository extends JpaRepository<RequestStatusChange, UUID> {

    List<RequestStatusChange> findByRequestIdOrderByAt(UUID requestId);
}
