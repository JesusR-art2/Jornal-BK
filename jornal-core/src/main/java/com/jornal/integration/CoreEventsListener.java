package com.jornal.integration;

import io.micrometer.core.instrument.MeterRegistry;
import com.jornal.requests.RequestSaga;
import jornal.common.events.CloudEvent;
import jornal.common.observability.Correlation;
import jornal.common.observability.MessageMetrics;
import jornal.common.subsystem.Inbox;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Respuestas de los subsistemas para esta región. Inbox + saga en una sola transacción local: si la saga falla
 * (por ejemplo, conflicto de versión optimista), el mensaje se reintenta y el inbox no queda marcado.
 * El {@code correlationid} del mensaje pasa al MDC: aparece en los logs y en los mensajes que emite la saga.
 */
@Component
@Slf4j
public class CoreEventsListener {

    private final Inbox inbox;
    private final RequestSaga saga;
    private final com.jornal.vacancies.VacancyPaymentEvents vacancyPayments;
    private final MeterRegistry metrics;

    public CoreEventsListener(Inbox inbox, RequestSaga saga, com.jornal.vacancies.VacancyPaymentEvents vacancyPayments,
                              ObjectProvider<MeterRegistry> metrics) {
        this.vacancyPayments = vacancyPayments;
        this.inbox = inbox;
        this.saga = saga;
        this.metrics = metrics.getIfAvailable();
    }

    @RabbitListener(queues = "#{coreQueueName}")
    @Transactional
    public void onEvent(CloudEvent event) {
        try (MDC.MDCCloseable ignored = Correlation.open(event.correlationid())) {
            if (!inbox.firstTime(event)) {
                log.info("Duplicate message {} ({}) discarded", event.id(), event.type());
                MessageMetrics.record(metrics, "core", event, "duplicate");
                return;
            }
            try {
                if (vacancyPayments.supports(event.type())) {
                    vacancyPayments.handle(event.type(), event.data());
                } else {
                    saga.handle(event);
                }
            } catch (RuntimeException e) {
                MessageMetrics.record(metrics, "core", event, "error");
                throw e;
            }
            MessageMetrics.record(metrics, "core", event, "ok");
        }
    }
}
