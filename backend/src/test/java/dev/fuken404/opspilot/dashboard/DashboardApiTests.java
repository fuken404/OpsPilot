package dev.fuken404.opspilot.dashboard;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class DashboardApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    Organizations organizations;

    @Autowired
    JdbcTemplate jdbc;

    UUID acme;

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
            "admin@test.dev", Role.ADMIN, "tech@test.dev", Role.TECHNICIAN, "client@test.dev", Role.CUSTOMER));
        for (String user : List.of("admin", "tech", "client")) {
            mvc.perform(get("/api/me").with(as(user))).andExpect(status().isOk());
        }
    }

    String create(String user, String priority) throws Exception {
        String body = mvc.perform(post("/api/organizations/" + acme + "/tickets").with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Falla\",\"description\":\"Detalle\",\"category\":\"OTHER\",\"priority\":\""
                    + priority + "\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    void change(String ticket, String path, String json) throws Exception {
        mvc.perform(put("/api/organizations/" + acme + "/tickets/" + ticket + path).with(as("tech"))
            .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    @Test
    void resumeLaOperacionDeLaOrganizacion() throws Exception {
        String solved = create("client", "LOW");
        create("client", "LOW");
        create("client", "LOW");
        create("admin", "CRITICAL");
        String tech = jdbc.queryForObject("select id::text from app_user where email = 'tech@test.dev'", String.class);
        change(solved, "/assignee", "{\"version\":0,\"assigneeId\":\"" + tech + "\"}");
        change(solved, "/status", "{\"version\":1,\"status\":\"IN_PROGRESS\"}");
        change(solved, "/status", "{\"version\":2,\"status\":\"RESOLVED\"}");

        mvc.perform(get("/api/organizations/" + acme + "/dashboard").with(as("admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.open").value(4))
            .andExpect(jsonPath("$.unassigned").value(3))
            .andExpect(jsonPath("$.byStatus.NEW").value(3))
            .andExpect(jsonPath("$.byStatus.RESOLVED").value(1))
            .andExpect(jsonPath("$.byStatus.WAITING_CUSTOMER").value(0))
            .andExpect(jsonPath("$.byPriority.CRITICAL").value(1))
            .andExpect(jsonPath("$.byPriority.MEDIUM").value(3)) // el cliente siempre crea con prioridad media
            .andExpect(jsonPath("$.sla.running").value(3))
            .andExpect(jsonPath("$.sla.breached").value(0))
            .andExpect(jsonPath("$.compliance.response").value(100.0))
            .andExpect(jsonPath("$.compliance.responseTotal").value(2))
            .andExpect(jsonPath("$.compliance.resolutionTotal").value(1))
            .andExpect(jsonPath("$.workload[0].displayName").value("tech"))
            .andExpect(jsonPath("$.workload[0].open").value(1))
            .andExpect(jsonPath("$.workload.length()").value(2))
            .andExpect(jsonPath("$.trend.length()").value(14))
            .andExpect(jsonPath("$.trend[13].created").value(4))
            .andExpect(jsonPath("$.trend[13].resolved").value(1));
    }

    @Test
    void elClienteNoVeElTablero() throws Exception {
        mvc.perform(get("/api/organizations/" + acme + "/dashboard").with(as("client"))).andExpect(status().isForbidden());
    }
}
