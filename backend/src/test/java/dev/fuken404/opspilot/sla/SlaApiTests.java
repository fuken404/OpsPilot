package dev.fuken404.opspilot.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.fuken404.opspilot.MutableClock;
import dev.fuken404.opspilot.TestcontainersConfiguration;
import dev.fuken404.opspilot.organizations.Organizations;
import dev.fuken404.opspilot.organizations.Role;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/** El tiempo lo controla la prueba: se adelanta el reloj en vez de esperar horas. */
@Import({TestcontainersConfiguration.class, MutableClock.Config.class})
@SpringBootTest(properties = "opspilot.internal-token=token-de-prueba")
@AutoConfigureMockMvc
class SlaApiTests {

    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    MockMvc mvc;

    @Autowired
    Organizations organizations;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    TransactionTemplate transaction;

    UUID acme;

    static Instant bogota(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(BOGOTA).toInstant();
    }

    static RequestPostProcessor as(String user) {
        return jwt().jwt(token -> token.subject("sub-" + user).claim("email", user + "@test.dev")
            .claim("name", user).claim("email_verified", true));
    }

    @BeforeEach
    void seed() throws Exception {
        clock.now = bogota("2026-11-09T09:00"); // lunes
        jdbc.update("delete from invitation");
        jdbc.update("delete from membership");
        jdbc.update("delete from organization");
        jdbc.update("delete from app_user");
        acme = organizations.createWithInvitations("Acme", "acme", Map.of(
            "admin@test.dev", Role.ADMIN, "tech@test.dev", Role.TECHNICIAN,
            "client@test.dev", Role.CUSTOMER, "client2@test.dev", Role.CUSTOMER));
        for (String user : List.of("admin", "tech", "client", "client2")) {
            mvc.perform(get("/api/me").with(as(user))).andExpect(status().isOk());
        }
    }

    String base() {
        return "/api/organizations/" + acme;
    }

