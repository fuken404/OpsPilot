package dev.fuken404.opspilot.shared;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Acepta la URL de base de datos en el formato que entregan Neon y la integración de Vercel
 * ({@code postgresql://usuario:clave@host/base?sslmode=require}) además del JDBC de siempre.
 * Prefiere la conexión directa: la app cambia de rol por conexión para Row Level Security, y un
 * pooler en modo transacción no conserva ese estado.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    record Connection(String jdbcUrl, String username, String password) {
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String explicit = environment.getProperty("DATABASE_URL");
        if (explicit != null && explicit.startsWith("jdbc:")) {
            return;
        }
        Optional.ofNullable(environment.getProperty("DATABASE_URL_UNPOOLED"))
            .or(() -> Optional.ofNullable(explicit))
            .flatMap(DatabaseUrlEnvironmentPostProcessor::parse)
            .ifPresent(connection -> {
                Map<String, Object> properties = new HashMap<>();
                properties.put("spring.datasource.url", connection.jdbcUrl());
                if (connection.username() != null) {
                    properties.put("spring.datasource.username", connection.username());
                }
                if (connection.password() != null) {
                    properties.put("spring.datasource.password", connection.password());
                }
                environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", properties));
            });
    }

    static Optional<Connection> parse(String url) {
        if (url == null || !url.matches("^postgres(ql)?://.+")) {
            return Optional.empty();
        }
        URI uri = URI.create(url.replaceFirst("^postgres(ql)?://", "http://"));
        String host = uri.getHost().replace("-pooler.", ".");
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        // Solo sslmode: el resto de parámetros de libpq no los entiende el driver JDBC.
        String sslmode = Arrays.stream(query.split("&"))
            .filter(p -> p.startsWith("sslmode="))
            .findFirst().orElse("sslmode=require");
        String jdbc = "jdbc:postgresql://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
            + uri.getRawPath() + "?" + sslmode;
        String user = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            user = decode(parts[0]);
            password = parts.length > 1 ? decode(parts[1]) : null;
        }
        return Optional.of(new Connection(jdbc, user, password));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
