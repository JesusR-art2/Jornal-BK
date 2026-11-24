package com.jornal.requests.services;

import com.jornal.catalog.CatalogApi;
import com.jornal.catalog.CategoryPriceSummary;
import com.jornal.region.PaymentMethod;
import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileView;
import com.jornal.region.RegionProfileView;
import com.jornal.requests.*;
import com.jornal.requests.dto.*;
import com.jornal.requests.enums.ErrorRequest;
import com.jornal.shared.ApiException;
import com.jornal.shared.PageResponse;
import com.jornal.users.UserApi;
import jornal.common.events.EventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final ServiceRequestRepository repository;
    private final PricingService pricing;
    private final RequestTransitions transitions;
    private final RegionApi regionApi;
    private final CatalogApi catalogApi;
    private final UserApi userApi;
    private final Clock clock;

    public record Created(RequestResponse request, boolean isNew) {
    }

    // ------------------------------------------------------------------ cliente: carrito y pedido

    @Transactional(readOnly = true)
    public QuoteResponse quote(Actor actor, RequestDraft draft) {
        RegionProfileView profile = regionApi.current();
        CategoryPriceSummary price = priceOf(draft.categoryId());
        resolveSchedule(profile, draft.scheduledLocal(), draft.timezone());
        requirePaymentMethod(profile, draft.paymentMethod());
        regionApi.validateAddress(profile.code(), draft.address());
        PricingService.Quote q = pricing.quote(profile, price, draft.units(), draft.tipMinor(), draft.tipPercent());
        return toQuote(profile, price, draft.units(), q);
    }

    /**
     * Crea el pedido en PENDING_PAYMENT y deja {@code jornal.request.created.v1} en el Outbox, en la misma
     * transacción. Repetir la misma {@code Idempotency-Key} devuelve el pedido ya creado.
     */
    @Transactional
    public Created create(Actor actor, String idempotencyKey, RequestDraft draft) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ApiException(ErrorRequest.REQ008_IDEMPOTENCY_KEY_REQUIRED);
        }
        var existing = repository.findByClientIdAndIdempotencyKey(actor.id(), idempotencyKey);
        if (existing.isPresent()) {
            return new Created(RequestResponse.of(existing.get()), false);
        }
        userApi.findSummaryById(actor.id())
                .orElseThrow(() -> new ApiException(ErrorRequest.REQ010_CLIENT_NOT_ACTIVE));

        RegionProfileView profile = regionApi.current();
        CategoryPriceSummary price = priceOf(draft.categoryId());
        Schedule schedule = resolveSchedule(profile, draft.scheduledLocal(), draft.timezone());
        PaymentMethod method = requirePaymentMethod(profile, draft.paymentMethod());
        Map<String, String> address = regionApi.validateAddress(profile.code(), draft.address());
        PricingService.Quote q = pricing.quote(profile, price, draft.units(), draft.tipMinor(), draft.tipPercent());

        Instant now = clock.instant();
        ServiceRequest r = new ServiceRequest();
        r.setId(UUID.randomUUID());
        r.setClientId(actor.id());
        r.setClientRole(actor.role());
        r.setRegionCode(profile.code());
        r.setCategoryId(price.categoryId());
        r.setCategorySlug(price.slug());
        r.setCategoryName(price.name());
        r.setUnit(price.unit());
        r.setUnits(draft.units());
        r.setUnitPriceMinor(price.amountMinor());
        r.setCommissionBps(profile.commissionBps());
        r.setPaymentMethod(method.code());
        r.setPaymentFlow(method.flow().name());
        r.setScheduledAt(schedule.at());
        r.setTimezone(schedule.zone());
        r.setAddressText(regionApi.formatAddress(profile.code(), address));
        r.setLatitude(draft.latitude());
        r.setLongitude(draft.longitude());
        r.setNotes(draft.notes());
        r.setIdempotencyKey(idempotencyKey);
        r.setStatus(RequestStatus.PENDING_PAYMENT);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        applyQuote(r, q);
        repository.save(r);

        transitions.created(r, actor);
        transitions.emit(r, EventTypes.REQUEST_CREATED, transitions.paymentData(r));
        return new Created(RequestResponse.of(r), true);
    }

    /** Cambios antes de la asignación: recotiza, sube el intento de pago y vuelve a PENDING_PAYMENT. */
    @Transactional
    public RequestResponse reprice(Actor actor, UUID id, RepriceRequest change) {
        ServiceRequest r = getOrThrow(id);
        requireOwner(actor, r);
        if (r.getStatus() != RequestStatus.PENDING_PAYMENT && r.getStatus() != RequestStatus.SEARCHING_WORKER) {
            throw new ApiException(ErrorRequest.REQ006_INVALID_TRANSITION, r.getStatus(), RequestStatus.PENDING_PAYMENT);
        }
        RegionProfileView profile = regionApi.current();
        CategoryPriceSummary price = priceOf(r.getCategoryId());
        int units = change.units() != null ? change.units() : r.getUnits();
        if (change.scheduledLocal() != null || change.timezone() != null) {
            LocalDateTime local = change.scheduledLocal() != null ? change.scheduledLocal()
                    : LocalDateTime.ofInstant(r.getScheduledAt(), ZoneId.of(r.getTimezone()));
            Schedule schedule = resolveSchedule(profile, local,
                    change.timezone() != null ? change.timezone() : r.getTimezone());
            r.setScheduledAt(schedule.at());
            r.setTimezone(schedule.zone());
        }
        Long tipMinor = change.tipMinor() != null || change.tipPercent() != null ? change.tipMinor() : r.getTipMinor();
        PricingService.Quote q = pricing.quote(profile, price, units, tipMinor, change.tipPercent());
        r.setUnits(units);
        r.setUnitPriceMinor(price.amountMinor());
        if (change.notes() != null) r.setNotes(change.notes());
        applyQuote(r, q);
        r.setPaymentAttempt(r.getPaymentAttempt() + 1);

        if (r.getStatus() == RequestStatus.SEARCHING_WORKER) {
            transitions.move(r, RequestStatus.PENDING_PAYMENT, actor, "REPRICED");
        } else {
            r.setUpdatedAt(clock.instant());
        }
        transitions.emit(r, EventTypes.REQUEST_REPRICED, transitions.paymentData(r));
        return RequestResponse.of(r);
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> mine(Actor actor, RequestStatus status, Pageable pageable) {
        Page<ServiceRequest> page;
        if (actor.isWorker()) {
            page = status == null ? repository.findByWorkerId(actor.id(), pageable)
                    : repository.findByWorkerIdAndStatus(actor.id(), status, pageable);
        } else {
            page = status == null ? repository.findByClientId(actor.id(), pageable)
                    : repository.findByClientIdAndStatus(actor.id(), status, pageable);
        }
        return PageResponse.from(page.map(RequestResponse::of));
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> all(RequestStatus status, Pageable pageable) {
        Page<ServiceRequest> page = status == null ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
        return PageResponse.from(page.map(RequestResponse::of));
    }

    @Transactional(readOnly = true)
    public RequestResponse get(Actor actor, UUID id) {
        ServiceRequest r = getOrThrow(id);
        requireParticipant(actor, r);
        return RequestResponse.of(r);
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> history(Actor actor, UUID id) {
        requireParticipant(actor, getOrThrow(id));
        return transitions.historyOf(id).stream()
                .map(h -> new HistoryResponse(h.getFromStatus(), h.getToStatus(), h.getAt(), h.getActor(), h.getReason()))
                .toList();
    }

    // ------------------------------------------------------------------ cambios de estado

    /**
     * Cancela con compensación: pagos anula o reembolsa y asignación libera la reserva al recibir
     * {@code jornal.request.cancelled.v1}. El cliente cancela hasta ASSIGNED; el administrador, en cualquier
     * estado previo a la confirmación (incidencias).
     */
    @Transactional
    public RequestResponse cancel(Actor actor, UUID id, String reason) {
        ServiceRequest r = getOrThrow(id);
        if (actor.isAdmin()) {
            // el administrador puede cancelar ante una incidencia
        } else {
            requireOwner(actor, r);
            if (!r.getStatus().clientCanCancel()) {
                throw new ApiException(ErrorRequest.REQ006_INVALID_TRANSITION, r.getStatus(), RequestStatus.CANCELLED);
            }
        }
        cancelWithCompensation(r, actor, reason == null || reason.isBlank()
                ? (actor.isAdmin() ? "ADMIN_CANCELLED" : "CLIENT_CANCELLED") : reason.trim());
        return RequestResponse.of(r);
    }

    /** Pasos del trabajador asignado: EN_ROUTE → IN_PROGRESS → PENDING_CONFIRMATION. */
    @Transactional
    public RequestResponse workerStep(Actor actor, UUID id, RequestStatus target) {
        ServiceRequest r = getOrThrow(id);
        if (!actor.isWorker() || !actor.id().equals(r.getWorkerId())) {
            throw new ApiException(ErrorRequest.REQ007_NOT_ALLOWED);
        }
        transitions.move(r, target, actor, null);
        if (target == RequestStatus.PENDING_CONFIRMATION) {
            r.setFinishedAt(clock.instant());
        }
        return RequestResponse.of(r);
    }

    /** El cliente confirma el trabajo terminado: se pide la captura del pago. */
    @Transactional
    public RequestResponse confirm(Actor actor, UUID id) {
        ServiceRequest r = getOrThrow(id);
        requireOwner(actor, r);
        confirmFinished(r, actor);
        return RequestResponse.of(r);
    }

    public void confirmFinished(ServiceRequest r, Actor actor) {
        if (r.getStatus() != RequestStatus.PENDING_CONFIRMATION) {
            throw new ApiException(ErrorRequest.REQ006_INVALID_TRANSITION, r.getStatus(), RequestStatus.COMPLETED);
        }
        if (r.getConfirmedAt() != null) return;   // ya confirmado: idempotente
        r.setConfirmedAt(clock.instant());
        r.setUpdatedAt(clock.instant());
        // Lleva además lo que facturación necesita para el comprobante (llega con payment.captured)
        Map<String, Object> data = new HashMap<>(transitions.paymentData(r));
        data.put("categoryName", r.getCategoryName());
        data.put("units", r.getUnits());
        data.put("addressText", r.getAddressText());
        data.put("scheduledAt", r.getScheduledAt().toString());
        data.put("confirmedAt", r.getConfirmedAt().toString());
        RegionProfileView profile = regionApi.current();
        data.put("receiptType", profile.receiptType());
        data.put("receiptPrefix", profile.receiptPrefix());
        data.put("regionLocale", profile.defaultLocale());
        data.put("taxRates", profile.taxRules().stream().collect(java.util.stream.Collectors.toMap(
                t -> t.name(), t -> t.rateBps(), (a, b) -> a)));
        userApi.fiscalIdentityOf(r.getClientId()).ifPresent(f -> {
            data.put("customerName", f.name());
            data.put("customerLegalName", f.legalName());
            data.put("customerTaxIdType", f.taxIdType());
            data.put("customerTaxIdNumber", f.taxIdNumber());
        });
        transitions.emit(r, EventTypes.REQUEST_CAPTURE_REQUESTED, data);
    }

    public void cancelWithCompensation(ServiceRequest r, Actor actor, String reason) {
        RequestStatus from = r.getStatus();
        transitions.move(r, RequestStatus.CANCELLED, actor, reason);
        r.setCancelReason(reason);
        emitCancelled(r, from, reason);
    }

    public void transitionToExpired(ServiceRequest r) {
        transitions.move(r, RequestStatus.EXPIRED, Actor.SYSTEM, "NO_WORKER_BEFORE_SCHEDULE");
        r.setCancelReason("EXPIRED");
    }

    public void emitCancelled(ServiceRequest r, RequestStatus from, String reason) {
        Map<String, Object> data = transitions.paymentData(r);
        data.put("fromStatus", from.name());
        data.put("reason", reason);
        data.put("paymentAuthorized", from != RequestStatus.PENDING_PAYMENT);
        transitions.emit(r, EventTypes.REQUEST_CANCELLED, data);
    }

    // ------------------------------------------------------------------ internos

    public ServiceRequest getOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(ErrorRequest.REQ001_NOT_FOUND, id));
    }

    private void requireOwner(Actor actor, ServiceRequest r) {
        if (!actor.isClient() || !r.getClientId().equals(actor.id())) {
            throw new ApiException(ErrorRequest.REQ001_NOT_FOUND, r.getId());
        }
    }

    private void requireParticipant(Actor actor, ServiceRequest r) {
        boolean allowed = actor.isAdmin()
                || r.getClientId().equals(actor.id())
                || (r.getWorkerId() != null && r.getWorkerId().equals(actor.id()));
        if (!allowed) {
            throw new ApiException(ErrorRequest.REQ001_NOT_FOUND, r.getId());
        }
    }

    private CategoryPriceSummary priceOf(UUID categoryId) {
        return catalogApi.priceOf(categoryId)
                .orElseThrow(() -> new ApiException(ErrorRequest.REQ002_CATEGORY_UNAVAILABLE));
    }

    private static PaymentMethod requirePaymentMethod(RegionProfileView profile, String code) {
        return profile.paymentMethods().stream()
                .filter(m -> m.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorRequest.REQ004_PAYMENT_METHOD_NOT_ALLOWED, code));
    }

    private record Schedule(Instant at, String zone) {
    }

    /** Hora local + zona IANA permitida por el perfil → instante UTC, que debe estar en el futuro. */
    private Schedule resolveSchedule(RegionProfileView profile, LocalDateTime local, String timezone) {
        String zone = timezone == null || timezone.isBlank() ? profile.defaultTimezone() : timezone;
        if (!profile.allowedTimezones().contains(zone)) {
            throw new ApiException(ErrorRequest.REQ009_TIMEZONE_NOT_ALLOWED, zone);
        }
        Instant at = local.atZone(ZoneId.of(zone)).toInstant();
        if (!at.isAfter(clock.instant())) {
            throw new ApiException(ErrorRequest.REQ003_SCHEDULE_IN_PAST);
        }
        return new Schedule(at, zone);
    }

    private static void applyQuote(ServiceRequest r, PricingService.Quote q) {
        r.setCurrency(q.currency());
        r.setMoneyScale(q.moneyScale());
        r.setSubtotalMinor(q.subtotalMinor());
        r.setTravelFeeMinor(q.travelFeeMinor());
        r.setTaxMinor(q.taxMinor());
        r.setTaxIncluded(q.taxIncluded());
        r.setTipMinor(q.tipMinor());
        r.setTotalMinor(q.totalMinor());
        r.getLines().clear();
        r.getLines().addAll(q.lines());
    }

    private static QuoteResponse toQuote(RegionProfileView profile, CategoryPriceSummary price, int units,
                                         PricingService.Quote q) {
        return new QuoteResponse(price.categoryId(), price.name(), price.unit(), units, q.lines(),
                q.subtotalMinor(), q.travelFeeMinor(), q.taxMinor(), q.taxIncluded(), q.tipMinor(), q.totalMinor(),
                q.currency(), q.moneyScale(), profile.tipMode().name(), profile.tipSuggestedPercents());
    }
}
