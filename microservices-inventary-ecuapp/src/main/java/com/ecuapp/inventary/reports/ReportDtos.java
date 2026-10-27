package com.ecuapp.inventary.reports;

import com.ecuapp.inventary.inventory.CostingMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() {}

    public record ValuationRow(UUID productId, String sku, String productName, UUID warehouseId, String warehouseCode, String currency,
                               BigDecimal onHand, BigDecimal valuedQuantity, BigDecimal averageCost, BigDecimal totalValue, boolean reconciled) {}
    public record CurrencyTotal(String currency, BigDecimal onHand, BigDecimal totalValue) {}
    public record ValuationReport(CostingMethod method, Instant generatedAt, int page, int size, long totalElements, int totalPages,
                                  List<CurrencyTotal> totals, List<ValuationRow> items) {}

    public record SalesSummary(long salesCount, long cancelledCount, BigDecimal grossSales, BigDecimal cancellations, BigDecimal returns,
                               BigDecimal netSales, BigDecimal costOfSales, BigDecimal grossProfit) {}
    public record SalesProductRow(UUID productId, String sku, String productName, String currency, BigDecimal netQuantity,
                                  BigDecimal netSales, BigDecimal costOfSales, BigDecimal grossProfit) {}
    public record DailySales(LocalDate date, long salesCount, BigDecimal grossSales) {}
    public record SalesReport(LocalDate from, LocalDate to, String zone, UUID warehouseId, Instant generatedAt, SalesSummary summary,
                              List<DailySales> daily, List<SalesProductRow> products, long totalProducts, boolean productsTruncated) {}

    public record PurchasesSummary(long receiptsCount, long returnsCount, BigDecimal receivedAmount, BigDecimal returnedAmount, BigDecimal netAmount) {}
    public record PurchasesRow(UUID supplierId, String supplierName, UUID productId, String sku, String productName, String currency,
                               BigDecimal receivedQuantity, BigDecimal receivedAmount, BigDecimal returnedQuantity, BigDecimal returnedAmount) {}
    public record PurchasesReport(LocalDate from, LocalDate to, String zone, UUID warehouseId, UUID supplierId, Instant generatedAt,
                                  PurchasesSummary summary, List<PurchasesRow> items, long totalItems, boolean itemsTruncated) {}

    public record KardexLine(UUID movementId, Instant occurredAt, String movementType, String referenceType, UUID referenceId, String notes,
                             BigDecimal quantityIn, BigDecimal quantityOut, BigDecimal valueIn, BigDecimal valueOut,
                             BigDecimal balanceQuantity, BigDecimal balanceValue, String createdBy) {}
    public record KardexReport(UUID productId, String sku, String productName, UUID warehouseId, String warehouseCode, LocalDate from, LocalDate to,
                               String zone, BigDecimal openingQuantity, BigDecimal openingValue, BigDecimal closingQuantity, BigDecimal closingValue,
                               List<KardexLine> movements) {}
}
