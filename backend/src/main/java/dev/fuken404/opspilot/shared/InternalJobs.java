package dev.fuken404.opspilot.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Puerta de los endpoints que llama un cron, no una persona. Se protegen con un secreto propio,
 * comparado en tiempo constante. Sin secreto configurado, responden 404 como si no existieran.
 */
@Component
public class InternalJobs {

    private final byte[] token;

    InternalJobs(@Value("${opspilot.internal-token:}") String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    public <T> ResponseEntity<T> run(String provided, Supplier<T> job) {
        if (token.length == 0) {
            return ResponseEntity.notFound().build();
        }
        if (provided == null || !MessageDigest.isEqual(token, provided.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(job.get());
    }
}
