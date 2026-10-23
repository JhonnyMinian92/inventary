package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Reproduces the scenario requested in PENDIENTES.md: receive 10, sell everything, receive 15 more of the same product,
 * then a customer returns 2 units. Every figure is asserted against the consolidated report, the sales and purchase
 * reports, the kardex and the valuation report (moving average policy).
 */
class ReportsApiTest extends ApiTestSupport {
    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

    @Test
    void soldOutAndRestockedProductProducesConsistentReports() {
        String supplierId = supplier().get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        String productId = product("3.00", "10.00").get("id").asText();

        JsonNode first = approvedOrder(supplierId, warehouseId, List.of(Map.of("productId", productId, "quantity", 10, "unitCost", 4)));
        receive(first.get("id").asText(), List.of(line("purchaseOrderLineId", first.get("items").get(0).get("id").asText(), "10")));
        JsonNode sale = sale(token("VENDEDOR"), warehouseId, productId, "10");
        assertAmount("0", onHand(productId, warehouseId));
        JsonNode second = approvedOrder(supplierId, warehouseId, List.of(Map.of("productId", productId, "quantity", 15, "unitCost", 5)));
        receive(second.get("id").asText(), List.of(line("purchaseOrderLineId", second.get("items").get(0).get("id").asText(), "15")));
        assertAmount("15", onHand(productId, warehouseId));
        httpPost(token("VENDEDOR"), "/api/v1/sales-returns", Map.of("sourceSaleId", sale.get("id").asText(), "reason", "Devolución parcial",
            "items", List.of(Map.of("saleItemId", sale.get("items").get(0).get("id").asText(), "quantity", 2, "disposition", "RESTOCK"))), 201, "Idempotency-Key", key());

        LocalDate today = LocalDate.now(ZONE);
        JsonNode report = httpGet(admin(), "/api/v1/reports/inventory-balance?from=" + today + "&to=" + today + "&warehouseId=" + warehouseId, 200);
        assertEquals("America/Guayaquil", report.get("zone").asText());
        assertEquals(1, report.get("totalElements").asInt());
        JsonNode row = report.get("items").get(0);
        assertAmount("0", row.get("openingStock"));
        assertAmount("25", row.get("purchasedQuantity"));
        assertAmount("115.00", row.get("purchaseAmount"));
        assertAmount("8", row.get("soldQuantity"));
        assertAmount("80.00", row.get("salesAmount"));
        assertAmount("17", row.get("closingStock"));
        assertAmount("17", row.get("currentStock"));
        assertAmount("0.00", row.get("openingValue"));
        // 15 @ 5.00 + 2 returned at the original sale cost of 4.00
        assertAmount("83.00", row.get("closingValue"));
        assertAmount("32.00", row.get("costOfSales"));
        assertAmount("48.00", row.get("grossProfit"));

        // Yesterday the product did not exist yet: everything that happened today is excluded from the closing stock.
        JsonNode yesterday = httpGet(admin(), "/api/v1/reports/inventory-balance?from=" + today.minusDays(1) + "&to=" + today.minusDays(1)
            + "&warehouseId=" + warehouseId + "&productId=" + productId, 200).get("items").get(0);
        assertAmount("0", yesterday.get("closingStock"));
        assertAmount("0", yesterday.get("purchasedQuantity"));
        assertAmount("17", yesterday.get("currentStock"));

        JsonNode sales = httpGet(admin(), "/api/v1/reports/sales?from=" + today + "&to=" + today + "&warehouseId=" + warehouseId, 200);
        JsonNode summary = sales.get("summary");
        assertEquals(1, summary.get("salesCount").asInt());
        assertAmount("100.00", summary.get("grossSales"));
        assertAmount("20.00", summary.get("returns"));
        assertAmount("80.00", summary.get("netSales"));
        assertAmount("32.00", summary.get("costOfSales"));
        assertAmount("48.00", summary.get("grossProfit"));
        assertEquals(today.toString(), sales.get("daily").get(0).get("date").asText());
        assertAmount("8", sales.get("products").get(0).get("netQuantity"));

        JsonNode purchases = httpGet(admin(), "/api/v1/reports/purchases?from=" + today + "&to=" + today + "&warehouseId=" + warehouseId + "&supplierId=" + supplierId, 200);
        assertEquals(2, purchases.get("summary").get("receiptsCount").asInt());
        assertAmount("115.00", purchases.get("summary").get("receivedAmount"));
        assertAmount("25", purchases.get("items").get(0).get("receivedQuantity"));

        JsonNode kardex = httpGet(admin(), "/api/v1/reports/kardex?productId=" + productId + "&warehouseId=" + warehouseId + "&from=" + today + "&to=" + today, 200);
        assertAmount("0", kardex.get("openingQuantity"));
        assertEquals(4, kardex.get("movements").size());
        assertEquals("RECEIPT", kardex.get("movements").get(0).get("movementType").asText());
        assertAmount("10", kardex.get("movements").get(0).get("balanceQuantity"));
        assertAmount("0", kardex.get("movements").get(1).get("balanceQuantity"));
        assertAmount("0.00", kardex.get("movements").get(1).get("balanceValue"));
        assertAmount("17", kardex.get("closingQuantity"));
        assertAmount("83.00", kardex.get("closingValue"));

        JsonNode valuation = httpGet(admin(), "/api/v1/reports/valuation?warehouseId=" + warehouseId, 200);
        assertEquals("MOVING_AVERAGE", valuation.get("method").asText());
        assertTrue(valuation.get("items").get(0).get("reconciled").asBoolean());
        assertAmount("83.00", valuation.get("items").get(0).get("totalValue"));
    }

