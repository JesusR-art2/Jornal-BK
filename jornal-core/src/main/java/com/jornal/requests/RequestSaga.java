package com.jornal.requests;

import com.jornal.requests.services.Actor;
import com.jornal.requests.services.RequestService;
import com.jornal.requests.services.RequestTransitions;
import jornal.common.events.CloudEvent;
import jornal.common.events.EventTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestador de la saga del pedido. Recibe las respuestas de los subsistemas (por la cola {@code core.<región>})
 * y mueve el pedido. Cada paso es una transacción local con sus mensajes en el Outbox:
 *
 * <pre>
 * payment.authorized        PENDING_PAYMENT  → SEARCHING_WORKER  + assignment-requested
 * payment.failed            PENDING_PAYMENT  → CANCELLED
 * dispatch.worker-assigned  SEARCHING_WORKER → ASSIGNED
 * dispatch.assignment-failed SEARCHING_WORKER → CANCELLED + cancelled (pagos anula o reembolsa)
 * payment.captured + billing.receipt-issued  PENDING_CONFIRMATION → COMPLETED
 * </pre>
 *
 * Los mensajes que no aplican al estado actual (tardíos, de un intento de pago viejo o duplicados) se ignoran.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestSaga {

    private final ServiceRequestRepository repository;
    private final RequestService requests;
    private final RequestTransitions transitions;

    @Transactional
    public void handle(CloudEvent event) {
        Optional<ServiceRequest> found = requestOf(event);
        if (found.isEmpty()) {
            log.warn("Saga event {} for unknown request {}", event.type(), event.subject());
            return;
        }
        ServiceRequest r = found.get();
        Actor actor = Actor.service(serviceOf(event));
        Map<String, Object> data = event.data() == null ? Map.of() : event.data();

        switch (event.type()) {
            case EventTypes.PAYMENT_AUTHORIZED -> {
                if (currentAttempt(r, data) && r.getStatus() == RequestStatus.PENDING_PAYMENT) {
                    transitions.move(r, RequestStatus.SEARCHING_WORKER, actor, "PAYMENT_AUTHORIZED");
                    transitions.emit(r, EventTypes.REQUEST_ASSIGNMENT_REQUESTED, assignmentData(r));
                } else {
                    ignore(event, r);
                }
            }
            case EventTypes.PAYMENT_FAILED -> {
                if (currentAttempt(r, data) && r.getStatus() == RequestStatus.PENDING_PAYMENT) {
                    String reason = "PAYMENT_FAILED" + suffix(data.get("reason"));
                    transitions.move(r, RequestStatus.CANCELLED, actor, reason);
                    r.setCancelReason(reason);
                } else {
                    ignore(event, r);
                }
            }
            case EventTypes.DISPATCH_WORKER_ASSIGNED -> {
                UUID workerId = UUID.fromString(String.valueOf(data.get("workerId")));
                if (r.getStatus() == RequestStatus.SEARCHING_WORKER) {
                    r.setWorkerId(workerId);
                    transitions.move(r, RequestStatus.ASSIGNED, actor, "WORKER_ASSIGNED");
                } else {
                    // Llegó tarde (pedido cancelado o ya asignado): asignación libera la reserva.
                    log.info("Late assignment of {} to request {} in {}", workerId, r.getId(), r.getStatus());
                    Map<String, Object> release = new HashMap<>(transitions.paymentData(r));
                    release.put("fromStatus", r.getStatus().name());
                    release.put("reason", "ASSIGNMENT_REJECTED");
                    release.put("paymentAuthorized", false);
                    release.put("workerId", workerId.toString());
                    release.put("releaseOnly", true);
                    transitions.emit(r, EventTypes.REQUEST_CANCELLED, release);
                }
            }
            case EventTypes.DISPATCH_ASSIGNMENT_FAILED -> {
                if (r.getStatus() == RequestStatus.SEARCHING_WORKER) {
                    requests.cancelWithCompensation(r, actor, "NO_WORKER_AVAILABLE");
                } else {
                    ignore(event, r);
                }
            }
            case EventTypes.PAYMENT_CAPTURED -> {
                r.setPaymentCaptured(true);
                completeIfDone(r, actor);
            }
            case EventTypes.BILLING_RECEIPT_ISSUED -> {
                Object number = data.get("receiptNumber");
                r.setReceiptNumber(number == null ? null : number.toString());
                completeIfDone(r, actor);
            }
            default -> log.debug("Saga ignores {}", event.type());
        }
    }

    private void completeIfDone(ServiceRequest r, Actor actor) {
        if (r.isPaymentCaptured() && r.getReceiptNumber() != null
                && r.getStatus() == RequestStatus.PENDING_CONFIRMATION) {
            transitions.move(r, RequestStatus.COMPLETED, actor, "PAID_AND_RECEIPT_ISSUED");
        }
    }

    private Optional<ServiceRequest> requestOf(CloudEvent event) {
        try {
            return repository.findById(UUID.fromString(event.subject()));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static boolean currentAttempt(ServiceRequest r, Map<String, Object> data) {
        Object attempt = data.get("attempt");
        return attempt == null || Integer.parseInt(attempt.toString()) == r.getPaymentAttempt();
    }

    private static Map<String, Object> assignmentData(ServiceRequest r) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", r.getId().toString());
        data.put("clientId", r.getClientId().toString());
        data.put("categoryId", r.getCategoryId().toString());
        data.put("categorySlug", r.getCategorySlug());
        data.put("units", r.getUnits());
        data.put("latitude", r.getLatitude());
        data.put("longitude", r.getLongitude());
        data.put("scheduledAt", r.getScheduledAt().toString());
        data.put("timezone", r.getTimezone());
        return data;
    }

    private static String serviceOf(CloudEvent event) {
        String type = event.type();
        if (type.startsWith("jornal.payment.")) return "payments";
        if (type.startsWith("jornal.dispatch.")) return "dispatch";
        if (type.startsWith("jornal.billing.")) return "billing";
        return event.source();
    }

    private static String suffix(Object reason) {
        return reason == null ? "" : ":" + reason;
    }

    private static void ignore(CloudEvent event, ServiceRequest r) {
        log.info("Saga ignores {} for request {} in {} (attempt {})", event.type(), r.getId(), r.getStatus(),
                r.getPaymentAttempt());
    }
}
