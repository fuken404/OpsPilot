package dev.fuken404.opspilot.realtime;

import dev.fuken404.opspilot.shared.SecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
class RealtimeConfig implements WebSocketConfigurer {

    private final RealtimeHandler handler;
    private final SecurityProperties security;

    RealtimeConfig(RealtimeHandler handler, SecurityProperties security) {
        this.handler = handler;
        this.security = security;
    }

    /** Solo se aceptan conexiones desde los orígenes del frontend, igual que CORS en la API. */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").setAllowedOrigins(security.allowedOrigins().toArray(String[]::new));
    }
}
