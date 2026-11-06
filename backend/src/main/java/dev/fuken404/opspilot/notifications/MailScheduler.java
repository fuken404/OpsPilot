package dev.fuken404.opspilot.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** En local el despachador corre dentro de la app; en producción lo dispara el cron. */
@Configuration
@ConditionalOnProperty("opspilot.mail.dispatcher-enabled")
class MailScheduler {

    private final EmailDispatcher dispatcher;

    MailScheduler(EmailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    void dispatch() {
        dispatcher.dispatch();
    }
}
