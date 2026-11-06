package dev.fuken404.opspilot.realtime;

import dev.fuken404.opspilot.incidents.TicketActivity;
import dev.fuken404.opspilot.sla.SlaAlert;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduce eventos del dominio a mensajes en vivo. Solo después del commit: nadie ve un cambio que
 * luego se deshizo. Los mensajes llevan qué cambió, no el contenido; la pantalla vuelve a pedir lo
 * que necesita a la API, que aplica los permisos de siempre.
 */
@Component
class RealtimeEvents {

    private final Broker broker;
    private final JsonMapper json;

    RealtimeEvents(Broker broker, JsonMapper json) {
        this.broker = broker;
        this.json = json;
    }

    @TransactionalEventListener
    void on(TicketActivity activity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ticket");
        payload.put("organizationId", activity.organizationId());
        payload.put("ticketId", activity.ticketId());
        payload.put("action", activity.action());
        payload.put("status", activity.status());
        payload.put("actorId", activity.actorId());
        payload.put("at", activity.occurredAt());
        OrgMessage.Audience audience = activity.isInternal() ? OrgMessage.Audience.STAFF
            : OrgMessage.Audience.STAFF_AND_USER;
        broker.publish(new OrgMessage(activity.organizationId(), audience, activity.requesterId(),
            json.writeValueAsString(payload)));
    }

    @TransactionalEventListener
    void on(SlaAlert alert) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "sla");
        payload.put("organizationId", alert.organizationId());
        payload.put("ticketId", alert.ticketId());
        payload.put("kind", alert.kind());
        payload.put("level", alert.level());
        payload.put("at", alert.occurredAt());
        broker.publish(new OrgMessage(alert.organizationId(), OrgMessage.Audience.STAFF, null,
            json.writeValueAsString(payload)));
    }
}
