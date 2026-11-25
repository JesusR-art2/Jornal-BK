package com.jornal.vacancies;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Postulación de un trabajador a una vacante. Al finalizarla queda en el formato de pago ({@code payout*}). */
@Entity
@Table(name = "vacancy_applications")
@Getter
@Setter
@NoArgsConstructor
public class VacancyApplication {

    @Id
    private UUID id;

    @Column(name = "vacancy_id", nullable = false)
    private UUID vacancyId;

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    private String message;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "selected_at")
    private Instant selectedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason")
    private CloseReason closeReason;

    private String justification;

    @Column(name = "payout_minor")
    private Long payoutMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_status")
    private PayoutStatus payoutStatus;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_mode")
    private PayoutMode payoutMode;

    /** Lo que realmente se pagó (puede ser menor a {@code payoutMinor} con justificación). */
    @Column(name = "paid_amount_minor")
    private Long paidAmountMinor;

    @Column(name = "payment_note")
    private String paymentNote;

    @Column(name = "payout_ref")
    private String payoutRef;

    @Column(name = "payout_failure")
    private String payoutFailure;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;
}
