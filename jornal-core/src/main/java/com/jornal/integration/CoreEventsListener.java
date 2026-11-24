package com.jornal.integration;

import com.jornal.requests.RequestSaga;
import jornal.common.events.CloudEvent;
import jornal.common.subsystem.Inbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Respuestas de los subsistemas para esta región. Inbox + saga en una sola transacción local: si la saga falla
 * (por ejemplo, conflicto de versión optimista), el mensaje se reintenta y el inbox no queda marcado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CoreEventsListener {

    private final Inbox inbox;
    private final RequestSaga saga;

    @RabbitListener(queues = "#{coreQueueName}")
    @Transactional
    public void onEvent(CloudEvent event) {
        if (!inbox.firstTime(event)) {
            log.info("Duplicate message {} ({}) discarded", event.id(), event.type());
            return;
        }
        saga.handle(event);
    }
}
