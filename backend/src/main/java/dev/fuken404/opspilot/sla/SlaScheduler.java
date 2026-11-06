package dev.fuken404.opspilot.sla;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Barrido dentro de la app, para correr en local. En producción lo dispara un cron externo. */
@Configuration
@EnableScheduling
@ConditionalOnProperty("opspilot.sla.scheduler-enabled")
class SlaScheduler {

    private final SlaSweeper sweeper;

    SlaScheduler(SlaSweeper sweeper) {
        this.sweeper = sweeper;
    }

    @Scheduled(fixedDelayString = "${opspilot.sla.sweep-interval}", initialDelayString = "PT20S")
    void sweep() {
        sweeper.sweep();
    }
}
