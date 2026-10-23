package com.ecuapp.inventary.reports;

import java.math.BigDecimal;
import java.util.UUID;

/** Operational stock, purchase and sales totals for one product in one warehouse. */
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
        BigDecimal currentAvailable) {
}
