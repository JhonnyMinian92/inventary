package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class SalesApiTest extends ApiTestSupport {

    @Test
    void salesUseServerPricesValidateStockAndAreIdempotent() {
        String seller = token("VENDEDOR");
        String warehouseId = warehouse().get("id").asText();
        JsonNode product = product("2.00", "7.35");
        String productId = product.get("id").asText();
        stock(productId, warehouseId, "10", "2.00");
        String customerId = customer().get("id").asText();
        String key = key();
        Map<String, Object> body = Map.of("warehouseId", warehouseId, "channel", "POS", "customerId", customerId,
            "items", List.of(Map.of("productId", productId, "quantity", new BigDecimal("1.500"))));
        JsonNode sale = httpPost(seller, "/api/v1/sales", body, 201, "Idempotency-Key", key);
        assertAmount("11.03", sale.get("total"));
        assertAmount("0.00", sale.get("tax"));
        assertEquals(customerId, sale.get("customerId").asText());
        assertEquals(sale.get("id").asText(), httpPost(seller, "/api/v1/sales", body, 201, "Idempotency-Key", key).get("id").asText());
        assertAmount("8.5", onHand(productId, warehouseId));
        problem(call(post("/api/v1/sales"), seller, Map.of("warehouseId", warehouseId, "channel", "POS",
            "items", List.of(Map.of("productId", productId, "quantity", 1))), "Idempotency-Key", key), 409, "CONFLICT");

        problem(call(post("/api/v1/sales"), seller, Map.of("warehouseId", warehouseId, "channel", "POS",
            "items", List.of(Map.of("productId", productId, "quantity", 9)))
            , "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/sales"), seller, Map.of("warehouseId", warehouseId, "channel", "POS",
            "items", List.of(Map.of("productId", productId, "quantity", 1), Map.of("productId", productId, "quantity", 1))), "Idempotency-Key", key()),
            422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/sales"), seller, Map.of("warehouseId", warehouseId, "channel", "POS", "unitPrice", 0.01,
            "items", List.of(Map.of("productId", productId, "quantity", 1))), "Idempotency-Key", key()), 400, "MALFORMED_JSON");
        problem(call(post("/api/v1/sales"), seller, Map.of("warehouseId", warehouseId, "channel", "POS", "items", List.of()), "Idempotency-Key", key()),
            400, "VALIDATION_FAILED");
        problem(call(post("/api/v1/sales"), seller, Map.of("warehouseId", warehouseId, "channel", "POS", "customerId", supplier().get("id").asText(),
            "items", List.of(Map.of("productId", productId, "quantity", 1))), "Idempotency-Key", key()), 404, "RESOURCE_NOT_FOUND");
        String otherCurrency = product("1.00", "1.00").get("id").asText();
        jdbc.update("update product set currency='EUR' where id=?::uuid", otherCurrency);
        stock(otherCurrency, warehouseId, "1", "1.00");
        problem(call(post("/api/v1/sales"), seller, Map.of("warehouseId", warehouseId, "channel", "POS",
            "items", List.of(Map.of("productId", productId, "quantity", 1), Map.of("productId", otherCurrency, "quantity", 1))), "Idempotency-Key", key()),
            422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/sales"), token("AUDITOR"), body, "Idempotency-Key", key()), 403, "ACCESS_DENIED");
        assertAmount("8.5", onHand(productId, warehouseId));

        JsonNode list = httpGet(token("AUDITOR"), "/api/v1/sales?customerId=" + customerId + "&status=COMPLETED&channel=POS&from=" + Instant.now().minusSeconds(600), 200);
        assertEquals(1, list.get("page").get("totalElements").asInt());
        assertEquals(1, list.get("content").get(0).get("items").size());
        problem(call(get("/api/v1/sales?sort=createdBy"), seller, null), 400, "INVALID_QUERY");
        assertEquals(1, jdbc.queryForObject("select count(*) from outbox_event where aggregate_id=?::uuid and event_type='SALE_COMPLETED'", Integer.class, sale.get("id").asText()));
    }

    @Test
    void concurrentSalesNeverOversellTheLastUnits() throws Exception {
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "2.00").get("id").asText();
        stock(productId, warehouseId, "5", "1.00");
        String seller = token("VENDEDOR");
        int requests = 12;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) results.add(pool.submit(() -> {
            start.await();
            return call(post("/api/v1/sales"), seller, Map.of("warehouseId", warehouseId, "channel", "POS",
                "items", List.of(Map.of("productId", productId, "quantity", 1))), "Idempotency-Key", key()).getResponse().getStatus();
        }));
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(5, statuses.stream().filter(s -> s == 201).count(), statuses.toString());
        assertTrue(statuses.stream().allMatch(s -> s == 201 || s == 422), statuses.toString());
        assertAmount("0", onHand(productId, warehouseId));
        assertAmount("5", jdbc.queryForObject("select coalesce(sum(quantity),0) from stock_movement where product_id=?::uuid and movement_type='SALE'",
            BigDecimal.class, productId));
    }

    @Test
    void concurrentRetriesWithTheSameKeyCreateOneSale() throws Exception {
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "2.00").get("id").asText();
        stock(productId, warehouseId, "5", "1.00");
        String seller = token("VENDEDOR");
        String key = key();
        Map<String, Object> body = Map.of("warehouseId", warehouseId, "channel", "WEB", "items", List.of(Map.of("productId", productId, "quantity", 2)));
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) results.add(pool.submit(() -> {
            start.await();
            var r = call(post("/api/v1/sales"), seller, body, "Idempotency-Key", key);
            return r.getResponse().getStatus() + ":" + (r.getResponse().getStatus() == 201 ? json.readTree(r.getResponse().getContentAsString()).get("id").asText() : "");
        }));
        start.countDown();
        Set<String> ids = new HashSet<>();
        for (Future<String> f : results) {
            String r = f.get(60, TimeUnit.SECONDS);
            assertTrue(r.startsWith("201:"), r);
            ids.add(r.substring(4));
        }
        pool.shutdown();
        assertEquals(1, ids.size());
        assertAmount("3", onHand(productId, warehouseId));
    }

    @Test
    void cancellationRequiresDistinctApproverRestoresStockOnceAndIsIdempotent() {
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "4.00").get("id").asText();
        stock(productId, warehouseId, "5", "1.00");
        String sellerUser = createUser("VENDEDOR");
        String seller = login(sellerUser, PASSWORD);
        JsonNode sale = sale(seller, warehouseId, productId, "2");
        String id = sale.get("id").asText();
        assertAmount("3", onHand(productId, warehouseId));

        problem(call(post("/api/v1/sales/" + id + "/request-cancellation"), seller, Map.of("reason", ""), "Idempotency-Key", key()), 400, "VALIDATION_FAILED");
        problem(call(post("/api/v1/sales/" + id + "/request-cancellation"), seller, Map.of("reason", "x")), 400, "MISSING_HEADER");
        String requestKey = key();
        JsonNode requested = httpPost(seller, "/api/v1/sales/" + id + "/request-cancellation", Map.of("reason", "Error de cobro"), 200, "Idempotency-Key", requestKey);
        assertEquals("CANCELLATION_REQUESTED", requested.get("status").asText());
        assertEquals("CANCELLATION_REQUESTED", httpPost(seller, "/api/v1/sales/" + id + "/request-cancellation", Map.of("reason", "Error de cobro"), 200,
            "Idempotency-Key", requestKey).get("status").asText());
        assertAmount("3", onHand(productId, warehouseId));
        problem(call(post("/api/v1/sales/" + id + "/approve-cancellation"), seller, null, "Idempotency-Key", key()), 403, "ACCESS_DENIED");

        // A user holding sales:cancel who also requested the cancellation cannot approve it.
        String approverUser = createUser("SUPER_ADMIN");
        String approver = login(approverUser, PASSWORD);
        JsonNode ownSale = sale(approver, warehouseId, productId, "1");
        httpPost(approver, "/api/v1/sales/" + ownSale.get("id").asText() + "/request-cancellation", Map.of("reason", "propio"), 200, "Idempotency-Key", key());
        problem(call(post("/api/v1/sales/" + ownSale.get("id").asText() + "/approve-cancellation"), approver, null, "Idempotency-Key", key()), 409, "CONFLICT");

        String approveKey = key();
        JsonNode cancelled = httpPost(admin(), "/api/v1/sales/" + id + "/approve-cancellation", null, 200, "Idempotency-Key", approveKey);
        assertEquals("CANCELLED", cancelled.get("status").asText());
        assertEquals(username("SUPER_ADMIN"), cancelled.get("cancellationApprovedBy").asText());
        assertEquals(id, httpPost(admin(), "/api/v1/sales/" + id + "/approve-cancellation", null, 200, "Idempotency-Key", approveKey).get("id").asText());
        problem(call(post("/api/v1/sales/" + id + "/approve-cancellation"), admin(), null, "Idempotency-Key", key()), 409, "CONFLICT");
        assertAmount("4", onHand(productId, warehouseId));
        assertEquals(1, jdbc.queryForObject("select count(*) from stock_movement where reference_id=?::uuid and movement_type='SALE_CANCEL_IN'", Integer.class, id));
    }

    @Test
    void salesReturnsRestockOrScrapAndCannotExceedSoldQuantity() throws Exception {
        String warehouseId = warehouseIdWithStock();
        String productId = lastProduct;
        String seller = token("VENDEDOR");
        JsonNode sale = sale(seller, warehouseId, productId, "4");
        String itemId = sale.get("items").get(0).get("id").asText();
        String key = key();
        Map<String, Object> restock = Map.of("sourceSaleId", sale.get("id").asText(), "reason", "Talla incorrecta",
            "items", List.of(Map.of("saleItemId", itemId, "quantity", 1, "disposition", "RESTOCK")));
        JsonNode first = httpPost(seller, "/api/v1/sales-returns", restock, 201, "Idempotency-Key", key);
        assertEquals(first.get("id").asText(), httpPost(seller, "/api/v1/sales-returns", restock, 201, "Idempotency-Key", key).get("id").asText());
        assertAmount("4.00", first.get("total"));
        assertAmount("7", onHand(productId, warehouseId));

        JsonNode scrap = httpPost(seller, "/api/v1/sales-returns", Map.of("sourceSaleId", sale.get("id").asText(), "reason", "Dañado",
            "items", List.of(Map.of("saleItemId", itemId, "quantity", 1, "disposition", "SCRAP"))), 201, "Idempotency-Key", key());
        assertAmount("7", onHand(productId, warehouseId), "scrapped goods never return to sellable stock");
        assertEquals(2, jdbc.queryForObject("select count(*) from stock_movement where reference_id=?::uuid", Integer.class, scrap.get("id").asText()));

        problem(call(post("/api/v1/sales-returns"), seller, Map.of("sourceSaleId", sale.get("id").asText(), "reason", "x",
            "items", List.of(Map.of("saleItemId", itemId, "quantity", 3, "disposition", "RESTOCK"))), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/sales-returns"), seller, Map.of("sourceSaleId", sale.get("id").asText(), "reason", "x",
            "items", List.of(Map.of("saleItemId", UUID.randomUUID(), "quantity", 1, "disposition", "RESTOCK"))), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/sales/" + sale.get("id").asText() + "/request-cancellation"), seller, Map.of("reason", "x"), "Idempotency-Key", key()),
            409, "CONFLICT");

        // Two concurrent returns of the remaining 2 units: only one may succeed.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
            start.await();
            return call(post("/api/v1/sales-returns"), seller, Map.of("sourceSaleId", sale.get("id").asText(), "reason", "Concurrente",
                "items", List.of(Map.of("saleItemId", itemId, "quantity", 2, "disposition", "RESTOCK"))), "Idempotency-Key", key()).getResponse().getStatus();
        }));
        start.countDown();
        List<Integer> statuses = List.of(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, statuses.stream().filter(s -> s == 201).count(), statuses.toString());
        assertAmount("4", jdbc.queryForObject("select coalesce(sum(quantity),0) from sales_return_line where source_sale_item_id=?::uuid", BigDecimal.class, itemId));

        assertEquals(3, httpGet(token("AUDITOR"), "/api/v1/sales-returns?sourceSaleId=" + sale.get("id").asText(), 200).get("page").get("totalElements").asInt());
        assertEquals(sale.get("id").asText(), httpGet(token("AUDITOR"), "/api/v1/sales-returns/" + first.get("id").asText(), 200).get("sourceSaleId").asText());
    }

    private String lastProduct;

    private String warehouseIdWithStock() {
        String warehouseId = warehouse().get("id").asText();
        lastProduct = product("1.50", "4.00").get("id").asText();
        stock(lastProduct, warehouseId, "10", "1.50");
        return warehouseId;
    }

    @Test
    void pricesComeFromPurchasesWithMarginAndSalesNeverLoseMoney() {
        String seller = token("VENDEDOR");
        String warehouseId = warehouse().get("id").asText();
        String supplierId = supplier().get("id").asText();
        // No prices are typed when creating the product.
        JsonNode created = httpPost(admin(), "/api/v1/catalog/products",
            Map.of("name", "Vaso " + suffix(), "categoryId", category().get("id").asText()), 201);
        String productId = created.get("id").asText();
        assertAmount("0.00", created.get("salePrice"));
        assertAmount("25.00", created.get("marginPercent"));

        // Invoice line: 12 units for 96.00 -> 8.00 each; suggested price 8.00 + 25 % = 10.00.
        JsonNode order = approvedOrder(supplierId, warehouseId, List.of(Map.of("productId", productId, "quantity", 12, "unitCost", new BigDecimal("8.00"))));
        receive(order.get("id").asText(), List.of(line("purchaseOrderLineId", order.get("items").get(0).get("id").asText(), "12")));
        JsonNode repriced = httpGet(admin(), "/api/v1/catalog/products/" + productId, 200);
        assertAmount("8.00", repriced.get("costPrice"));
        assertAmount("10.00", repriced.get("salePrice"));

        JsonNode quote = httpPost(seller, "/api/v1/sales/pricing", Map.of("warehouseId", warehouseId,
            "items", List.of(Map.of("productId", productId, "quantity", 2))), 200).get(0);
        assertAmount("8.00", quote.get("minimumPrice"));
        assertAmount("10.00", quote.get("suggestedPrice"));

        // Without a price the suggested one is used; the seller may go up or down, but never below cost.
        assertAmount("20.00", sale(seller, warehouseId, productId, "2").get("total"));
        Map<String, Object> discounted = Map.of("warehouseId", warehouseId, "channel", "POS",
            "items", List.of(Map.of("productId", productId, "quantity", 1, "unitPrice", new BigDecimal("8.50"))));
        assertAmount("8.50", httpPost(seller, "/api/v1/sales", discounted, 201, "Idempotency-Key", key()).get("total"));
        Map<String, Object> loss = Map.of("warehouseId", warehouseId, "channel", "POS",
            "items", List.of(Map.of("productId", productId, "quantity", 1, "unitPrice", new BigDecimal("7.99"))));
        JsonNode rejected = problem(call(post("/api/v1/sales"), seller, loss, "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        assertTrue(rejected.toString().contains("pérdida"), rejected.toString());
        assertAmount("9", onHand(productId, warehouseId));

        // A cheaper invoice lowers the catalog price, but older, more expensive units are still protected.
        JsonNode cheaper = approvedOrder(supplierId, warehouseId, List.of(Map.of("productId", productId, "quantity", 10, "unitCost", new BigDecimal("4.00"))));
        receive(cheaper.get("id").asText(), List.of(line("purchaseOrderLineId", cheaper.get("items").get(0).get("id").asText(), "10")));
        assertAmount("5.00", httpGet(admin(), "/api/v1/catalog/products/" + productId, 200).get("salePrice"));
        JsonNode next = httpPost(seller, "/api/v1/sales/pricing", Map.of("warehouseId", warehouseId,
            "items", List.of(Map.of("productId", productId, "quantity", 1))), 200).get(0);
        assertTrue(dec(next.get("minimumPrice")).compareTo(new BigDecimal("4.00")) > 0, next.toString());
        assertTrue(dec(next.get("suggestedPrice")).compareTo(dec(next.get("minimumPrice"))) > 0, next.toString());

        // Changing the markup reprices from the last purchase cost.
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("name", repriced.get("name").asText());
        update.put("categoryId", repriced.get("categoryId").asText());
        update.put("marginPercent", new BigDecimal("50"));
        assertAmount("6.00", httpPut(admin(), "/api/v1/catalog/products/" + productId, update, 200).get("salePrice"));
    }

    private static void assertAmount(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), message + " (was " + actual + ")");
    }
}
