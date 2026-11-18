package dev.fuken404.opspilot.incidents;

import dev.fuken404.opspilot.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/** Las mismas pruebas de adjuntos, guardando el contenido en PostgreSQL (como en Vercel). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "opspilot.attachments.storage=database")
@AutoConfigureMockMvc
class DatabaseAttachmentsApiTests extends AttachmentsApiTests {
}
