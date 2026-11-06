package dev.fuken404.opspilot.incidents;

import dev.fuken404.opspilot.shared.RejectedFileException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Lista blanca de tipos. El tipo se decide por el contenido (los primeros bytes), no por lo que
 * diga el navegador, y la extensión debe coincidir. Fuera quedan ejecutables, HTML y SVG, que
 * pueden llevar código.
 */
final class FileTypes {

    private record Kind(String contentType, List<String> extensions, byte[] magic, int offset) {

        boolean matches(byte[] content) {
            if (magic == null) {
                return false;
            }
            return content.length >= offset + magic.length
                && Arrays.equals(content, offset, offset + magic.length, magic, 0, magic.length);
        }
    }

    private static final byte[] ZIP = {'P', 'K', 3, 4};

    private static final List<Kind> BINARY = List.of(
        new Kind("image/png", List.of("png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, 0),
        new Kind("image/jpeg", List.of("jpg", "jpeg"), new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}, 0),
        new Kind("image/gif", List.of("gif"), "GIF8".getBytes(StandardCharsets.US_ASCII), 0),
        new Kind("image/webp", List.of("webp"), "WEBP".getBytes(StandardCharsets.US_ASCII), 8),
        new Kind("application/pdf", List.of("pdf"), "%PDF-".getBytes(StandardCharsets.US_ASCII), 0),
        new Kind("application/vnd.openxmlformats-officedocument.wordprocessingml.document", List.of("docx"), ZIP, 0),
        new Kind("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", List.of("xlsx"), ZIP, 0),
        new Kind("application/zip", List.of("zip"), ZIP, 0));

    private static final List<String> TEXT = List.of("txt", "log", "csv");

    private FileTypes() {
    }

    /** Devuelve el Content-Type que se guardará y con el que se servirá el archivo. */
    static String detect(String filename, byte[] content) {
        String extension = extension(filename);
        for (Kind kind : BINARY) {
            if (kind.extensions().contains(extension)) {
                if (!kind.matches(content)) {
                    throw rejected("El contenido del archivo no corresponde a su extensión.");
                }
                return kind.contentType();
            }
        }
        if (TEXT.contains(extension)) {
            if (!isText(content)) {
                throw rejected("El archivo de texto contiene datos binarios.");
            }
            return extension.equals("csv") ? "text/csv; charset=UTF-8" : "text/plain; charset=UTF-8";
        }
        throw rejected("Tipo de archivo no permitido. Usa imágenes, PDF, documentos de Office, ZIP o texto.");
    }

    private static boolean isText(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException invalid) {
            return false;
        }
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Sin rutas ni caracteres de control; el nombre solo se usa para mostrarlo y al descargar. */
    static String cleanName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw rejected("El archivo no tiene un nombre válido.");
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static RejectedFileException rejected(String message) {
        return new RejectedFileException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }
}
