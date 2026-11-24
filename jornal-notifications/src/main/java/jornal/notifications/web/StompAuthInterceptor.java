package jornal.notifications.web;

import jornal.notifications.NotificationRepository;
import jornal.notifications.RequestTopics;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/**
 * Autenticación y permisos del WebSocket:
 * <ul>
 *   <li>CONNECT: exige un JWT válido del core en el header {@code Authorization}.</li>
 *   <li>SUBSCRIBE: {@code /topic/users.<id>} solo el propio usuario; {@code /topic/requests.<id>} solo
 *       el cliente o el trabajador del pedido. Cualquier otro destino se rechaza.</li>
 *   <li>SEND: no se permite (los clientes solo reciben).</li>
 * </ul>
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final NotificationRepository repository;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, NotificationRepository repository) {
        this.jwtDecoder = jwtDecoder;
        this.repository = repository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorize(accessor.getUser(), accessor.getDestination());
            case SEND -> throw new MessagingException("Sending messages is not allowed");
            default -> { }
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing bearer token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()).trim());
            return new JwtAuthenticationToken(jwt);
        } catch (JwtException e) {
            throw new MessagingException("Invalid token");
        }
    }

    private void authorize(Principal user, String destination) {
        if (user == null) {
            throw new MessagingException("Not authenticated");
        }
        UUID userId = UUID.fromString(user.getName());
        Optional<UUID> ownTopic = RequestTopics.userOf(destination);
        if (ownTopic.isPresent()) {
            if (!ownTopic.get().equals(userId)) throw new MessagingException("Forbidden topic");
            return;
        }
        Optional<UUID> requestTopic = RequestTopics.requestOf(destination);
        if (requestTopic.isPresent()) {
            if (!repository.isParticipant(requestTopic.get(), userId)) throw new MessagingException("Forbidden topic");
            return;
        }
        throw new MessagingException("Unknown topic");
    }
}
