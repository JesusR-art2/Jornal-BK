package jornal.notifications;

import jornal.common.events.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * Convierte eventos del pedido en avisos para cliente y trabajador, cada uno en su idioma y su zona horaria.
 * Canales: en la app (WebSocket y bandeja), correo (Mailpit), y SMS y push simulados en el log.
 * Cada entrega queda registrada una vez por evento, destinatario y canal.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public enum Channel { IN_APP, PUSH, EMAIL, SMS }

    /** Avisos urgentes que además van por SMS. */
    private static final Set<String> SMS_TEMPLATES = Set.of("offer", "status.ASSIGNED", "status.EN_ROUTE");

    private final Templates templates;
    private final ContactClient contacts;
    private final NotificationRepository repository;
    private final SimpMessagingTemplate websocket;
    private final JavaMailSender mail;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationService(Templates templates, ContactClient contacts, NotificationRepository repository,
                               SimpMessagingTemplate websocket, JavaMailSender mail,
                               NotificationProperties properties, Clock clock) {
        this.templates = templates;
        this.contacts = contacts;
        this.repository = repository;
        this.websocket = websocket;
        this.mail = mail;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ eventos

    public void onStatusChanged(CloudEvent event) {
        Map<String, Object> data = event.data();
        UUID requestId = uuid(data.get("requestId"));
        UUID clientId = uuid(data.get("clientId"));
        UUID workerId = uuid(data.get("workerId"));
        String status = String.valueOf(data.get("to"));
        repository.upsertParticipants(requestId, event.region(), clientId, workerId, status, event.time());

        // Tema del pedido: la app del cliente y del trabajador refrescan el estado sin consultar
        websocket.convertAndSend(RequestTopics.request(requestId),
                (Object) Map.of("type", "status", "requestId", requestId.toString(), "status", status,
                        "at", event.time().toString()));

        Map<String, Object> args = new HashMap<>();
        args.put("category", text(data.get("categoryName")));
        String template = "status." + status;
        notify(event, template, "client", clientId, requestId, args, instant(data.get("scheduledAt")));
        if (workerId != null) {
            notify(event, template, "worker", workerId, requestId, args, instant(data.get("scheduledAt")));
        }
    }

    public void onOfferCreated(CloudEvent event) {
        Map<String, Object> data = event.data();
        Instant expiresAt = instant(data.get("expiresAt"));
        long seconds = expiresAt == null ? 0 : Math.max(0, Duration.between(event.time(), expiresAt).toSeconds());
        Map<String, Object> args = new HashMap<>();
        args.put("category", text(data.get("categoryName")));
        args.put("address", text(data.get("addressText")));
        args.put("distance", data.get("distanceKm") instanceof Number n ? n : 0);
        args.put("seconds", seconds);
        notify(event, "offer", "worker", uuid(data.get("workerId")), uuid(data.get("requestId")), args,
                instant(data.get("scheduledAt")));
    }

    // ------------------------------------------------------------------ entrega

    private void notify(CloudEvent event, String template, String audience, UUID recipientId, UUID requestId,
                        Map<String, Object> baseArgs, Instant scheduledAt) {
        String key = template + "." + audience;
        if (recipientId == null || !templates.exists(key)) {
            return;   // ese cambio no se le avisa a esta audiencia
        }
        Optional<ContactClient.Contact> found = contacts.find(event.region(), recipientId);
        if (found.isEmpty() || !found.get().active()) {
            log.info("Recipient {} not found or inactive: {} not sent", recipientId, key);
            return;
        }
        ContactClient.Contact contact = found.get();
        Locale locale = Locale.forLanguageTag(contact.locale());
        ZoneId zone = ZoneId.of(contact.timezone());
        Map<String, Object> args = new HashMap<>(baseArgs);
        args.put("when", scheduledAt == null ? "" : templates.when(scheduledAt, locale, zone));
        Templates.Rendered message = templates.render(key, locale, args).orElseThrow();

        for (Channel channel : channelsOf(template)) {
            if (repository.exists(event.id(), recipientId, channel.name())) {
                continue;
            }
            Outcome outcome = send(channel, contact, requestId, template, message);
            repository.insert(new NotificationRepository.Delivery(outcome.id, event.region(), event.id(), requestId,
                    recipientId, channel.name(), template, contact.locale(), zone.getId(), outcome.destination,
                    message.title(), message.body(), outcome.status, outcome.error, clock.instant()));
        }
    }

    private record Outcome(UUID id, String destination, String status, String error) {
    }

    private Outcome send(Channel channel, ContactClient.Contact contact, UUID requestId, String template,
                         Templates.Rendered message) {
        UUID id = UUID.randomUUID();
        return switch (channel) {
            case IN_APP -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("id", id.toString());
                payload.put("requestId", requestId == null ? null : requestId.toString());
                payload.put("template", template);
                payload.put("title", message.title());
                payload.put("body", message.body());
                payload.put("createdAt", clock.instant().toString());
                websocket.convertAndSend(RequestTopics.user(contact.id()), (Object) payload);
                yield new Outcome(id, null, "SENT", null);
            }
            case PUSH -> {
                log.info("[PUSH simulated] to user {} ({}): {}", contact.id(), contact.locale(), message.title());
                yield new Outcome(id, null, "SENT", null);
            }
            case SMS -> {
                if (contact.phone() == null || contact.phone().isBlank()) {
                    yield new Outcome(id, null, "SKIPPED", "no phone");
                }
                log.info("[SMS simulated] to {}: {}", mask(contact.phone()), message.body());
                yield new Outcome(id, mask(contact.phone()), "SENT", null);
            }
            case EMAIL -> {
                if (contact.email() == null || contact.email().isBlank()) {
                    yield new Outcome(id, null, "SKIPPED", "no email");
                }
                try {
                    SimpleMailMessage email = new SimpleMailMessage();
                    email.setFrom(properties.mailFrom());
                    email.setTo(contact.email());
                    email.setSubject(message.title());
                    email.setText(message.body());
                    mail.send(email);
                    yield new Outcome(id, contact.email(), "SENT", null);
                } catch (MailException e) {
                    log.warn("Email to user {} failed: {}", contact.id(), e.getMessage());
                    yield new Outcome(id, contact.email(), "FAILED", e.getMessage());
                }
            }
        };
    }

    private static List<Channel> channelsOf(String template) {
        List<Channel> channels = new ArrayList<>(List.of(Channel.IN_APP, Channel.PUSH, Channel.EMAIL));
        if (SMS_TEMPLATES.contains(template)) {
            channels.add(Channel.SMS);
        }
        return channels;
    }

    private static String mask(String phone) {
        return phone.length() <= 4 ? "****" : "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }

    private static Instant instant(Object value) {
        return value == null ? null : Instant.parse(value.toString());
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
