package dev.fuken404.opspilot.sla;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class BusinessCalendarTests {

    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    final BusinessCalendar calendar = new BusinessCalendar(BOGOTA, LocalTime.of(8, 0), LocalTime.of(18, 0));

    static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(BOGOTA).toInstant();
    }

    @Test
    void festivosDe2026() {
        assertThat(ColombianHolidays.of(2026)).containsExactlyInAnyOrder(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 3, 23), LocalDate.of(2026, 4, 2),
            LocalDate.of(2026, 4, 3), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 8),
            LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 7),
            LocalDate.of(2026, 8, 17), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 16),
            LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 25));
    }

    @Test
    void pascua() {
        assertThat(ColombianHolidays.easter(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(ColombianHolidays.easter(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(ColombianHolidays.easter(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    }

    @Test
    void dentroDeLaJornada() {
        assertThat(calendar.add(at("2026-11-09T09:00"), 240)).isEqualTo(at("2026-11-09T13:00"));
    }

    @Test
    void loQueNoCabeHoySigueElSiguienteDiaHabil() {
        // Viernes 17:30 + 1 h → lunes 16 es festivo → martes 17 a las 8:30
        assertThat(calendar.add(at("2026-11-13T17:30"), 60)).isEqualTo(at("2026-11-17T08:30"));
    }

    @Test
    void fueraDeHorarioEmpiezaALaApertura() {
        assertThat(calendar.add(at("2026-11-10T06:00"), 30)).isEqualTo(at("2026-11-10T08:30"));
        assertThat(calendar.add(at("2026-11-10T19:00"), 30)).isEqualTo(at("2026-11-11T08:30"));
        assertThat(calendar.add(at("2026-11-14T10:00"), 0)).isEqualTo(at("2026-11-17T08:00"));
    }

    @Test
    void minutosEntreDosMomentos() {
        assertThat(calendar.minutesBetween(at("2026-11-09T10:00"), at("2026-11-10T10:00"))).isEqualTo(600);
        assertThat(calendar.minutesBetween(at("2026-11-13T17:00"), at("2026-11-17T09:00"))).isEqualTo(120);
        assertThat(calendar.minutesBetween(at("2026-11-14T09:00"), at("2026-11-16T17:00"))).isZero();
        assertThat(calendar.minutesBetween(at("2026-11-10T10:00"), at("2026-11-09T10:00"))).isEqualTo(-600);
    }

    @Test
    void sumarYMedirSonInversos() {
        Instant start = at("2026-12-23T15:20");
        Instant due = calendar.add(start, 1_000);
        assertThat(calendar.minutesBetween(start, due)).isEqualTo(1_000);
    }
}
