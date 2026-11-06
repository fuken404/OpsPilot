package dev.fuken404.opspilot.organizations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.fuken404.opspilot.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationsApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    Organizations organizations;

    @Autowired
    JdbcTemplate jdbc;

    UUID acme;
    UUID nimbus;

    static RequestPostProcessor as(String user) {
        return jwt().jwt(token -> token.subject("sub-" + user).claim("email", user + "@test.dev")
            .claim("name", user).claim("email_verified", true));
    }

    static RequestPostProcessor unverified(String user) {
        return jwt().jwt(token -> token.subject("sub-" + user).claim("email", user + "@test.dev")
            .claim("email_verified", false));
    }

    @BeforeEach
    void seed() {
        jdbc.update("delete from invitation");
        jdbc.update("delete from membership");
        jdbc.update("delete from organization");
        jdbc.update("delete from app_user");
        acme = organizations.createWithInvitations("Acme", "acme", Map.of(
            "admin@test.dev", Role.ADMIN, "tech@test.dev", Role.TECHNICIAN, "client@test.dev", Role.CUSTOMER));
        nimbus = organizations.createWithInvitations("Nimbus", "nimbus", Map.of("other@test.dev", Role.ADMIN));
    }

    String body(Object... pairs) {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            json.append(i == 0 ? "" : ",").append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
        }
        return json.append('}').toString();
    }

    @Test
    void sinTokenNoHayAcceso() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void elPrimerIngresoAceptaLasInvitaciones() throws Exception {
        mvc.perform(get("/api/me").with(as("admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberships.length()").value(1))
            .andExpect(jsonPath("$.memberships[0].slug").value("acme"))
            .andExpect(jsonPath("$.memberships[0].role").value("ADMIN"));
    }

    @Test
    void sinCorreoVerificadoNoSeHeredaLaInvitacion() throws Exception {
        mvc.perform(get("/api/me").with(unverified("admin")))
            .andExpect(jsonPath("$.memberships.length()").value(0));
    }

    @Test
    void otraOrganizacionNoExisteParaQuienNoEsMiembro() throws Exception {
        mvc.perform(get("/api/me").with(as("admin")));
        mvc.perform(get("/api/organizations/{id}/members", nimbus).with(as("admin")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/organizations/{id}/members", UUID.randomUUID()).with(as("admin")))
            .andExpect(status().isNotFound());
    }

    @Test
    void elClienteNoVeLosMiembros() throws Exception {
        mvc.perform(get("/api/me").with(as("client")));
        mvc.perform(get("/api/organizations/{id}/members", acme).with(as("client")))
            .andExpect(status().isForbidden());
    }

    @Test
    void elTecnicoVePeroNoAgrega() throws Exception {
        mvc.perform(get("/api/me").with(as("tech")));
        mvc.perform(get("/api/organizations/{id}/members", acme).with(as("tech"))).andExpect(status().isOk());
        mvc.perform(post("/api/organizations/{id}/members", acme).with(as("tech"))
                .contentType(MediaType.APPLICATION_JSON).content(body("email", "x@test.dev", "role", "CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorAgregaEInvita() throws Exception {
        mvc.perform(get("/api/me").with(as("admin")));
        mvc.perform(get("/api/me").with(as("newbie")));
        mvc.perform(post("/api/organizations/{id}/members", acme).with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(body("email", "NEWBIE@test.dev", "role", "TECHNICIAN")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pending").value(false));
        mvc.perform(post("/api/organizations/{id}/members", acme).with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(body("email", "newbie@test.dev", "role", "TECHNICIAN")))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/organizations/{id}/members", acme).with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(body("email", "later@test.dev", "role", "CUSTOMER")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pending").value(true));
        mvc.perform(post("/api/organizations/{id}/members", acme).with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(body("email", "no-es-correo", "role", "CUSTOMER")))
            .andExpect(status().isBadRequest());
    }

    String membershipOf(String user) throws Exception {
        String json = mvc.perform(get("/api/organizations/{id}/members", acme).with(as("admin")))
            .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, "$[?(@.email == '" + user + "@test.dev')].membershipId");
        return ids.getFirst();
    }

    @Test
    void elUltimoAdministradorNoPuedeIrse() throws Exception {
        mvc.perform(get("/api/me").with(as("admin")));
        String admin = membershipOf("admin");
        mvc.perform(patch("/api/organizations/{id}/members/{m}", acme, admin).with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(body("role", "CUSTOMER")))
            .andExpect(status().isConflict());
        mvc.perform(delete("/api/organizations/{id}/members/{m}", acme, admin).with(as("admin")))
            .andExpect(status().isConflict());
    }

    /**
     * Dos administradores se quitan el rol al mismo tiempo. Sin el bloqueo, ambos verían que queda
     * otro administrador y la organización se quedaría sin ninguno.
     */
    @Test
    void cambiosSimultaneosNoDejanLaOrganizacionSinAdministradores() throws Exception {
        mvc.perform(get("/api/me").with(as("admin")));
        mvc.perform(get("/api/me").with(as("tech")));
        String tech = membershipOf("tech");
        mvc.perform(patch("/api/organizations/{id}/members/{m}", acme, tech).with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(body("role", "ADMIN")))
            .andExpect(status().isNoContent());
        String admin = membershipOf("admin");

        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> demotions = new ArrayList<>();
        for (var pair : List.of(List.of("admin", admin), List.of("tech", tech))) {
            demotions.add(() -> {
                start.await();
                return mvc.perform(patch("/api/organizations/{id}/members/{m}", acme, pair.get(1)).with(as(pair.get(0)))
                        .contentType(MediaType.APPLICATION_JSON).content(body("role", "CUSTOMER")))
                    .andReturn().getResponse().getStatus();
            });
        }
        List<Future<Integer>> results = demotions.stream().map(pool::submit).toList();
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(204, 409);
        Integer admins = jdbc.queryForObject(
            "select count(*) from membership where organization_id = ? and role = 'ADMIN'", Integer.class, acme);
        assertThat(admins).isEqualTo(1);
    }
}
