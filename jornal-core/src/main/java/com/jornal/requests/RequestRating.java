package com.jornal.requests;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Calificación de un pedido terminado: el cliente califica al trabajador y el trabajador al cliente. */
@Entity
@Table(name = "request_ratings")
@Getter
@Setter
@NoArgsConstructor
public class RequestRating {

    public enum Direction { CLIENT_TO_WORKER, WORKER_TO_CLIENT }

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(name = "rater_id", nullable = false)
    private UUID raterId;

    @Column(name = "ratee_id", nullable = false)
    private UUID rateeId;

    @Column(nullable = false)
    private int score;

    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
