package dev.fuken404.opspilot.shared;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El reloj se inyecta para que el SLA y las pruebas controlen el tiempo. */
@Configuration
class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
