package com.ecuapp.inventary.reports;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stock, purchase and sales totals for one product in one warehouse, plus inventory value at the period
 * boundaries and the cost of goods sold taken from the valuation ledger (FIFO or moving average).
 */
public record InventoryBalanceReportRow(
        UUID productId,
        String sku,
        String productName,
        String currency,
        BigDecimal openingStock,
        BigDecimal purchasedQuantity,
        BigDecimal purchaseAmount,
        BigDecimal soldQuantity,
        BigDecimal salesAmount,
        BigDecimal closingStock,
        BigDecimal currentStock,
        BigDecimal currentReserved,
        BigDecimal currentAvailable,
        BigDecimal openingValue,
        BigDecimal closingValue,
        BigDecimal costOfSales,
        BigDecimal grossProfit) {
}
