package dev.fuken404.opspilot.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.fuken404.opspilot.MutableClock;
import dev.fuken404.opspilot.TestcontainersConfiguration;
import dev.fuken404.opspilot.organizations.Organizations;
import dev.fuken404.opspilot.organizations.Role;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

@Import({TestcontainersConfiguration.class, MutableClock.Config.class, NotificationsTests.Config.class})
@SpringBootTest(properties = "opspilot.internal-token=token-de-prueba")
@AutoConfigureMockMvc
class NotificationsTests {

    /** Un SMTP de mentira: guarda lo que se envía y puede fallar a propósito. */
    static class RecordingMailSender extends JavaMailSenderImpl {

        final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
        final AtomicInteger failures = new AtomicInteger();

        @Override
        protected void doSend(MimeMessage[] messages, Object[] originals) {
            for (MimeMessage message : messages) {
                if (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                    throw new MailSendException("SMTP no disponible");
                }
                sent.add(message);
            }
        }

        List<String> recipients() {
            return sent.stream().map(m -> {
                try {
                    return Arrays.stream(m.getAllRecipients()).map(Address::toString).findFirst().orElseThrow();
                } catch (MessagingException error) {
                    throw new IllegalStateException(error);
                }
            }).toList();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    Organizations organizations;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    EmailDispatcher dispatcher;

    @Autowired
    RecordingMailSender mail;

    @Autowired
    MutableClock clock;

    UUID acme;

    static RequestPostProcessor as(String user) {
        return jwt().jwt(token -> token.subject("sub-" + user).claim("email", user + "@test.dev")
            .claim("name", user).claim("email_verified", true));
    }

    @BeforeEach
    void seed() throws Exception {
        clock.now = java.time.Instant.parse("2026-11-09T14:00:00Z");
        mail.sent.clear();
        mail.failures.set(0);
        jdbc.update("delete from invitation");
        jdbc.update("delete from membership");
        jdbc.update("delete from organization");
        jdbc.update("delete from app_user");
        acme = organizations.createWithInvitations("Acme", "acme", Map.of(
            "admin@test.dev", Role.ADMIN, "tech@test.dev", Role.TECHNICIAN, "client@test.dev", Role.CUSTOMER));
        for (String user : List.of("admin", "tech", "client")) {
            mvc.perform(get("/api/me").with(as(user))).andExpect(status().isOk());
        }
    }

    String create() throws Exception {
        String body = mvc.perform(post("/api/organizations/" + acme + "/tickets").with(as("client"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Impresora atascada\",\"description\":\"Papel\",\"category\":\"HARDWARE\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    String userId(String user) {
        return jdbc.queryForObject("select id::text from app_user where email = ?", String.class, user + "@test.dev");
    }

    void assign(String ticket, String actor, long version, String assignee) throws Exception {
        mvc.perform(put("/api/organizations/" + acme + "/tickets/" + ticket + "/assignee").with(as(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + ",\"assigneeId\":\"" + userId(assignee) + "\"}"))
            .andExpect(status().isOk());
    }

    void comment(String ticket, String actor, boolean internal) throws Exception {
        mvc.perform(post("/api/organizations/" + acme + "/tickets/" + ticket + "/comments").with(as(actor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Texto privado\",\"internal\":" + internal + "}"))
            .andExpect(status().isCreated());
    }

    List<String> deliver() {
        mail.sent.clear();
        dispatcher.dispatch();
        return mail.recipients();
    }

    String statusOfOutbox() {
        return transaction.execute(tx -> {
            jdbc.queryForObject("select set_config('app.organization_id', ?, true)", String.class, acme.toString());
            return jdbc.queryForObject("select string_agg(status || ':' || attempts, ',' order by created_at) "
                + "from email_outbox", String.class);
        });
    }

    @Test
    void cadaCambioAvisaAQuienCorrespondeYNuncaAQuienLoHizo() throws Exception {
        String ticket = create();
        assertThat(deliver()).containsExactlyInAnyOrder("admin@test.dev", "tech@test.dev");
        assertThat(mail.sent.getFirst().getSubject()).isEqualTo("[OpsPilot] #1 Impresora atascada");
        assertThat((String) mail.sent.getFirst().getContent()).contains("/app/tickets/" + ticket)
            .doesNotContain("Texto privado");

        assign(ticket, "tech", 0, "tech");
        assertThat(deliver()).isEmpty(); // se lo asignó a sí mismo

        comment(ticket, "admin", true);
        assertThat(deliver()).containsExactly("tech@test.dev"); // nota interna: solo el responsable

        comment(ticket, "tech", false);
        List<String> toClient = deliver();
        assertThat(toClient).containsExactly("client@test.dev");
        assertThat((String) mail.sent.getFirst().getContent()).doesNotContain("Texto privado");

        comment(ticket, "client", false);
        assertThat(deliver()).containsExactly("tech@test.dev");
    }

    @Test
    void siElSmtpFallaElCorreoSeReintentaHastaSalir() throws Exception {
        mail.failures.set(1);
        create();
        assertThat(deliver()).hasSize(1); // uno falló, el otro salió
        assertThat(statusOfOutbox()).contains("PENDING:1").contains("SENT:1");

        assertThat(deliver()).isEmpty(); // aún no toca reintentar
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertThat(deliver()).hasSize(1);
        assertThat(statusOfOutbox()).doesNotContain("PENDING");
    }

    @Test
    void trasOchoIntentosSeDaPorFallido() throws Exception {
        mail.failures.set(1_000);
        create();
        for (int attempt = 0; attempt < 10; attempt++) {
            dispatcher.dispatch();
            clock.now = clock.now.plus(Duration.ofHours(1));
        }
        assertThat(statusOfOutbox()).isEqualTo("FAILED:8,FAILED:8");
    }

    @Test
    void siElCambioFallaNoQuedaCorreo() throws Exception {
        String ticket = create();
        deliver();
        mvc.perform(put("/api/organizations/" + acme + "/tickets/" + ticket + "/status").with(as("tech"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"status\":\"CLOSED\"}"))
            .andExpect(status().isConflict());
        assertThat(deliver()).isEmpty();
    }

    @Test
    void elEndpointDelCronExigeSuSecreto() throws Exception {
        mvc.perform(post("/api/internal/mail/dispatch")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/mail/dispatch").header("X-Internal-Token", "token-de-prueba"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(0));
    }
}
