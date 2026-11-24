package com.jornal.requests.services;

import com.jornal.requests.*;
import com.jornal.requests.enums.ErrorRequest;
import com.jornal.shared.ApiException;
import com.jornal.shared.IntegrationEvents;
import jornal.common.events.EventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Único lugar donde cambia el estado de un pedido: valida la transición, deja la fila de historial
 * y publica los mensajes en el Outbox (en la misma transacción).
 */
@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class RequestTransitions {

    private final RequestStatusChangeRepository history;
    private final IntegrationEvents events;
    private final Clock clock;

    public void move(ServiceRequest request, RequestStatus to, Actor actor, String reason) {
        RequestStatus from = request.getStatus();
        if (from == null || !from.canMoveTo(to)) {
            throw new ApiException(ErrorRequest.REQ006_INVALID_TRANSITION, from, to);
        }
        Instant now = clock.instant();
        request.setStatus(to);
        request.setUpdatedAt(now);
        record(request, from, to, actor, reason, now);
        statusChanged(request, from, reason);
        log.info("Request {} {} -> {} by {}{}", request.getId(), from, to, actor.label(), reason == null ? "" : " (" + reason + ")");
    }

    /** Primera fila del historial (creación). */
    public void created(ServiceRequest request, Actor actor) {
        record(request, null, request.getStatus(), actor, null, request.getCreatedAt());
        statusChanged(request, null, null);
        log.info("Request {} created in {} by {}", request.getId(), request.getStatus(), actor.label());
    }

    public void emit(ServiceRequest request, String type, Map<String, Object> data) {
        events.publish(type, request.getId().toString(), request.nextEventSeq(), data, request.getId().toString());
    }

    /** Datos de pago que necesitan pagos y facturación. */
    public Map<String, Object> paymentData(ServiceRequest r) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", r.getId().toString());
        data.put("clientId", r.getClientId().toString());
        data.put("clientRole", r.getClientRole());
        data.put("workerId", r.getWorkerId() == null ? null : r.getWorkerId().toString());
        data.put("attempt", r.getPaymentAttempt());
        data.put("paymentMethod", r.getPaymentMethod());
        data.put("paymentFlow", r.getPaymentFlow());
        data.put("currency", r.getCurrency());
        data.put("moneyScale", r.getMoneyScale());
        data.put("subtotalMinor", r.getSubtotalMinor());
        data.put("travelFeeMinor", r.getTravelFeeMinor());
        data.put("taxMinor", r.getTaxMinor());
        data.put("taxIncluded", r.isTaxIncluded());
        data.put("tipMinor", r.getTipMinor());
        data.put("totalMinor", r.getTotalMinor());
        data.put("commissionBps", r.getCommissionBps());
        data.put("timezone", r.getTimezone());
        data.put("lines", r.getLines().stream().map(l -> Map.<String, Object>of(
                "kind", l.kind(), "description", l.description(), "quantity", l.quantity(),
                "unitAmountMinor", l.unitAmountMinor(), "amountMinor", l.amountMinor(),
                "includedInTotal", l.includedInTotal())).toList());
        return data;
    }

    private void record(ServiceRequest request, RequestStatus from, RequestStatus to, Actor actor,
                        String reason, Instant at) {
        RequestStatusChange change = new RequestStatusChange();
        change.setId(UUID.randomUUID());
        change.setRequestId(request.getId());
        change.setFromStatus(from);
        change.setToStatus(to);
        change.setAt(at);
        change.setActor(actor.label());
        change.setReason(reason);
        history.save(change);
    }

    private void statusChanged(ServiceRequest r, RequestStatus from, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", r.getId().toString());
        data.put("clientId", r.getClientId().toString());
        data.put("workerId", r.getWorkerId() == null ? null : r.getWorkerId().toString());
        data.put("from", from == null ? null : from.name());
        data.put("to", r.getStatus().name());
        data.put("reason", reason);
        data.put("categoryName", r.getCategoryName());
        data.put("scheduledAt", r.getScheduledAt().toString());
        data.put("timezone", r.getTimezone());
        data.put("totalMinor", r.getTotalMinor());
        data.put("currency", r.getCurrency());
        emit(r, EventTypes.REQUEST_STATUS_CHANGED, data);
    }

    public List<RequestStatusChange> historyOf(UUID requestId) {
        return history.findByRequestIdOrderByAt(requestId);
    }
}
