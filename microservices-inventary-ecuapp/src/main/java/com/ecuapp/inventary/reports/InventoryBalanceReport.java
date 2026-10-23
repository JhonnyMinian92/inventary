package com.ecuapp.inventary.reports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InventoryBalanceReport(
        LocalDate from,
        LocalDate to,
        String zone,
        UUID warehouseId,
        Instant generatedAt,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<InventoryBalanceReportRow> items) {
}
