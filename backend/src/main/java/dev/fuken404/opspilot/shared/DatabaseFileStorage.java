package dev.fuken404.opspilot.shared;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adjuntos dentro de PostgreSQL. Para plataformas sin disco persistente: el archivo se guarda en la
 * misma transacción que su registro, así que si algo falla no queda ni el uno ni el otro.
 */
@Component
@ConditionalOnProperty(name = "opspilot.attachments.storage", havingValue = "database")
class DatabaseFileStorage implements FileStorage {

    private final JdbcTemplate jdbc;

    DatabaseFileStorage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        jdbc.update("insert into stored_file (key, content, content_type) values (?, ?, ?)", key, content, contentType);
    }

    @Override
    public InputStream open(String key) throws FileNotFoundException {
        List<byte[]> found = jdbc.query("select content from stored_file where key = ?", (rs, i) -> rs.getBytes(1), key);
        if (found.isEmpty()) {
            throw new FileNotFoundException(key);
        }
        return new ByteArrayInputStream(found.getFirst());
    }

    @Override
    public void delete(String key) {
        jdbc.update("delete from stored_file where key = ?", key);
    }
}
