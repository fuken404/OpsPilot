package dev.fuken404.opspilot.realtime;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.identity.Users;
import dev.fuken404.opspilot.organizations.OrganizationAccess;
import dev.fuken404.opspilot.organizations.Role;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Protocolo mínimo sobre WebSocket:
 * <ol>
 *   <li>{@code {"type":"auth","token":"..."}}: el mismo access token de la API. Se puede repetir al renovarlo.</li>
 *   <li>{@code {"type":"subscribe","organizationId":"..."}}: solo si la persona es miembro.</li>
 * </ol>
 * La conexión se cierra (código 4401) si no se autentica en 10 segundos o cuando el token vence.
 */
@Component
class RealtimeHandler extends TextWebSocketHandler {

    static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4401, "Token vencido");
    private static final Duration AUTH_GRACE = Duration.ofSeconds(10);
    private static final Logger log = LoggerFactory.getLogger(RealtimeHandler.class);

    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final JwtDecoder jwtDecoder;
    private final Users users;
    private final OrganizationAccess access;
    private final JsonMapper json;
    private final Clock clock;

    RealtimeHandler(JwtDecoder jwtDecoder, Users users, OrganizationAccess access, JsonMapper json, Clock clock) {
        this.jwtDecoder = jwtDecoder;
        this.users = users;
        this.access = access;
        this.json = json;
        this.clock = clock;
    }

    /** Estado de una conexión. Se modifica solo desde los mensajes de esa misma conexión. */
    private static final class Client {

        final WebSocketSession session;
        final Instant connectedAt;
        volatile UUID userId;
        volatile Instant expiresAt;
        volatile UUID organizationId;
        volatile Role role;

        Client(WebSocketSession session, Instant connectedAt) {
            this.session = session;
            this.connectedAt = connectedAt;
        }

        boolean receives(OrgMessage message) {
            if (organizationId == null || !organizationId.equals(message.organizationId())) {
                return false;
            }
            return role != Role.CUSTOMER
                || message.audience() == OrgMessage.Audience.STAFF_AND_USER && message.userId().equals(userId);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // El decorador serializa los envíos: WebSocketSession no admite dos envíos a la vez.
        clients.put(session.getId(), new Client(new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024),
            clock.instant()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Client client = clients.get(session.getId());
        if (client == null) {
            return;
        }
        JsonNode body;
        try {
            body = json.readTree(message.getPayload());
        } catch (RuntimeException invalid) {
            send(client, Map.of("type", "error", "message", "Mensaje inválido."));
            return;
        }
        switch (body.path("type").asString("")) {
            case "auth" -> authenticate(client, body.path("token").asString(""));
            case "subscribe" -> subscribe(client, body.path("organizationId").asString(""));
            case "ping" -> send(client, Map.of("type", "pong"));
            default -> send(client, Map.of("type", "error", "message", "Tipo de mensaje desconocido."));
        }
    }

    private void authenticate(Client client, String token) throws IOException {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException invalid) {
            client.session.close(TOKEN_EXPIRED);
            return;
        }
        AppUser user = users.current(jwt);
        if (client.userId != null && !client.userId.equals(user.getId())) {
            client.session.close(CloseStatus.POLICY_VIOLATION); // no se cambia de persona a mitad de conexión
            return;
        }
        client.userId = user.getId();
        client.expiresAt = jwt.getExpiresAt();
        send(client, Map.of("type", "ready"));
    }

    private void subscribe(Client client, String organizationId) throws IOException {
        if (client.userId == null) {
            send(client, Map.of("type", "error", "message", "Primero envía el token."));
            return;
        }
        Optional<Role> role;
        try {
            role = access.roleOf(UUID.fromString(organizationId), client.userId);
        } catch (IllegalArgumentException invalid) {
            role = Optional.empty();
        }
        if (role.isEmpty()) {
            client.organizationId = null;
            send(client, Map.of("type", "error", "message", "Organización no encontrada."));
            return;
        }
        client.role = role.get();
        client.organizationId = UUID.fromString(organizationId);
        send(client, Map.of("type", "subscribed", "organizationId", organizationId));
    }

    /** Entrega un evento a las conexiones de esta instancia que deben verlo. */
    void deliver(OrgMessage message) {
        TextMessage text = new TextMessage(message.payload());
        clients.values().stream().filter(c -> c.receives(message)).forEach(c -> {
            try {
                c.session.sendMessage(text);
            } catch (IOException | RuntimeException error) {
                log.debug("No se pudo entregar a la conexión {}: {}", c.session.getId(), error.getMessage());
            }
        });
    }

    /** Cierra las conexiones que no se autenticaron a tiempo o cuyo token ya venció. */
    @Scheduled(fixedDelay = 5_000)
    void closeExpired() {
        Instant now = clock.instant();
        clients.values().forEach(c -> {
            boolean unauthenticated = c.userId == null && c.connectedAt.plus(AUTH_GRACE).isBefore(now);
            boolean expired = c.expiresAt != null && c.expiresAt.isBefore(now);
            if (unauthenticated || expired) {
                try {
                    c.session.close(TOKEN_EXPIRED);
                } catch (IOException ignored) {
                    // ya estaba cerrada
                }
            }
        });
    }

    int connections() {
        return clients.size();
    }

    private void send(Client client, Map<String, ?> body) throws IOException {
        client.session.sendMessage(new TextMessage(json.writeValueAsString(body)));
    }
}
