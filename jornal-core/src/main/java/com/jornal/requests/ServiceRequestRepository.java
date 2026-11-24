package com.jornal.requests;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    Optional<ServiceRequest> findByClientIdAndIdempotencyKey(UUID clientId, String idempotencyKey);

    Page<ServiceRequest> findByClientId(UUID clientId, Pageable pageable);

    Page<ServiceRequest> findByClientIdAndStatus(UUID clientId, RequestStatus status, Pageable pageable);

    Page<ServiceRequest> findByWorkerId(UUID workerId, Pageable pageable);

    Page<ServiceRequest> findByWorkerIdAndStatus(UUID workerId, RequestStatus status, Pageable pageable);

    Page<ServiceRequest> findByStatus(RequestStatus status, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select r.status, count(r) from ServiceRequest r group by r.status")
    java.util.List<Object[]> countGroupedByStatus();

    long countByCreatedAtGreaterThanEqual(java.time.Instant since);

    long countByStatusAndUpdatedAtGreaterThanEqual(RequestStatus status, java.time.Instant since);

    List<ServiceRequest> findTop100ByStatusAndUpdatedAtBefore(RequestStatus status, Instant before);

    List<ServiceRequest> findTop100ByStatusAndScheduledAtBefore(RequestStatus status, Instant before);

    List<ServiceRequest> findTop100ByStatusAndConfirmedAtIsNullAndFinishedAtBefore(RequestStatus status, Instant before);

    long countByStatusIn(Collection<RequestStatus> statuses);
}
