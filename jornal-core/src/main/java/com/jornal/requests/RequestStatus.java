package com.jornal.requests;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados del pedido. Las transiciones a CANCELLED son compensaciones: antes de cancelar,
 * pagos anula o reembolsa (evento {@code jornal.request.cancelled.v1}).
 */
public enum RequestStatus {
    DRAFT,
    PENDING_PAYMENT,
    SEARCHING_WORKER,
    ASSIGNED,
    EN_ROUTE,
    IN_PROGRESS,
    PENDING_CONFIRMATION,
    COMPLETED,
    CANCELLED,
    FAILED,
    EXPIRED;

    private static final Map<RequestStatus, Set<RequestStatus>> NEXT = new EnumMap<>(RequestStatus.class);

    static {
        NEXT.put(DRAFT, EnumSet.of(PENDING_PAYMENT, CANCELLED));
        NEXT.put(PENDING_PAYMENT, EnumSet.of(SEARCHING_WORKER, CANCELLED, FAILED));
        NEXT.put(SEARCHING_WORKER, EnumSet.of(ASSIGNED, PENDING_PAYMENT, CANCELLED, EXPIRED, FAILED));
        NEXT.put(ASSIGNED, EnumSet.of(EN_ROUTE, CANCELLED));
        NEXT.put(EN_ROUTE, EnumSet.of(IN_PROGRESS, CANCELLED));
        NEXT.put(IN_PROGRESS, EnumSet.of(PENDING_CONFIRMATION, CANCELLED));
        NEXT.put(PENDING_CONFIRMATION, EnumSet.of(COMPLETED, FAILED));
        for (RequestStatus terminal : EnumSet.of(COMPLETED, CANCELLED, FAILED, EXPIRED)) {
            NEXT.put(terminal, EnumSet.noneOf(RequestStatus.class));
        }
    }

    public boolean canMoveTo(RequestStatus target) {
        return NEXT.get(this).contains(target);
    }

    public boolean isTerminal() {
        return NEXT.get(this).isEmpty();
    }

    /** Estados en los que el cliente todavía puede cancelar sin intervención del administrador. */
    public boolean clientCanCancel() {
        return this == PENDING_PAYMENT || this == SEARCHING_WORKER || this == ASSIGNED;
    }
}
