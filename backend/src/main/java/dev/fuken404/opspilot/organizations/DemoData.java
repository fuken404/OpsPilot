package dev.fuken404.opspilot.organizations;

import java.util.Map;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Organizaciones de demostración, con los usuarios definidos en el realm de Keycloak. */
@Configuration
@Profile("demo")
class DemoData {

    @Bean
    ApplicationRunner demoOrganizations(Organizations organizations) {
        return args -> {
            organizations.createWithInvitations("Acme Logística", "acme", Map.of(
                "ana.admin@opspilot.demo", Role.ADMIN,
                "tomas.tecnico@opspilot.demo", Role.TECHNICIAN,
                "carla.cliente@opspilot.demo", Role.CUSTOMER));
            organizations.createWithInvitations("Nimbus Salud", "nimbus", Map.of(
                "ana.admin@opspilot.demo", Role.ADMIN,
                "lucia.cliente@opspilot.demo", Role.CUSTOMER));
        };
    }
}
