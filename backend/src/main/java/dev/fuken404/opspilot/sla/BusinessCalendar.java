package dev.fuken404.opspilot.sla;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Horario laboral de lunes a viernes, sin festivos de Colombia, en la zona horaria de la operación. */
final class BusinessCalendar implements WorkingTime {

    private final ZoneId zone;
    private final LocalTime opens;
    private final LocalTime closes;

    BusinessCalendar(ZoneId zone, LocalTime opens, LocalTime closes) {
        if (!opens.isBefore(closes)) {
            throw new IllegalArgumentException("La jornada debe abrir antes de cerrar.");
        }
        this.zone = zone;
        this.opens = opens;
        this.closes = closes;
    }

    boolean isWorkday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
            && !ColombianHolidays.isHoliday(date);
    }

    /** Recorre la jornada de cada día hábil hasta gastar los minutos. Fuera de horario, espera a la apertura. */
    @Override
    public Instant add(Instant from, long minutes) {
        long seconds = Math.max(0, minutes) * 60;
        ZonedDateTime cursor = from.atZone(zone);
        LocalDate day = cursor.toLocalDate();
        while (true) {
            if (isWorkday(day)) {
                ZonedDateTime open = day.atTime(opens).atZone(zone);
                ZonedDateTime close = day.atTime(closes).atZone(zone);
                ZonedDateTime begin = cursor.isAfter(open) ? cursor : open;
                if (begin.isBefore(close)) {
                    long available = Duration.between(begin, close).getSeconds();
                    if (seconds <= available) {
                        return begin.plusSeconds(seconds).toInstant();
                    }
                    seconds -= available;
                }
            }
            day = day.plusDays(1);
            cursor = day.atStartOfDay(zone);
        }
    }

    @Override
    public long minutesBetween(Instant from, Instant to) {
        if (to.isBefore(from)) {
            return -minutesBetween(to, from);
        }
        long seconds = 0;
        for (LocalDate day = from.atZone(zone).toLocalDate(); !day.isAfter(to.atZone(zone).toLocalDate());
             day = day.plusDays(1)) {
            if (!isWorkday(day)) {
                continue;
            }
            Instant open = day.atTime(opens).atZone(zone).toInstant();
            Instant close = day.atTime(closes).atZone(zone).toInstant();
            Instant begin = from.isAfter(open) ? from : open;
            Instant end = to.isBefore(close) ? to : close;
            if (begin.isBefore(end)) {
                seconds += Duration.between(begin, end).getSeconds();
            }
        }
        return seconds / 60;
    }
}
