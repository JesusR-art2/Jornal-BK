package com.jornal.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Si RabbitMQ estaba caído, la publicación queda incompleta en {@code event_publication}. Esta tarea la
 * reintenta cada 30 s. Un reenvío duplicado no hace daño: los consumidores lo descartan con su inbox.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxResubmitter {

    private final IncompleteEventPublications incomplete;

    @Scheduled(fixedDelayString = "${jornal.outbox.resubmit-every:30s}",
               initialDelayString = "${jornal.outbox.resubmit-every:30s}")
    public void resubmit() {
        try {
            incomplete.resubmitIncompletePublicationsOlderThan(Duration.ofSeconds(30));
        } catch (RuntimeException e) {
            log.warn("Outbox resubmission failed, will retry: {}", e.getMessage());
        }
    }
}
