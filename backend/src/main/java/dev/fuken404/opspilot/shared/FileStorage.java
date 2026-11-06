package dev.fuken404.opspilot.shared;

import java.io.IOException;
import java.io.InputStream;

/** Dónde viven los archivos adjuntos. Las claves las genera la app, nunca el usuario. */
public interface FileStorage {

    void put(String key, byte[] content, String contentType) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;
}
