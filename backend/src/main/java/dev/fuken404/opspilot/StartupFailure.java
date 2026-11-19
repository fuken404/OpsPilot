package dev.fuken404.opspilot;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Si la app no logra arrancar en un contenedor (Vercel, donde los logs se ven poco tiempo), deja
 * un servidor mínimo que responde 503 con la causa, sin URLs, hosts ni secretos. Así el problema
 * se diagnostica con una petición. Tras unos minutos termina con error, como lo haría la app.
 */
final class StartupFailure {

    private StartupFailure() {
    }

    static void serve(Throwable error) {
        String port = System.getenv("PORT");
        if (port == null || port.isBlank()) {
            return;
        }
        byte[] body = ("{\"error\":\"La API no pudo arrancar\",\"causa\":\"" + escape(describe(error)) + "\"}")
            .getBytes(StandardCharsets.UTF_8);
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(Integer.parseInt(port)), 0);
            server.createContext("/", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(503, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            TimeUnit.MINUTES.sleep(5);
        } catch (IOException | RuntimeException | InterruptedException ignored) {
            // si ni esto funciona, se sale con el error original
        }
        System.exit(1);
    }

    /** Las causas encadenadas, de la más profunda a la más general, sin datos sensibles. */
    static String describe(Throwable error) {
        List<String> causes = new ArrayList<>();
        for (Throwable t = error; t != null && causes.size() < 4; t = t.getCause() == t ? null : t.getCause()) {
            causes.addFirst(t.getClass().getSimpleName() + ": " + mask(String.valueOf(t.getMessage())));
        }
        return String.join(" <- ", causes);
    }

    static String mask(String message) {
        String masked = message
            .replaceAll("[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s'\"]+", "<url>")
            .replaceAll("(?i)(password|pwd|secret|token)=[^\\s&;,]+", "$1=***")
            .replaceAll("[\\w.-]+\\.(tech|io|com|dev|net|org|cloud)(:\\d+)?", "<host>");
        return masked.length() > 400 ? masked.substring(0, 400) + "…" : masked;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\r\\n\\t]+", " ");
    }
}
