package dev.fuken404.opspilot.sla;

public enum SlaKind {
    /** Primera respuesta del equipo: un comentario público o un cambio de estado. */
    RESPONSE,
    /** Hasta que el ticket queda resuelto. Se pausa mientras se espera al cliente. */
    RESOLUTION
}
