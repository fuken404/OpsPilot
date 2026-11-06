package dev.fuken404.opspilot.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.fuken404.opspilot.TestcontainersConfiguration;
import dev.fuken404.opspilot.organizations.Organizations;
import dev.fuken404.opspilot.organizations.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.GenericContainer;

/**
 * Prueba de punta a punta con Redis real: el evento sale de la transacción, pasa por el canal de
 * Redis y llega por WebSocket solo a quien le corresponde.
 */
@Import({TestcontainersConfiguration.class, RealtimeTests.Config.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "opspilot.realtime.broker=redis")
@AutoConfigureMockMvc
class RealtimeTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> valkey() {
            return new GenericContainer<>("valkey/valkey:8-alpine").withExposedPorts(6379);
        }

        /** "token-ana" es un token válido de ana; cualquier otra cosa, inválido. */
        @Bean
        @Primary
        JwtDecoder testTokens() {
            return token -> {
                if (!token.startsWith("token-")) {
                    throw new BadJwtException("Token inválido");
                }
                String user = token.substring("token-".length());
                return Jwt.withTokenValue(token).header("alg", "none").subject("sub-" + user)
                    .claim("email", user + "@test.dev").claim("name", user).claim("email_verified", true)
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
            };
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mvc;

    @Autowired
    Organizations organizations;

    @Autowired
    JdbcTemplate jdbc;

    UUID acme;
    UUID nimbus;
    final List<Socket> sockets = new ArrayList<>();

    static RequestPostProcessor as(String user) {
        return jwt().jwt(token -> token.subject("sub-" + user).claim("email", user + "@test.dev")
            .claim("name", user).claim("email_verified", true));
    }

    /** Una pestaña del navegador: guarda lo que llega y cómo se cerró. */
    class Socket extends TextWebSocketHandler {

        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        String next() throws InterruptedException {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertThat(message).as("mensaje esperado").isNotNull();
            return message;
        }

        void expectNothing() throws InterruptedException {
            assertThat(messages.poll(400, TimeUnit.MILLISECONDS)).isNull();
        }
    }

    Socket open() throws Exception {
        Socket socket = new Socket();
        socket.session = new StandardWebSocketClient().execute(socket, "ws://localhost:" + port + "/ws")
            .get(5, TimeUnit.SECONDS);
        sockets.add(socket);
        return socket;
    }

    Socket subscribed(String user, UUID organization) throws Exception {
        Socket socket = open();
        socket.send("{\"type\":\"auth\",\"token\":\"token-" + user + "\"}");
        assertThat(socket.next()).contains("\"ready\"");
        socket.send("{\"type\":\"subscribe\",\"organizationId\":\"" + organization + "\"}");
        assertThat(socket.next()).contains("\"subscribed\"");
        return socket;
    }

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("delete from invitation");
        jdbc.update("delete from membership");
        jdbc.update("delete from organization");
        jdbc.update("delete from app_user");
        acme = organizations.createWithInvitations("Acme", "acme", Map.of(
            "tech@test.dev", Role.TECHNICIAN, "client@test.dev", Role.CUSTOMER, "client2@test.dev", Role.CUSTOMER));
        nimbus = organizations.createWithInvitations("Nimbus", "nimbus", Map.of("other@test.dev", Role.ADMIN));
        for (String user : List.of("tech", "client", "client2", "other")) {
            mvc.perform(get("/api/me").with(as(user))).andExpect(status().isOk());
        }
    }

    @AfterEach
    void close() throws Exception {
        for (Socket socket : sockets) {
            if (socket.session.isOpen()) {
                socket.session.close();
            }
        }
    }

    String comment(String ticket, String user, boolean internal) throws Exception {
        return mvc.perform(post("/api/organizations/" + acme + "/tickets/" + ticket + "/comments").with(as(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Hola\",\"internal\":" + internal + "}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void cadaEventoLlegaSoloAQuienPuedeVerlo() throws Exception {
        Socket tech = subscribed("tech", acme);
        Socket client = subscribed("client", acme);
        Socket client2 = subscribed("client2", acme);
        Socket other = subscribed("other", nimbus);

        String body = mvc.perform(post("/api/organizations/" + acme + "/tickets").with(as("client"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sin VPN\",\"description\":\"No conecta\",\"category\":\"NETWORK\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ticket = JsonPath.read(body, "$.id");

        String event = tech.next();
        assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("ticket");
        assertThat((String) JsonPath.read(event, "$.action")).isEqualTo("CREATED");
        assertThat((String) JsonPath.read(event, "$.ticketId")).isEqualTo(ticket);
        assertThat(client.next()).contains(ticket); // quien lo reportó
        client2.expectNothing();                   // otro cliente de la misma empresa
        other.expectNothing();                     // otra empresa

        comment(ticket, "tech", true);
        assertThat(tech.next()).contains("COMMENTED");
        client.expectNothing(); // ni el aviso de una nota interna llega al cliente

        comment(ticket, "tech", false);
        assertThat(tech.next()).contains("COMMENTED");
        assertThat(client.next()).contains("COMMENTED");
    }

    @Test
    void sinTokenNoHaySuscripcionYUnTokenInvalidoCierra() throws Exception {
        Socket socket = open();
        socket.send("{\"type\":\"subscribe\",\"organizationId\":\"" + acme + "\"}");
        assertThat(socket.next()).contains("Primero envía el token");
        socket.send("{\"type\":\"auth\",\"token\":\"falso\"}");
        assertThat(socket.closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(4401);
    }

    @Test
    void noSePuedeEscucharOtraOrganizacion() throws Exception {
        Socket socket = open();
        socket.send("{\"type\":\"auth\",\"token\":\"token-tech\"}");
        assertThat(socket.next()).contains("\"ready\"");
        socket.send("{\"type\":\"subscribe\",\"organizationId\":\"" + nimbus + "\"}");
        assertThat(socket.next()).contains("Organización no encontrada");
        socket.send("{\"type\":\"subscribe\",\"organizationId\":\"no-es-uuid\"}");
        assertThat(socket.next()).contains("Organización no encontrada");
    }
}
