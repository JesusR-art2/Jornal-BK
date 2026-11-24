package com.jornal.requests;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Una fila por transición: origen, destino, instante UTC, actor y motivo. */
@Entity
@Table(name = "request_status_history")
@Getter
@Setter
@NoArgsConstructor
public class RequestStatusChange {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private RequestStatus toStatus;

    @Column(nullable = false)
    private Instant at;

    @Column(nullable = false)
    private String actor;

    private String reason;
}
