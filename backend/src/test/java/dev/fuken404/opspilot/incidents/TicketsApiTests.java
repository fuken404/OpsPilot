package dev.fuken404.opspilot.incidents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.fuken404.opspilot.TestcontainersConfiguration;
import dev.fuken404.opspilot.organizations.Organizations;
import dev.fuken404.opspilot.organizations.Role;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TicketsApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    Organizations organizations;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    UUID acme;
    UUID nimbus;

    static RequestPostProcessor as(String user) {
        return jwt().jwt(token -> token.subject("sub-" + user).claim("email", user + "@test.dev")
            .claim("name", user).claim("email_verified", true));
    }

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("delete from invitation");
        jdbc.update("delete from membership");
        jdbc.update("delete from organization");
        jdbc.update("delete from app_user");
        acme = organizations.createWithInvitations("Acme", "acme", Map.of(
            "admin@test.dev", Role.ADMIN, "tech@test.dev", Role.TECHNICIAN, "tech2@test.dev", Role.TECHNICIAN,
            "client@test.dev", Role.CUSTOMER, "client2@test.dev", Role.CUSTOMER));
        nimbus = organizations.createWithInvitations("Nimbus", "nimbus", Map.of("other@test.dev", Role.ADMIN));
        for (String user : List.of("admin", "tech", "tech2", "client", "client2", "other")) {
            mvc.perform(get("/api/me").with(as(user))).andExpect(status().isOk());
        }
    }

    String json(Object... pairs) {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            String rendered = value == null ? "null"
                : value instanceof Number || value instanceof Boolean ? value.toString() : "\"" + value + "\"";
            json.append(i == 0 ? "" : ",").append('"').append(pairs[i]).append("\":").append(rendered);
        }
        return json.append('}').toString();
    }

    String tickets(UUID organization) {
        return "/api/organizations/" + organization + "/tickets";
    }

    ResultActions create(UUID organization, String user, String title, String priority) throws Exception {
        return mvc.perform(post(tickets(organization)).with(as(user)).contentType(MediaType.APPLICATION_JSON)
            .content(json("title", title, "description", "No funciona", "category", "SOFTWARE", "priority", priority)));
    }

    String created(UUID organization, String user, String title) throws Exception {
        String body = create(organization, user, title, "HIGH").andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    ResultActions move(String ticket, String user, long version, String to) throws Exception {
        return mvc.perform(put(tickets(acme) + "/" + ticket + "/status").with(as(user))
            .contentType(MediaType.APPLICATION_JSON).content(json("version", version, "status", to)));
    }

    ResultActions assign(String ticket, String user, long version, String assignee) throws Exception {
        return mvc.perform(put(tickets(acme) + "/" + ticket + "/assignee").with(as(user))
            .contentType(MediaType.APPLICATION_JSON).content(json("version", version, "assigneeId", assignee)));
    }

    ResultActions comment(String ticket, String user, String body, boolean internal) throws Exception {
        return mvc.perform(post(tickets(acme) + "/" + ticket + "/comments").with(as(user))
            .contentType(MediaType.APPLICATION_JSON).content(json("body", body, "internal", internal)));
    }

    String userId(String user) {
        return jdbc.queryForObject("select id::text from app_user where email = ?", String.class, user + "@test.dev");
    }

    @Test
    void elClienteCreaConPrioridadMediaYSoloVeLosSuyos() throws Exception {
        create(acme, "client", "La impresora no imprime", "CRITICAL")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.number").value(1))
            .andExpect(jsonPath("$.priority").value("MEDIUM"))
            .andExpect(jsonPath("$.status").value("NEW"))
            .andExpect(jsonPath("$.canAssign").value(false));
        String ticket = created(acme, "tech", "Servidor lento");

        mvc.perform(get(tickets(acme)).with(as("client"))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(tickets(acme)).with(as("client2"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(tickets(acme) + "/" + ticket).with(as("client"))).andExpect(status().isNotFound());
        mvc.perform(get(tickets(acme)).with(as("tech"))).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(tickets(acme)).param("q", "IMPRESORA").with(as("tech"))).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void cadaOrganizacionNumeraSusTickets() throws Exception {
        create(acme, "client", "Uno", null).andExpect(jsonPath("$.number").value(1));
        create(acme, "client", "Dos", null).andExpect(jsonPath("$.number").value(2));
        create(nimbus, "other", "Primero de Nimbus", null).andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void losTicketsDeOtraOrganizacionNoExisten() throws Exception {
        String foreign = created(nimbus, "other", "Secreto de Nimbus");
        mvc.perform(get(tickets(acme) + "/" + foreign).with(as("admin"))).andExpect(status().isNotFound());
        mvc.perform(get(tickets(nimbus) + "/" + foreign).with(as("admin"))).andExpect(status().isNotFound());
        mvc.perform(get(tickets(acme) + "/" + foreign + "/history").with(as("admin")))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elFlujoValidaTransicionesVersionYRol() throws Exception {
        String ticket = created(acme, "client", "No hay internet");
        move(ticket, "tech", 0, "CLOSED").andExpect(status().isConflict());
        move(ticket, "client", 0, "IN_PROGRESS").andExpect(status().isForbidden());
        move(ticket, "tech", 0, "IN_PROGRESS").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        move(ticket, "tech", 0, "RESOLVED").andExpect(status().isConflict());
        move(ticket, "client", 1, "RESOLVED").andExpect(status().isForbidden());
        move(ticket, "tech", 1, "RESOLVED").andExpect(status().isOk())
            .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        mvc.perform(get(tickets(acme) + "/" + ticket).with(as("client")))
            .andExpect(jsonPath("$.nextStatuses.length()").value(2));
        move(ticket, "client", 2, "CLOSED").andExpect(status().isOk())
            .andExpect(jsonPath("$.closedAt").isNotEmpty())
            .andExpect(jsonPath("$.canComment").value(false));
        comment(ticket, "client", "Gracias", false).andExpect(status().isConflict());
    }

    @Test
    void elClienteNoVeNotasInternasNiElHistorial() throws Exception {
        String ticket = created(acme, "client", "Correo caído");
        comment(ticket, "tech", "Parece el certificado del servidor", true).andExpect(status().isCreated());
        comment(ticket, "tech", "Estamos revisando", false).andExpect(status().isCreated());
        comment(ticket, "client", "Intento colarme como nota interna", true).andExpect(status().isForbidden());

        mvc.perform(get(tickets(acme) + "/" + ticket).with(as("client")))
            .andExpect(jsonPath("$.comments.length()").value(1))
            .andExpect(jsonPath("$.comments[0].body").value("Estamos revisando"));
        mvc.perform(get(tickets(acme) + "/" + ticket).with(as("tech")))
            .andExpect(jsonPath("$.comments.length()").value(2));
        mvc.perform(get(tickets(acme) + "/" + ticket + "/history").with(as("client"))).andExpect(status().isForbidden());
        mvc.perform(get(tickets(acme) + "/" + ticket + "/history").with(as("tech")))
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].action").value("CREATED"))
            .andExpect(jsonPath("$[1].details.internal").value(true))
            .andExpect(jsonPath("$[2].actor.displayName").value("tech"));
    }

    @Test
    void laRespuestaDelClienteReactivaElTicket() throws Exception {
        String ticket = created(acme, "client", "VPN no conecta");
        move(ticket, "tech", 0, "IN_PROGRESS").andExpect(status().isOk());
        move(ticket, "tech", 1, "WAITING_CUSTOMER").andExpect(status().isOk());
        comment(ticket, "client", "Ya reinicié el equipo", false)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(get(tickets(acme) + "/" + ticket + "/history").with(as("admin")))
            .andExpect(jsonPath("$[4].details.reason").value("CUSTOMER_REPLIED"));
    }

    @Test
    void reglasDeAsignacion() throws Exception {
        String ticket = created(acme, "client", "Pantalla azul");
        assign(ticket, "tech", 0, userId("tech2")).andExpect(status().isForbidden());
        assign(ticket, "admin", 0, userId("client")).andExpect(status().isConflict());
        assign(ticket, "client", 0, userId("client")).andExpect(status().isForbidden());
        assign(ticket, "tech", 0, userId("tech")).andExpect(status().isOk())
            .andExpect(jsonPath("$.assignee.displayName").value("tech"));
        assign(ticket, "tech2", 1, userId("tech2")).andExpect(status().isForbidden());
        assign(ticket, "tech", 1, userId("tech2")).andExpect(status().isOk());
        assign(ticket, "admin", 2, null).andExpect(status().isOk()).andExpect(jsonPath("$.assignee").isEmpty());
        mvc.perform(get(tickets(acme)).param("assignee", "UNASSIGNED").with(as("tech")))
            .andExpect(jsonPath("$.length()").value(1));
    }

    /** Dos técnicos toman el mismo ticket a la vez: el bloqueo optimista deja ganar a uno solo. */
    @Test
    void dosTecnicosNoTomanElMismoTicket() throws Exception {
        String ticket = created(acme, "client", "Sin acceso al ERP");
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> takes = new ArrayList<>();
        for (String user : List.of("tech", "tech2")) {
            String id = userId(user);
            takes.add(() -> {
                start.await();
                return assign(ticket, user, 0, id).andReturn().getResponse().getStatus();
            });
        }
        List<Future<Integer>> results = takes.stream().map(pool::submit).toList();
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        Integer audited = jdbc.queryForObject("select count(*) from audit_event where action = 'ASSIGNED'", Integer.class);
        assertThat(audited).isZero();
    }

    /** La base aísla por sí sola: aunque una consulta olvide filtrar por organización, no ve otra empresa. */
    @Test
    void rowLevelSecurityAislaAunqueElCodigoNoFiltre() throws Exception {
        created(acme, "client", "De Acme");
        created(nimbus, "other", "De Nimbus");

        assertThat(jdbc.queryForObject("select count(*) from ticket", Integer.class)).isZero();
        Integer visible = transaction.execute(tx -> {
            jdbc.queryForObject("select set_config('app.organization_id', ?, true)", String.class, nimbus.toString());
            return jdbc.queryForObject("select count(*) from ticket", Integer.class);
        });
        assertThat(visible).isEqualTo(1);

        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> {
            jdbc.queryForObject("select set_config('app.organization_id', ?, true)", String.class, nimbus.toString());
            jdbc.update("""
                insert into ticket (id, organization_id, number, title, description, category, priority, status,
                                    requester_id, created_at, updated_at)
                values (gen_random_uuid(), ?, 99, 'Infiltrado', '', 'OTHER', 'LOW', 'NEW', ?::uuid, now(), now())""",
                acme, userId("client"));
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void laAuditoriaNoSePuedeAlterar() throws Exception {
        created(acme, "client", "Teclado roto");
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> {
            jdbc.queryForObject("select set_config('app.organization_id', ?, true)", String.class, acme.toString());
            jdbc.update("delete from audit_event");
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
    }
}
