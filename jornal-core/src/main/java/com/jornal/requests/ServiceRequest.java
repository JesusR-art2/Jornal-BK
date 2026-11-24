package com.jornal.requests;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pedido de un trabajo. El dinero va en unidad menor (D8) y la hora en UTC con la zona IANA del lugar (D9). */
@Entity
@Table(name = "service_requests")
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequest {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "client_role", nullable = false)
    private String clientRole;

    @Column(name = "region_code", nullable = false, length = 2)
    private String regionCode;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "category_slug", nullable = false)
    private String categorySlug;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private int units;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "money_scale", nullable = false)
    private int moneyScale;

    @Column(name = "subtotal_minor", nullable = false)
    private long subtotalMinor;

    @Column(name = "travel_fee_minor", nullable = false)
    private long travelFeeMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "tax_included", nullable = false)
    private boolean taxIncluded;

    @Column(name = "tip_minor", nullable = false)
    private long tipMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "commission_bps", nullable = false)
    private int commissionBps;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "payment_flow", nullable = false)
    private String paymentFlow;

    /** Sube cada vez que el pedido se recotiza; los eventos de pagos de intentos viejos se ignoran. */
    @Column(name = "payment_attempt", nullable = false)
    private int paymentAttempt = 1;

    @Column(name = "payment_captured", nullable = false)
    private boolean paymentCaptured;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "address_text", nullable = false)
    private String addressText;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Column(name = "worker_id")
    private UUID workerId;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    /** Secuencia de los mensajes emitidos por este pedido ({@code aggregateversion}). */
    @Column(name = "event_seq", nullable = false)
    private long eventSeq;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @ElementCollection
    @CollectionTable(name = "request_lines", joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "position")
    private List<RequestLine> lines = new ArrayList<>();

    public long nextEventSeq() {
        return ++eventSeq;
    }
}
