package com.jornal.requests.controllers;

import com.jornal.requests.RequestStatus;
import com.jornal.requests.dto.*;
import com.jornal.requests.services.Actor;
import com.jornal.requests.services.RequestService;
import com.jornal.shared.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/requests")
@RequiredArgsConstructor
public class RequestController {

    private static final String CLIENT = "hasAnyRole('APPLICANT','BUSINESS')";

    private final RequestService requests;
    private final com.jornal.requests.services.RatingService ratings;

    /** Cotiza el carrito sin guardar nada. */
    @PostMapping("/quote")
    @PreAuthorize(CLIENT)
    public QuoteResponse quote(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RequestDraft draft) {
        return requests.quote(actor(jwt), draft);
    }

    /** Crea el pedido. Exige {@code Idempotency-Key}: repetirla devuelve el mismo pedido (200 en vez de 201). */
    @PostMapping
    @PreAuthorize(CLIENT)
    public ResponseEntity<RequestResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                                  @Valid @RequestBody RequestDraft draft) {
        RequestService.Created created = requests.create(actor(jwt), key, draft);
        return ResponseEntity.status(created.isNew() ? HttpStatus.CREATED : HttpStatus.OK).body(created.request());
    }

    /** Pedidos propios: como cliente, los que pidió; como trabajador, los asignados. */
    @GetMapping
    public PageResponse<RequestResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) RequestStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return requests.mine(actor(jwt), status, pageable(page, size));
    }

    @GetMapping("/{id}")
    public RequestResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return requests.get(actor(jwt), id);
    }

    @GetMapping("/{id}/history")
    public List<HistoryResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return requests.history(actor(jwt), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(CLIENT)
    public RequestResponse reprice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @Valid @RequestBody RepriceRequest change) {
        return requests.reprice(actor(jwt), id, change);
    }

    /** Califica el pedido terminado: el cliente al trabajador o el trabajador al cliente (una vez por sentido). */
    @PostMapping("/{id}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('APPLICANT','BUSINESS','WORKER')")
    public RatingResponse rate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody RatingRequest body) {
        return ratings.rate(actor(jwt), id, body);
    }

    @GetMapping("/{id}/ratings")
    public List<RatingResponse> ratings(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ratings.of(actor(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('APPLICANT','BUSINESS','ADMIN')")
    public RequestResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody(required = false) CancelRequest body) {
        return requests.cancel(actor(jwt), id, body == null ? null : body.reason());
    }

    @PostMapping("/{id}/en-route")
    @PreAuthorize("hasRole('WORKER')")
    public RequestResponse enRoute(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return requests.workerStep(actor(jwt), id, RequestStatus.EN_ROUTE);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('WORKER')")
    public RequestResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return requests.workerStep(actor(jwt), id, RequestStatus.IN_PROGRESS);
    }

    @PostMapping("/{id}/finish")
    @PreAuthorize("hasRole('WORKER')")
    public RequestResponse finish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return requests.workerStep(actor(jwt), id, RequestStatus.PENDING_CONFIRMATION);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(CLIENT)
    public RequestResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return requests.confirm(actor(jwt), id);
    }

    static Actor actor(Jwt jwt) {
        return new Actor(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"));
    }

    static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
    }
}
