package dev.fuken404.opspilot.incidents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.fuken404.opspilot.TestcontainersConfiguration;
import dev.fuken404.opspilot.organizations.Organizations;
import dev.fuken404.opspilot.organizations.Role;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "opspilot.attachments.local-dir=${java.io.tmpdir}/opspilot-adjuntos-${random.uuid}")
@AutoConfigureMockMvc
class AttachmentsApiTests {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

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

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("delete from invitation");
        jdbc.update("delete from membership");
        jdbc.update("delete from organization");
        jdbc.update("delete from app_user");
        acme = organizations.createWithInvitations("Acme", "acme", Map.of(
            "tech@test.dev", Role.TECHNICIAN, "client@test.dev", Role.CUSTOMER));
        nimbus = organizations.createWithInvitations("Nimbus", "nimbus", Map.of("other@test.dev", Role.ADMIN));
        for (String user : List.of("tech", "client", "other")) {
            mvc.perform(get("/api/me").with(as(user))).andExpect(status().isOk());
        }
    }

    String ticket(UUID organization, String user) throws Exception {
        String body = mvc.perform(post("/api/organizations/" + organization + "/tickets").with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Pantalla rota\",\"description\":\"Se ve negra\",\"category\":\"HARDWARE\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    ResultActions upload(UUID organization, String ticket, String user, String filename, byte[] content,
                         boolean internal) throws Exception {
        return mvc.perform(multipart("/api/organizations/" + organization + "/tickets/" + ticket + "/attachments")
            .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
            .param("internal", String.valueOf(internal)).with(as(user)));
    }

    @Test
    void subirYDescargar() throws Exception {
        String ticket = ticket(acme, "client");
        String body = upload(acme, ticket, "client", "../../captura de pantalla.png", PNG, false)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.attachments[0].filename").value("captura de pantalla.png"))
            .andExpect(jsonPath("$.attachments[0].contentType").value("image/png"))
            .andExpect(jsonPath("$.attachments[0].sizeBytes").value(PNG.length))
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.attachments[0].id");

        byte[] downloaded = mvc.perform(get("/api/organizations/" + acme + "/attachments/" + id).with(as("tech")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PNG);
        mvc.perform(get("/api/organizations/" + acme + "/tickets/" + ticket + "/history").with(as("tech")))
            .andExpect(jsonPath("$[1].action").value("ATTACHED"));
    }

    @Test
    void losAdjuntosInternosNoExistenParaElCliente() throws Exception {
        String ticket = ticket(acme, "client");
        String body = upload(acme, ticket, "tech", "diagnostico.log", "error de disco\n".getBytes(StandardCharsets.UTF_8),
            true).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.attachments[0].id");

        mvc.perform(get("/api/organizations/" + acme + "/tickets/" + ticket).with(as("client")))
            .andExpect(jsonPath("$.attachments.length()").value(0));
        mvc.perform(get("/api/organizations/" + acme + "/attachments/" + id).with(as("client")))
            .andExpect(status().isNotFound());
        upload(acme, ticket, "client", "nota.txt", "hola".getBytes(StandardCharsets.UTF_8), true)
            .andExpect(status().isForbidden());
    }

    @Test
    void elTipoSeDecidePorElContenido() throws Exception {
        String ticket = ticket(acme, "client");
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
        upload(acme, ticket, "client", "foto.png", exe, false).andExpect(status().isUnsupportedMediaType());
        upload(acme, ticket, "client", "programa.exe", exe, false).andExpect(status().isUnsupportedMediaType());
        upload(acme, ticket, "client", "pagina.html", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8), false)
            .andExpect(status().isUnsupportedMediaType());
        upload(acme, ticket, "client", "logo.svg", "<svg onload=alert(1)/>".getBytes(StandardCharsets.UTF_8), false)
            .andExpect(status().isUnsupportedMediaType());
        upload(acme, ticket, "client", "datos.txt", new byte[] {'a', 0, 'b'}, false)
            .andExpect(status().isUnsupportedMediaType());
        upload(acme, ticket, "client", "vacio.txt", new byte[0], false).andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/organizations/" + acme + "/tickets/" + ticket).with(as("client")))
            .andExpect(jsonPath("$.attachments.length()").value(0));
    }

    @Test
    void hayUnTamanoMaximo() throws Exception {
        String ticket = ticket(acme, "client");
        byte[] big = Arrays.copyOf(PNG, 4 * 1024 * 1024 + 1);
        upload(acme, ticket, "client", "enorme.png", big, false).andExpect(status().isPayloadTooLarge());
    }

    @Test
    void elAdjuntoDeOtraOrganizacionNoExiste() throws Exception {
        String foreign = ticket(nimbus, "other");
        String body = upload(nimbus, foreign, "other", "secreto.pdf", "%PDF-1.7 ...".getBytes(StandardCharsets.UTF_8),
            false).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.attachments[0].id");
        mvc.perform(get("/api/organizations/" + acme + "/attachments/" + id).with(as("tech")))
            .andExpect(status().isNotFound());
        upload(acme, foreign, "tech", "x.png", PNG, false).andExpect(status().isNotFound());
    }
}
