package com.ecuapp.inventary.reports;

import com.ecuapp.inventary.common.InvalidQueryException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Inclusive calendar-day range resolved in a business time zone: [from 00:00, to+1 00:00). */
public record ReportPeriod(LocalDate from, LocalDate to, ZoneId zone, Instant fromInclusive, Instant toExclusive) {
    public static final int MAX_DAYS = 366;

    public static ReportPeriod of(LocalDate from, LocalDate to, String zone, ZoneId defaultZone) {
        if (from == null || to == null) throw new InvalidQueryException("Los parámetros from y to son obligatorios.");
        ZoneId zoneId = zone(zone, defaultZone);
        if (from.isAfter(to)) throw new InvalidQueryException("from no puede ser posterior a to.");
        if (to.isAfter(LocalDate.now(zoneId))) throw new InvalidQueryException("to no puede ser una fecha futura.");
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS)
            throw new InvalidQueryException("El rango máximo permitido es de " + MAX_DAYS + " días incluyendo ambas fechas.");
        return new ReportPeriod(from, to, zoneId, from.atStartOfDay(zoneId).toInstant(), to.plusDays(1).atStartOfDay(zoneId).toInstant());
    }

    public static ZoneId zone(String zone, ZoneId defaultZone) {
        if (zone == null || zone.isBlank()) return defaultZone;
        try {
            return ZoneId.of(zone.trim());
        } catch (DateTimeException ex) {
            throw new InvalidQueryException("Zona horaria inválida; use un identificador IANA como America/Guayaquil.");
        }
    }
}