    String create(String user, String priority) throws Exception {
        String body = mvc.perform(post(base() + "/tickets").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sin red\",\"description\":\"No hay conexión\",\"category\":\"NETWORK\""
                    + (priority == null ? "" : ",\"priority\":\"" + priority + "\"") + "}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    void move(String ticket, String user, long version, String to) throws Exception {
        mvc.perform(put(base() + "/tickets/" + ticket + "/status").with(as(user)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":" + version + ",\"status\":\"" + to + "\"}")).andExpect(status().isOk());
    }

    ResultActions sla(String ticket, String user) throws Exception {
        return mvc.perform(get(base() + "/tickets/" + ticket + "/sla").with(as(user)));
    }

    ResultActions sweep(String token) throws Exception {
        var request = post("/api/internal/sla/sweep");
        if (token != null) {
            request.header("X-Internal-Token", token);
        }
        return mvc.perform(request);
    }

    @Test
    void losRelojesCuentanEnHorarioLaboral() throws Exception {
        String ticket = create("client", null);
        // Media: 4 h para responder (lunes 13:00) y 24 h laborales para resolver (miércoles 13:00).
        sla(ticket, "client")
            .andExpect(jsonPath("$[0].kind").value("RESPONSE"))
            .andExpect(jsonPath("$[0].state").value("RUNNING"))
            .andExpect(jsonPath("$[0].dueAt").value("2026-11-09T18:00:00Z"))
            .andExpect(jsonPath("$[0].remainingMinutes").value(240))
            .andExpect(jsonPath("$[1].kind").value("RESOLUTION"))
            .andExpect(jsonPath("$[1].dueAt").value("2026-11-11T18:00:00Z"));
    }

    @Test
    void esperarAlClienteNoGastaTiempoDelSla() throws Exception {
        String ticket = create("client", null);
        clock.now = bogota("2026-11-09T10:00");
        move(ticket, "tech", 0, "IN_PROGRESS");
        move(ticket, "tech", 1, "WAITING_CUSTOMER");
        sla(ticket, "tech")
            .andExpect(jsonPath("$[0].state").value("MET"))
            .andExpect(jsonPath("$[1].state").value("PAUSED"));

        clock.now = bogota("2026-11-10T10:00"); // 10 horas laborales después
        mvc.perform(post(base() + "/tickets/" + ticket + "/comments").with(as("client"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Ya reinicié\",\"internal\":false}"))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        // La resolución se corre esas 10 horas: de miércoles 13:00 a jueves 13:00.
        sla(ticket, "tech")
            .andExpect(jsonPath("$[1].state").value("RUNNING"))
            .andExpect(jsonPath("$[1].dueAt").value("2026-11-12T18:00:00Z"));
    }

    @Test
    void siElClienteReabreElTiempoResueltoNoCuenta() throws Exception {
        String ticket = create("client", null);
        move(ticket, "tech", 0, "IN_PROGRESS");
        clock.now = bogota("2026-11-09T10:00");
        move(ticket, "tech", 1, "RESOLVED");
        sla(ticket, "client").andExpect(jsonPath("$[1].state").value("MET"));
        clock.now = bogota("2026-11-10T10:00");
        move(ticket, "client", 2, "IN_PROGRESS");
        sla(ticket, "client")
            .andExpect(jsonPath("$[1].state").value("RUNNING"))
            .andExpect(jsonPath("$[1].dueAt").value("2026-11-12T18:00:00Z"));
    }

    @Test
    void cambiarLaPrioridadRecalculaElVencimiento() throws Exception {
        String ticket = create("client", null);
        mvc.perform(patch(base() + "/tickets/" + ticket).with(as("tech")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"priority\":\"CRITICAL\",\"category\":\"NETWORK\"}")).andExpect(status().isOk());
        sla(ticket, "tech")
            .andExpect(jsonPath("$[1].targetMinutes").value(240))
            .andExpect(jsonPath("$[1].dueAt").value("2026-11-09T18:00:00Z"));
    }

    @Test
    void cadaOrganizacionDefineSusPoliticas() throws Exception {
        String change = "{\"responseMinutes\":30,\"resolutionMinutes\":120,\"businessHours\":false}";
        mvc.perform(put(base() + "/sla/policies/HIGH").with(as("tech")).contentType(MediaType.APPLICATION_JSON)
            .content(change)).andExpect(status().isForbidden());
        mvc.perform(put(base() + "/sla/policies/HIGH").with(as("admin")).contentType(MediaType.APPLICATION_JSON)
            .content(change)).andExpect(status().isOk());
        mvc.perform(get(base() + "/sla/policies").with(as("tech")))
            .andExpect(jsonPath("$[1].priority").value("HIGH"))
            .andExpect(jsonPath("$[1].responseMinutes").value(30));

        String ticket = create("tech", "HIGH");
        sla(ticket, "tech")
            .andExpect(jsonPath("$[0].state").value("MET"))
            .andExpect(jsonPath("$[1].businessHours").value(false))
            .andExpect(jsonPath("$[1].dueAt").value("2026-11-09T16:00:00Z"));
    }

    @Test
    void elClienteSoloVeElSlaDeSusTickets() throws Exception {
        String ticket = create("client", null);
        sla(ticket, "client2").andExpect(status().isNotFound());
        mvc.perform(get(base() + "/sla/open").with(as("client"))).andExpect(status().isForbidden());
        mvc.perform(get(base() + "/sla/open").with(as("tech"))).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void elBarridoExigeSuSecreto() throws Exception {
        sweep(null).andExpect(status().isUnauthorized());
        sweep("otro").andExpect(status().isUnauthorized());
        sweep("token-de-prueba").andExpect(status().isOk());
    }

    @Test
    void elBarridoAvisaYEscalaUnaSolaVez() throws Exception {
        String ticket = create("client", null);
        clock.now = bogota("2026-11-09T12:30"); // pasó el 80 % de las 4 h de respuesta
        sweep("token-de-prueba").andExpect(jsonPath("$.warnings").value(1)).andExpect(jsonPath("$.breaches").value(0));
        sweep("token-de-prueba").andExpect(jsonPath("$.warnings").value(0));
        sla(ticket, "tech").andExpect(jsonPath("$[0].state").value("AT_RISK"));

        clock.now = bogota("2026-11-09T13:05");
        sweep("token-de-prueba").andExpect(jsonPath("$.breaches").value(1));
        sweep("token-de-prueba").andExpect(jsonPath("$.breaches").value(0));
        sla(ticket, "tech")
            .andExpect(jsonPath("$[0].state").value("BREACHED"))
            .andExpect(jsonPath("$[0].remainingMinutes").value(-5))
            .andExpect(jsonPath("$[0].late").value(true));
        mvc.perform(get(base() + "/tickets/" + ticket + "/history").with(as("admin")))
            .andExpect(jsonPath("$[1].action").value("SLA_WARNING"))
            .andExpect(jsonPath("$[2].action").value("SLA_BREACHED"))
            .andExpect(jsonPath("$[2].details.kind").value("RESPONSE"))
            .andExpect(jsonPath("$[2].actor").isEmpty());
    }

    /** Dos barridos a la vez (cron solapado, dos instancias): cada vencimiento se registra una vez. */
    @Test
    void barridosSimultaneosNoDuplicanAlertas() throws Exception {
        for (int i = 0; i < 5; i++) {
            create("client", null);
        }
        clock.now = bogota("2026-11-12T09:00"); // vencieron respuesta y resolución de los cinco
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        List<Callable<String>> sweeps = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            sweeps.add(() -> {
                start.await();
                return sweep("token-de-prueba").andReturn().getResponse().getContentAsString();
            });
        }
        List<Future<String>> results = sweeps.stream().map(pool::submit).toList();
        start.countDown();
        int breaches = 0;
        for (Future<String> result : results) {
            breaches += (Integer) JsonPath.read(result.get(), "$.breaches");
        }
        pool.shutdown();

        assertThat(breaches).isEqualTo(10);
        Integer audited = transaction.execute(tx -> {
            jdbc.queryForObject("select set_config('app.organization_id', ?, true)", String.class, acme.toString());
            return jdbc.queryForObject("select count(*) from audit_event where action = 'SLA_BREACHED'", Integer.class);
        });
        assertThat(audited).isEqualTo(10);
    }
}
