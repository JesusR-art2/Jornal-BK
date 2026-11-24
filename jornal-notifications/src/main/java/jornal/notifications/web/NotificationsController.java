package jornal.notifications.web;

import jornal.notifications.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bandeja de avisos de la app y registro de entregas por pedido. */
@RestController
public class NotificationsController {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationsController(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Avisos del usuario (los mismos que llegan por WebSocket), el más reciente primero. */
    @GetMapping("/notifications/me")
    public List<Map<String, Object>> mine(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(defaultValue = "20") int limit) {
        return repository.inbox(UUID.fromString(jwt.getSubject()), Math.clamp(limit, 1, 100));
    }

    @PostMapping("/notifications/{id}/read")
    public Map<String, Object> read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        if (!repository.markRead(UUID.fromString(jwt.getSubject()), id, clock.instant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return Map.of("id", id, "read", true);
    }

    /** Entregas de un pedido en todos los canales (sin correo ni teléfono). */
    @GetMapping("/deliveries")
    public List<Map<String, Object>> deliveries(@RequestParam UUID requestId) {
        return repository.byRequest(requestId);
    }
}
