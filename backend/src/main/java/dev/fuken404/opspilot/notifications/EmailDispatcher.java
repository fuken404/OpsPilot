package dev.fuken404.opspilot.notifications;

import dev.fuken404.opspilot.organizations.Organizations;
import dev.fuken404.opspilot.shared.TenantContext;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envía lo que está pendiente en la bandeja de salida. Entrega al menos una vez: si el SMTP aceptó
 * el correo pero la base cae antes de marcarlo, se reenviará. Es el compromiso habitual de un outbox.
 */
@Service
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final EmailOutboxRepository outbox;
    private final Organizations organizations;
    private final TenantContext tenant;
    private final TransactionTemplate transaction;
    private final JavaMailSender mail;
    private final String from;
    private final Clock clock;

    EmailDispatcher(EmailOutboxRepository outbox, Organizations organizations, TenantContext tenant,
                    TransactionTemplate transaction, JavaMailSender mail, @Value("${opspilot.mail.from}") String from,
                    Clock clock) {
        this.outbox = outbox;
        this.organizations = organizations;
        this.tenant = tenant;
        this.transaction = transaction;
        this.mail = mail;
        this.from = from;
        this.clock = clock;
    }

    public record Result(int sent, int failed) {

        Result plus(Result other) {
            return new Result(sent + other.sent, failed + other.failed);
        }
    }

    public Result dispatch() {
        Instant now = clock.instant();
        Result total = new Result(0, 0);
        for (UUID organizationId : organizations.allIds()) {
            try {
                total = total.plus(transaction.execute(tx -> dispatch(organizationId, now)));
            } catch (RuntimeException error) {
                log.error("Falló el envío de correos de la organización {}", organizationId, error);
            }
        }
        if (total.sent() + total.failed() > 0) {
            log.info("Correos: {} enviados, {} con error", total.sent(), total.failed());
        }
        return total;
    }

    private Result dispatch(UUID organizationId, Instant now) {
        tenant.enter(organizationId);
        int sent = 0;
        int failed = 0;
        for (EmailOutboxEntry entry : outbox.lockDue(organizationId, now)) {
            try {
                mail.send(message(entry));
                entry.sent(now);
                sent++;
            } catch (MailException | jakarta.mail.MessagingException error) {
                entry.failed(now, error.getMessage());
                failed++;
            }
        }
        return new Result(sent, failed);
    }

    private MimeMessage message(EmailOutboxEntry entry) throws jakarta.mail.MessagingException {
        MimeMessage message = mail.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(from);
        helper.setTo(entry.getRecipient());
        helper.setSubject(entry.getSubject());
        helper.setText(entry.getBody());
        return message;
    }
}
