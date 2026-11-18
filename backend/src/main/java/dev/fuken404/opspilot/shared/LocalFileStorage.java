package dev.fuken404.opspilot.shared;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Disco local: desarrollo o un servidor con volumen persistente. */
@Component
@ConditionalOnProperty(name = "opspilot.attachments.storage", havingValue = "local", matchIfMissing = true)
class LocalFileStorage implements FileStorage {

    private final Path root;

    LocalFileStorage(@Value("${opspilot.attachments.local-dir}") String root) throws IOException {
        this.root = Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public void put(String key, byte[] content, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        // Se escribe aparte y se mueve: nadie lee un archivo a medio escribir.
        Path temporary = Files.createTempFile(target.getParent(), ".subiendo-", ".tmp");
        try {
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Clave de archivo inválida");
        }
        return path;
    }
}
