package dev.fuken404.opspilot;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reloj que la prueba adelanta a mano. Se registra con {@code @Import(MutableClock.Config.class)}. */
public class MutableClock extends Clock {

    public volatile Instant now = Instant.parse("2026-11-09T14:00:00Z");

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public MutableClock testClock() {
            return new MutableClock();
        }
    }
}
