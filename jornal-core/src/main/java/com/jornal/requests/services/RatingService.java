package com.jornal.requests.services;

import com.jornal.requests.*;
import com.jornal.requests.dto.RatingRequest;
import com.jornal.requests.dto.RatingResponse;
import com.jornal.requests.enums.ErrorRequest;
import com.jornal.shared.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Calificaciones en ambos sentidos, solo sobre pedidos {@code COMPLETED}; una por pedido y sentido. */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RequestService requests;
    private final RequestRatingRepository ratings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public RatingResponse rate(Actor actor, UUID requestId, RatingRequest body) {
        ServiceRequest r = requests.getOrThrow(requestId);
        RequestRating.Direction direction;
        UUID ratee;
        if (actor.isClient() && r.getClientId().equals(actor.id())) {
            direction = RequestRating.Direction.CLIENT_TO_WORKER;
            ratee = r.getWorkerId();
        } else if (actor.isWorker() && actor.id().equals(r.getWorkerId())) {
            direction = RequestRating.Direction.WORKER_TO_CLIENT;
            ratee = r.getClientId();
        } else {
            throw new ApiException(ErrorRequest.REQ007_NOT_ALLOWED);
        }
        if (r.getStatus() != RequestStatus.COMPLETED || ratee == null) {
            throw new ApiException(ErrorRequest.REQ011_NOT_COMPLETED);
        }
        if (ratings.existsByRequestIdAndDirection(requestId, direction)) {
            throw new ApiException(ErrorRequest.REQ012_ALREADY_RATED);
        }
        RequestRating rating = new RequestRating();
        rating.setId(UUID.randomUUID());
        rating.setRequestId(requestId);
        rating.setDirection(direction);
        rating.setRaterId(actor.id());
        rating.setRateeId(ratee);
        rating.setScore(body.score());
        rating.setComment(body.comment() == null || body.comment().isBlank() ? null : body.comment().strip());
        rating.setCreatedAt(clock.instant());
        try {
            ratings.saveAndFlush(rating);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorRequest.REQ012_ALREADY_RATED);
        }
        if (direction == RequestRating.Direction.CLIENT_TO_WORKER) {
            events.publishEvent(new WorkerRated(ratee, requestId, body.score()));
        }
        return toResponse(rating);
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> of(Actor actor, UUID requestId) {
        ServiceRequest r = requests.getOrThrow(requestId);
        boolean participant = actor.id() != null
                && (actor.id().equals(r.getClientId()) || actor.id().equals(r.getWorkerId()));
        if (!participant && !actor.isAdmin()) {
            throw new ApiException(ErrorRequest.REQ007_NOT_ALLOWED);
        }
        return ratings.findByRequestIdOrderByCreatedAt(requestId).stream().map(RatingService::toResponse).toList();
    }

    private static RatingResponse toResponse(RequestRating r) {
        return new RatingResponse(r.getId(), r.getRequestId(), r.getDirection().name(), r.getRaterId(),
                r.getRateeId(), r.getScore(), r.getComment(), r.getCreatedAt());
    }
}
