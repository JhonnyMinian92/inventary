package com.ecuapp.inventary.reports;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class InventoryBalanceReportController {
    private final InventoryBalanceReportService reports;

    public InventoryBalanceReportController(InventoryBalanceReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/inventory-balance")
    @PreAuthorize("hasAuthority('reports:financial')")
    public InventoryBalanceReport balance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) UUID productId,
            @PageableDefault(size = 50) Pageable pageable) {
        return reports.get(from, to, warehouseId, productId, pageable);
    }
}
