package com.jornal.requests.services;

import com.jornal.requests.RequestProperties;
import com.jornal.requests.RequestStatus;
import com.jornal.requests.ServiceRequest;
import com.jornal.requests.ServiceRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Plazos de la saga. Corren en una sola instancia de la región (ShedLock). Los métodos con {@code now}
 * permiten probarlos con cualquier hora.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestTimeouts {

    private final ServiceRequestRepository repository;
    private final RequestService requests;
    private final RequestProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${jornal.requests.check-every:30s}", initialDelayString = "${jornal.requests.check-every:30s}")
    @SchedulerLock(name = "request-timeouts")
    @Transactional   // las llamadas internas no pasan por el proxy: la transacción va aquí
    public void run() {
        Instant now = clock.instant();
        expirePendingPayments(now);
        expireSearches(now);
        autoConfirm(now);
    }

    /** Pagos sin respuesta: el pedido se cancela (pagos anula si llegó a autorizar). */
    @Transactional
    public int expirePendingPayments(Instant now) {
        var stale = repository.findTop100ByStatusAndUpdatedAtBefore(RequestStatus.PENDING_PAYMENT,
                now.minus(properties.paymentTimeout()));
        stale.forEach(r -> requests.cancelWithCompensation(r, Actor.SYSTEM, "PAYMENT_TIMEOUT"));
        return log("payment timeouts", stale.size());
    }

    /** La hora del trabajo pasó sin trabajador: EXPIRED, con compensación del pago. */
    @Transactional
    public int expireSearches(Instant now) {
        var expired = repository.findTop100ByStatusAndScheduledAtBefore(RequestStatus.SEARCHING_WORKER, now);
        for (ServiceRequest r : expired) {
            RequestStatus from = r.getStatus();
            requests.transitionToExpired(r);
            requests.emitCancelled(r, from, "EXPIRED");
        }
        return log("expired searches", expired.size());
    }

    /** Trabajo terminado sin confirmación del cliente: se confirma solo y se pide la captura. */
    @Transactional
    public int autoConfirm(Instant now) {
        var pending = repository.findTop100ByStatusAndConfirmedAtIsNullAndFinishedAtBefore(
                RequestStatus.PENDING_CONFIRMATION, now.minus(properties.autoConfirmAfter()));
        pending.forEach(r -> requests.confirmFinished(r, Actor.SYSTEM));
        return log("auto confirmations", pending.size());
    }

    private static int log(String what, int count) {
        if (count > 0) log.info("Request timeouts: {} {}", count, what);
        return count;
    }
}
