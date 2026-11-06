package dev.fuken404.opspilot.shared;

import org.springframework.http.HttpStatus;

/** Un archivo que no se acepta: tipo no permitido (415) o demasiado grande (413). */
public class RejectedFileException extends RuntimeException {

    private final HttpStatus status;

    public RejectedFileException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
