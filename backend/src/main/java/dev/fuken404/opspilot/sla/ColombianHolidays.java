package dev.fuken404.opspilot.sla;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Festivos de Colombia. Algunos son fijos, otros se corren al lunes siguiente (Ley 51 de 1983,
 * "Ley Emiliani") y otros dependen de la Pascua.
 */
final class ColombianHolidays {

    private static final List<MonthDay> FIXED = List.of(
        MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(7, 20), MonthDay.of(8, 7), MonthDay.of(12, 8),
        MonthDay.of(12, 25));

    private static final List<MonthDay> MOVED_TO_MONDAY = List.of(
        MonthDay.of(1, 6), MonthDay.of(3, 19), MonthDay.of(6, 29), MonthDay.of(8, 15), MonthDay.of(10, 12),
        MonthDay.of(11, 1), MonthDay.of(11, 11));

    private static final Map<Integer, Set<LocalDate>> BY_YEAR = new ConcurrentHashMap<>();

    private ColombianHolidays() {
    }

    static boolean isHoliday(LocalDate date) {
        return of(date.getYear()).contains(date);
    }

    static Set<LocalDate> of(int year) {
        return BY_YEAR.computeIfAbsent(year, ColombianHolidays::compute);
    }

    private static Set<LocalDate> compute(int year) {
        Set<LocalDate> days = new TreeSet<>();
        FIXED.forEach(day -> days.add(day.atYear(year)));
        MOVED_TO_MONDAY.forEach(day -> days.add(nextMonday(day.atYear(year))));
        LocalDate easter = easter(year);
        days.add(easter.minusDays(3));               // Jueves Santo
        days.add(easter.minusDays(2));               // Viernes Santo
        days.add(nextMonday(easter.plusDays(39)));   // Ascensión del Señor
        days.add(nextMonday(easter.plusDays(60)));   // Corpus Christi
        days.add(nextMonday(easter.plusDays(68)));   // Sagrado Corazón
        return Set.copyOf(days);
    }

    private static LocalDate nextMonday(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** Domingo de Pascua, algoritmo gregoriano anónimo (Meeus/Jones/Butcher). */
    static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
