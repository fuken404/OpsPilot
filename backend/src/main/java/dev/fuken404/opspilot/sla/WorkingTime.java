package dev.fuken404.opspilot.sla;

import java.time.Duration;
import java.time.Instant;

/** Cómo corre el reloj de un SLA: todo el tiempo o solo en horario laboral. */
interface WorkingTime {

    /** El instante en que se cumplen {@code minutes} minutos de trabajo contados desde {@code from}. */
    Instant add(Instant from, long minutes);

    /** Minutos de trabajo entre dos instantes; negativo si {@code to} es anterior a {@code from}. */
    long minutesBetween(Instant from, Instant to);

    WorkingTime ALWAYS = new WorkingTime() {
        @Override
        public Instant add(Instant from, long minutes) {
            return from.plus(Duration.ofMinutes(minutes));
        }

        @Override
        public long minutesBetween(Instant from, Instant to) {
            return Duration.between(from, to).toMinutes();
        }
    };
}
