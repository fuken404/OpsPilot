package dev.fuken404.opspilot.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
class BrokerConfig {

    static final String CHANNEL = "opspilot:realtime";

    private static final Logger log = LoggerFactory.getLogger(BrokerConfig.class);

    /** Una sola instancia: entrega directa. */
    @Bean
    @ConditionalOnProperty(name = "opspilot.realtime.broker", havingValue = "memory", matchIfMissing = true)
    Broker memoryBroker(RealtimeHandler handler) {
        return handler::deliver;
    }

    /**
     * Varias instancias: cada evento se publica en un canal de Redis y todas las instancias (incluida
     * la que lo publicó) lo reciben y lo entregan a sus propias conexiones.
     */
    @Bean
    @ConditionalOnProperty(name = "opspilot.realtime.broker", havingValue = "redis")
    Broker redisBroker(StringRedisTemplate redis, JsonMapper json) {
        return message -> {
            try {
                redis.convertAndSend(CHANNEL, json.writeValueAsString(message));
            } catch (RuntimeException error) {
                // El cambio ya está guardado: perder el aviso en vivo no debe romper la petición.
                log.warn("No se pudo publicar el evento en Redis: {}", error.getMessage());
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "opspilot.realtime.broker", havingValue = "redis")
    RedisMessageListenerContainer realtimeListener(RedisConnectionFactory connections, RealtimeHandler handler,
                                                   JsonMapper json) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connections);
        container.addMessageListener(
            (message, pattern) -> handler.deliver(json.readValue(message.getBody(), OrgMessage.class)),
            new ChannelTopic(CHANNEL));
        return container;
    }
}