    @Test
    void reportsValidateParametersAndPermissions() {
        String admin = admin();
        String warehouseId = warehouse().get("id").asText();
        LocalDate today = LocalDate.now(ZONE);
        problem(call(get("/api/v1/reports/inventory-balance?from=" + today + "&to=" + today.plusDays(1) + "&warehouseId=" + warehouseId), admin, null), 400, "INVALID_QUERY");
        problem(call(get("/api/v1/reports/inventory-balance?from=" + today.minusDays(400) + "&to=" + today + "&warehouseId=" + warehouseId), admin, null), 400, "INVALID_QUERY");
        problem(call(get("/api/v1/reports/inventory-balance?from=" + today + "&to=" + today.minusDays(1) + "&warehouseId=" + warehouseId), admin, null), 400, "INVALID_QUERY");
        problem(call(get("/api/v1/reports/inventory-balance?from=" + today + "&to=" + today + "&warehouseId=" + warehouseId + "&zone=Mars/Base"), admin, null), 400, "INVALID_QUERY");
        problem(call(get("/api/v1/reports/inventory-balance?from=" + today + "&to=" + today), admin, null), 400, "INVALID_PARAMETER");
        problem(call(get("/api/v1/reports/inventory-balance?from=hoy&to=" + today + "&warehouseId=" + warehouseId), admin, null), 400, "INVALID_PARAMETER");
        problem(call(get("/api/v1/reports/sales?from=" + today + "&to=" + today), token("VENDEDOR"), null), 403, "ACCESS_DENIED");
        problem(call(get("/api/v1/reports/valuation"), token("AUDITOR"), null), 403, "ACCESS_DENIED");
        problem(call(get("/api/v1/reports/kardex?productId=" + java.util.UUID.randomUUID() + "&warehouseId=" + warehouseId + "&from=" + today + "&to=" + today), admin, null),
            404, "RESOURCE_NOT_FOUND");
        JsonNode utc = httpGet(admin, "/api/v1/reports/sales?from=" + today.minusDays(1) + "&to=" + today.minusDays(1) + "&zone=UTC", 200);
        assertEquals("UTC", utc.get("zone").asText());
        httpGet(admin, "/api/v1/reports/purchases?from=" + today.minusDays(30) + "&to=" + today, 200);
        httpGet(admin, "/api/v1/reports/low-stock", 200);
    }
}
