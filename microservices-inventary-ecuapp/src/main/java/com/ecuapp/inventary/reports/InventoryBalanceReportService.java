package com.ecuapp.inventary.reports;

import com.ecuapp.inventary.auth.WarehouseScopeService;
import com.ecuapp.inventary.common.InvalidQueryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryBalanceReportService {
    private final InventoryBalanceReportRepository repository;
    private final ZoneId defaultZone;
    private final WarehouseScopeService scopes;

    public InventoryBalanceReportService(InventoryBalanceReportRepository repository,
                                         WarehouseScopeService scopes,
                                         @Value("${app.reports.time-zone:America/Guayaquil}") String defaultZone) {
        this.repository = repository;
        this.scopes = scopes;
        this.defaultZone = ZoneId.of(defaultZone);
    }

    @Transactional(readOnly = true)
    public InventoryBalanceReport get(LocalDate from, LocalDate to, UUID warehouseId, UUID productId, String zone, Pageable pageable,String actor) {
        if (warehouseId == null) throw new InvalidQueryException("El parámetro warehouseId es obligatorio.");
        scopes.requireWarehouseAccess(actor,warehouseId);
        scopes.requireFinancialDataAccess(actor);
        ReportPeriod period = ReportPeriod.of(from, to, zone, defaultZone);
        if (pageable.getPageNumber() < 0) throw new InvalidQueryException("El número de página no puede ser negativo.");
        int page = pageable.getPageNumber();
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Instant generatedAt = Instant.now();
        long total = repository.count(warehouseId, productId, period.fromInclusive(), generatedAt);
        long offset = (long) page * size;
        List<InventoryBalanceReportRow> rows = repository.find(warehouseId, productId, period.fromInclusive(),
                period.toExclusive(), generatedAt, size, offset);
        int pages = total == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size);
        return new InventoryBalanceReport(period.from(), period.to(), period.zone().getId(), warehouseId, generatedAt, page, size, total, pages, rows);
    }
}
