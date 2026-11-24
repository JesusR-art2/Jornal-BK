package jornal.notifications;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Temas STOMP: {@code /topic/users.<id>} (avisos de una persona) y {@code /topic/requests.<id>}
 * (estado y posición en vivo de un pedido, solo para su cliente y su trabajador).
 */
public final class RequestTopics {

    private static final Pattern USER = Pattern.compile("^/topic/users\\.([0-9a-fA-F-]{36})$");
    private static final Pattern REQUEST = Pattern.compile("^/topic/requests\\.([0-9a-fA-F-]{36})$");

    private RequestTopics() {
    }

    public static String user(UUID userId) {
        return "/topic/users." + userId;
    }

    public static String request(UUID requestId) {
        return "/topic/requests." + requestId;
    }

    public static Optional<UUID> userOf(String destination) {
        return match(USER, destination);
    }

    public static Optional<UUID> requestOf(String destination) {
        return match(REQUEST, destination);
    }

    private static Optional<UUID> match(Pattern pattern, String destination) {
        if (destination == null) return Optional.empty();
        Matcher m = pattern.matcher(destination);
        try {
            return m.matches() ? Optional.of(UUID.fromString(m.group(1))) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
