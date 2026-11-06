package dev.fuken404.opspilot.realtime;

import java.util.UUID;

/**
 * Un evento para las conexiones de una organización. {@code STAFF} llega solo al equipo;
 * {@code STAFF_AND_USER} además a una persona concreta (quien reportó el ticket).
 */
record OrgMessage(UUID organizationId, Audience audience, UUID userId, String payload) {

    enum Audience {
        STAFF, STAFF_AND_USER
    }
}
