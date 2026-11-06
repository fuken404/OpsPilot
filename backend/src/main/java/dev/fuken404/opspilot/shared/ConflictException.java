package dev.fuken404.opspilot.shared;

/** La operación choca con el estado actual: duplicados, cambios concurrentes o reglas de negocio. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
