package com.ecuapp.inventary.reports;

import com.ecuapp.inventary.inventory.ReorderDtos;
import com.ecuapp.inventary.inventory.ReorderService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read-only operational and financial reports. All of them expose costs and require reports:financial. */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('reports:financial')")
public class InventoryBalanceReportController {
    private final InventoryBalanceReportService balance;
    private final OperationalReportService reports;
    private final ReorderService reorder;

    public InventoryBalanceReportController(InventoryBalanceReportService balance, OperationalReportService reports, ReorderService reorder) {
        this.balance = balance;
        this.reports = reports;
        this.reorder = reorder;
    }

    @GetMapping("/inventory-balance")
    public InventoryBalanceReport balance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String zone,
            @PageableDefault(size = 50) Pageable pageable) {
        return balance.get(from, to, warehouseId, productId, zone, pageable);
    }

    @GetMapping("/valuation")
    public ReportDtos.ValuationReport valuation(@RequestParam(required = false) UUID warehouseId,
                                                @RequestParam(required = false) UUID productId,
                                                @RequestParam(defaultValue = "false") boolean includeEmpty,
                                                @PageableDefault(size = 50) Pageable pageable) {
        return reports.valuation(warehouseId, productId, includeEmpty, pageable);
    }

    @GetMapping("/sales")
    public ReportDtos.SalesReport sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) UUID warehouseId,
                                       @RequestParam(required = false) String zone) {
        return reports.sales(from, to, warehouseId, zone);
    }

    @GetMapping("/purchases")
    public ReportDtos.PurchasesReport purchases(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(required = false) UUID warehouseId,
                                                @RequestParam(required = false) UUID supplierId,
                                                @RequestParam(required = false) String zone) {
        return reports.purchases(from, to, warehouseId, supplierId, zone);
    }

    @GetMapping("/kardex")
    public ReportDtos.KardexReport kardex(@RequestParam UUID productId,
                                          @RequestParam UUID warehouseId,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) String zone) {
        return reports.kardex(productId, warehouseId, from, to, zone);
    }

    @GetMapping("/low-stock")
    public List<ReorderDtos.Suggestion> lowStock(@RequestParam(required = false) UUID warehouseId) {
        return reorder.suggestions().stream().filter(s -> warehouseId == null || warehouseId.equals(s.warehouseId())).toList();
    }
}
