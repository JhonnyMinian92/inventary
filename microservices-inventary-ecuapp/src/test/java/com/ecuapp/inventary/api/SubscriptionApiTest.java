package com.ecuapp.inventary.api;

import com.ecuapp.inventary.subscriptions.SubscriptionBillingJob;
import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class SubscriptionApiTest extends ApiTestSupport {
    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    @Autowired SubscriptionBillingJob billingJob;

    private JsonNode service(String price, Map<String, Object> plan) {
        Map<String, Object> body = new LinkedHashMap<>(plan);
        body.put("name", "Servicio " + suffix());
        body.put("categoryId", category().get("id").asText());
        body.put("kind", "SERVICE");
        body.put("salePrice", new BigDecimal(price));
        return httpPost(admin(), "/api/v1/catalog/products", body, 201);
    }

    private JsonNode subscribe(String productId, String customerId, String warehouseId, Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>(extra);
        body.put("productId", productId);
        body.put("customerId", customerId);
        body.put("warehouseId", warehouseId);
        return httpPost(token("VENDEDOR"), "/api/v1/subscriptions", body, 201, "Idempotency-Key", key());
    }

    @Test
    void oneTimeServicesAreSoldWithoutTouchingStockAndSubscriptionsCannotBeSoldDirectly() {
        String warehouseId = warehouse().get("id").asText();
        JsonNode oneTime = service("15.00", Map.of("serviceBilling", "ONE_TIME"));
        assertEquals("ONE_TIME", oneTime.get("serviceBilling").asText());
        JsonNode sale = sale(token("VENDEDOR"), warehouseId, oneTime.get("id").asText(), "1");
        assertAmount("15.00", sale.get("total"));
        assertEquals(0, httpGet(admin(), "/api/v1/inventory/movements?productId=" + oneTime.get("id").asText(), 200).get("content").size());

        JsonNode plan = service("20.00", Map.of("serviceBilling", "SUBSCRIPTION", "billingFrequency", "MONTHLY", "termMonths", 3));
        assertEquals("MONTHLY", plan.get("billingFrequency").asText());
        problem(call(post("/api/v1/sales"), token("VENDEDOR"), Map.of("warehouseId", warehouseId, "channel", "POS",
            "items", List.of(Map.of("productId", plan.get("id").asText(), "quantity", 1))), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/catalog/products"), admin(), Map.of("name", "Plan " + suffix(), "categoryId", category().get("id").asText(),
            "kind", "SERVICE", "salePrice", 10, "serviceBilling", "SUBSCRIPTION", "billingFrequency", "MONTHLY", "termMonths", 5)), 422, "BUSINESS_RULE_VIOLATION");
    }

    @Test
    void previewSplitsBiweeklyChargesExactly() {
        JsonNode preview = httpPost(token("VENDEDOR"), "/api/v1/subscriptions/preview",
            Map.of("billingFrequency", "BIWEEKLY", "termMonths", 1, "monthlyPrice", new BigDecimal("25.01"), "startDate", "2026-01-01"), 200);
        assertEquals(2, preview.get("installmentCount").asInt());
        assertAmount("12.51", preview.get("installments").get(0).get("amount"));
        assertAmount("12.50", preview.get("installments").get(1).get("amount"));
        assertEquals("2026-01-16", preview.get("installments").get(1).get("billingDate").asText());
        assertAmount("25.01", preview.get("total"));
        JsonNode atEnd = httpPost(token("VENDEDOR"), "/api/v1/subscriptions/preview",
            Map.of("billingFrequency", "AT_END", "termMonths", 6, "monthlyPrice", 10, "startDate", "2026-01-01"), 200);
        assertEquals("2026-07-01", atEnd.get("installments").get(0).get("billingDate").asText());
        assertAmount("60.00", atEnd.get("total"));
        assertEquals(3, httpGet(token("VENDEDOR"), "/api/v1/subscriptions/options", 200).get("frequencies").size());
    }

    @Test
    void chargesAreInvoicedOnTheirDateAndCancellationOnlyChargesTheCurrentPeriod() {
        LocalDate today = LocalDate.now(ZONE);
        String warehouseId = warehouse().get("id").asText();
        String customerId = customer().get("id").asText();
        String productId = service("20.00", Map.of("serviceBilling", "SUBSCRIPTION", "billingFrequency", "MONTHLY", "termMonths", 3)).get("id").asText();

        JsonNode created = subscribe(productId, customerId, warehouseId, Map.of());
        String id = created.get("id").asText();
        assertEquals("ACTIVE", created.get("status").asText());
        assertAmount("60.00", created.get("totalAmount"));
        assertAmount("20.00", created.get("invoicedAmount"));
        assertEquals("INVOICED", created.get("installments").get(0).get("status").asText());
        assertTrue(created.get("installments").get(0).get("saleNumber").asText().startsWith("VTA-"));
        assertEquals(today.plusMonths(1).toString(), created.get("nextBillingDate").asText());

        // The billing job generates the next invoice on its date, and running it twice never bills twice.
        assertTrue(billingJob.runFor(today.plusMonths(1)) >= 1);
        billingJob.runFor(today.plusMonths(1));
        assertAmount("40.00", httpGet(admin(), "/api/v1/subscriptions/" + id, 200).get("invoicedAmount"));

        JsonNode preview = httpGet(admin(), "/api/v1/subscriptions/" + id + "/cancellation-preview", 200);
        assertEquals(1, preview.get("cancelledCount").asInt());
        assertAmount("0.00", preview.get("amountChargedNow"));
        JsonNode cancelled = httpPost(token("VENDEDOR"), "/api/v1/subscriptions/" + id + "/cancel", Map.of("reason", "Cliente se muda"), 200);
        assertEquals("CANCELLED", cancelled.get("status").asText());
        assertEquals("CANCELLED", cancelled.get("installments").get(2).get("status").asText());
        assertAmount("40.00", cancelled.get("invoicedAmount"));

        // Paid at the end: cancelling mid-plan invoices the current (and only) charge.
        String atEnd = service("10.00", Map.of("serviceBilling", "SUBSCRIPTION", "billingFrequency", "AT_END", "termMonths", 6)).get("id").asText();
        JsonNode atEndSub = subscribe(atEnd, customerId, warehouseId, Map.of());
        assertAmount("0.00", atEndSub.get("invoicedAmount"));
        assertAmount("60.00", httpGet(admin(), "/api/v1/subscriptions/" + atEndSub.get("id").asText() + "/cancellation-preview", 200).get("amountChargedNow"));
        assertAmount("60.00", httpPost(admin(), "/api/v1/subscriptions/" + atEndSub.get("id").asText() + "/cancel", Map.of("reason", "x"), 200).get("invoicedAmount"));

        // Starting in the future: nothing is billed yet and cancelling charges nothing.
        JsonNode future = subscribe(productId, customerId, warehouseId, Map.of("startDate", today.plusDays(10).toString()));
        assertAmount("0.00", future.get("invoicedAmount"));
        assertAmount("0.00", httpPost(admin(), "/api/v1/subscriptions/" + future.get("id").asText() + "/cancel", Map.of("reason", "x"), 200).get("invoicedAmount"));
        problem(call(post("/api/v1/subscriptions"), token("VENDEDOR"), Map.of("productId", productId, "customerId", customerId, "warehouseId", warehouseId,
            "startDate", today.minusDays(1).toString()), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
    }

    @Test
    void prepaymentBillsEverythingAtOnceAndOnlyTheOwnerGivesDiscounts() {
        String warehouseId = warehouse().get("id").asText();
        String customerId = customer().get("id").asText();
        String productId = service("30.00", Map.of("serviceBilling", "SUBSCRIPTION", "billingFrequency", "BIWEEKLY", "termMonths", 1)).get("id").asText();

        problem(call(post("/api/v1/subscriptions"), token("VENDEDOR"), Map.of("productId", productId, "customerId", customerId, "warehouseId", warehouseId,
            "termMonths", 12, "prepay", true, "discountPercent", 10), "Idempotency-Key", key()), 403, "ACCESS_DENIED");
        assertTrue(httpGet(admin(), "/api/v1/subscriptions/options", 200).get("canDiscount").asBoolean());
        assertFalse(httpGet(token("VENDEDOR"), "/api/v1/subscriptions/options", 200).get("canDiscount").asBoolean());

        JsonNode noDiscount = subscribe(productId, customerId, warehouseId, Map.of("termMonths", 12, "prepay", true));
        assertAmount("360.00", noDiscount.get("invoicedAmount"));
        assertTrue(noDiscount.get("prepaid").asBoolean());

        Map<String, Object> body = Map.of("productId", productId, "customerId", customerId, "warehouseId", warehouseId, "termMonths", 12, "prepay", true, "discountPercent", 10);
        JsonNode discounted = httpPost(admin(), "/api/v1/subscriptions", body, 201, "Idempotency-Key", key());
        assertAmount("324.00", discounted.get("invoicedAmount"));
        assertEquals(24, discounted.get("installmentCount").asInt());
        Set<String> sales = new HashSet<>();
        discounted.get("installments").forEach(i -> sales.add(i.get("saleId").asText()));
        assertEquals(1, sales.size(), "a prepayment is a single invoice");
        problem(call(post("/api/v1/subscriptions/" + discounted.get("id").asText() + "/prepay"), admin(), Map.of()), 409, "CONFLICT");
    }
}
