package dev.fuken404.opspilot.shared;

/** Recurso inexistente o de otra organización: no se distingue, para no revelar qué existe. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
