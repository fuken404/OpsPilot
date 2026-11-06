package dev.fuken404.opspilot;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	public PostgreSQLContainer postgresContainer() {
		// Mismas credenciales que en local: Flyway migra con este usuario y la app cambia a opspilot_app.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
			.withDatabaseName("opspilot").withUsername("opspilot").withPassword("opspilot");
	}

}
