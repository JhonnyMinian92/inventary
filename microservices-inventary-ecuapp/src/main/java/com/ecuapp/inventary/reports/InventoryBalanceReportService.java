package com.ecuapp.inventary.reports;

import com.ecuapp.inventary.common.InvalidQueryException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryBalanceReportService {
    private final InventoryBalanceReportRepository repository;

    public InventoryBalanceReportService(InventoryBalanceReportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public InventoryBalanceReport get(LocalDate from, LocalDate to, UUID warehouseId, UUID productId,
                                      Pageable pageable) {
        if (from == null || to == null) throw new InvalidQueryException("Los parámetros from y to son obligatorios.");
        if (warehouseId == null) throw new InvalidQueryException("El parámetro warehouseId es obligatorio.");
        if (from.isAfter(to)) throw new InvalidQueryException("from no puede ser posterior a to.");
        if (to.isAfter(LocalDate.now(ZoneOffset.UTC))) throw new InvalidQueryException("to no puede ser una fecha futura.");
        if (ChronoUnit.DAYS.between(from, to) > 365)
            throw new InvalidQueryException("El rango máximo permitido es de 366 días incluyendo ambas fechas.");

        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Instant generatedAt = Instant.now();
        Instant fromInclusive = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long total = repository.count(warehouseId, productId, fromInclusive, generatedAt);
        long offset = (long) page * size;
        List<InventoryBalanceReportRow> rows = repository.find(warehouseId, productId, fromInclusive,
                toExclusive, generatedAt, size, offset);
        int pages = total == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size);
        return new InventoryBalanceReport(from, to, warehouseId, generatedAt, page, size, total, pages, rows);
    }
}
