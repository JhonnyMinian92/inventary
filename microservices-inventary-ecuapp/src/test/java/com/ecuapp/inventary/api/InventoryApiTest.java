package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class InventoryApiTest extends ApiTestSupport {

    @Test
    void warehousesHaveImmutableCodesLocationsAndCannotBeDeactivatedWithStock() {
        String admin = admin();
        String code = "wh" + suffix().toLowerCase();
        JsonNode wh = httpPost(admin, "/api/v1/warehouses", Map.of("code", code, "name", "Central", "address", "Quito"), 201);
        String id = wh.get("id").asText();
        assertEquals(code.toUpperCase(), wh.get("code").asText());
        problem(call(post("/api/v1/warehouses"), admin, Map.of("code", code.toUpperCase(), "name", "dup")), 409, "CONFLICT");
        problem(call(put("/api/v1/warehouses/" + id), admin, Map.of("code", "OTRO" + suffix(), "name", "x")), 409, "CONFLICT");
        assertEquals("Norte", httpPut(admin, "/api/v1/warehouses/" + id, Map.of("code", code, "name", "Norte"), 200).get("name").asText());

        JsonNode locations = httpGet(token("VENDEDOR"), "/api/v1/warehouses/" + id + "/locations", 200);
        assertEquals("MAIN", locations.get(0).get("code").asText());
        httpPost(admin, "/api/v1/warehouses/" + id + "/locations", Map.of("code", "a-01", "name", "Pasillo A", "locationType", "PICKING"), 201);
        problem(call(post("/api/v1/warehouses/" + id + "/locations"), admin, Map.of("code", "A-01", "name", "dup", "locationType", "STORAGE")), 409, "CONFLICT");
        assertEquals(2, httpGet(admin, "/api/v1/warehouses/" + id + "/locations", 200).size());

        JsonNode product = product("1.00", "2.00");
        stock(product.get("id").asText(), id, "1", "1.00");
        problem(call(post("/api/v1/warehouses/" + id + "/deactivate"), admin, null), 409, "CONFLICT");
        adjust(product.get("id").asText(), id, "DECREASE", "1", null);
        httpPost(admin, "/api/v1/warehouses/" + id + "/deactivate", null, 204);
        problem(call(post("/api/v1/inventory/adjustments"), admin, Map.of("productId", product.get("id").asText(), "warehouseId", id,
            "direction", "INCREASE", "quantity", 1, "reason", "x"), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        httpPost(admin, "/api/v1/warehouses/" + id + "/activate", null, 204);
        assertTrue(httpGet(admin, "/api/v1/warehouses?active=true&size=100&sort=createdAt,desc", 200).toString().contains(id));
    }

    @Test
    void adjustmentsAreIdempotentNeverGoNegativeAndValueStock() {
        String admin = admin();
        String productId = product("2.00", "5.00").get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        String key = key();
        Map<String, Object> body = Map.of("productId", productId, "warehouseId", warehouseId, "direction", "INCREASE",
            "quantity", new BigDecimal("7.125"), "reason", "Inventario inicial", "unitCost", new BigDecimal("2.5000"));
        JsonNode first = httpPost(admin, "/api/v1/inventory/adjustments", body, 201, "Idempotency-Key", key);
        JsonNode replay = httpPost(admin, "/api/v1/inventory/adjustments", body, 201, "Idempotency-Key", key);
        assertEquals(first.get("id").asText(), replay.get("id").asText());
        assertAmount("7.125", onHand(productId, warehouseId));
        problem(call(post("/api/v1/inventory/adjustments"), admin, Map.of("productId", productId, "warehouseId", warehouseId, "direction", "INCREASE",
            "quantity", 1, "reason", "otro"), "Idempotency-Key", key), 409, "CONFLICT");
        assertEquals(1, jdbc.queryForObject("select count(*) from stock_movement where idempotency_key=?", Integer.class, key));

        problem(call(post("/api/v1/inventory/adjustments"), admin, Map.of("productId", productId, "warehouseId", warehouseId, "direction", "DECREASE",
            "quantity", new BigDecimal("7.126"), "reason", "x"), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/inventory/adjustments"), admin, Map.of("productId", productId, "warehouseId", warehouseId, "direction", "DECREASE",
            "quantity", 1, "reason", "x", "unitCost", 1), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/inventory/adjustments"), admin, Map.of("productId", productId, "warehouseId", warehouseId, "direction", "INCREASE",
            "quantity", new BigDecimal("1.0001"), "reason", "x"), "Idempotency-Key", key()), 400, "VALIDATION_FAILED");
        problem(call(post("/api/v1/inventory/adjustments"), admin, Map.of("productId", productId, "warehouseId", warehouseId, "direction", "INCREASE",
            "quantity", 0, "reason", "x"), "Idempotency-Key", key()), 400, "VALIDATION_FAILED");
        problem(call(post("/api/v1/inventory/adjustments"), admin, Map.of("productId", UUID.randomUUID(), "warehouseId", warehouseId, "direction", "INCREASE",
            "quantity", 1, "reason", "x"), "Idempotency-Key", key()), 404, "RESOURCE_NOT_FOUND");
        problem(call(post("/api/v1/inventory/adjustments"), admin, Map.of("productId", productId, "warehouseId", warehouseId, "direction", "INCREASE",
            "quantity", 1, "reason", "x"), "Idempotency-Key", "k".repeat(121)), 422, "BUSINESS_RULE_VIOLATION");

        JsonNode detail = httpGet(admin, "/api/v1/inventory/movements/" + first.get("id").asText(), 200);
        assertAmount("17.8125", detail.get("valuedTotalCost"));
        assertEquals("MOVING_AVERAGE", detail.get("valuation").get(0).get("method").asText());
        problem(call(post("/api/v1/inventory/adjustments"), token("VENDEDOR"), body, "Idempotency-Key", key()), 403, "ACCESS_DENIED");
    }

    @Test
    void concurrentIdempotentAdjustmentsProduceExactlyOneMovement() throws Exception {
        String productId = product("2.00", "5.00").get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        String key = key();
        Map<String, Object> body = Map.of("productId", productId, "warehouseId", warehouseId, "direction", "INCREASE", "quantity", 3, "reason", "Concurrente");
        String admin = admin();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) results.add(pool.submit(() -> {
            start.await();
            return call(post("/api/v1/inventory/adjustments"), admin, body, "Idempotency-Key", key).getResponse().getStatus();
        }));
        start.countDown();
        for (Future<Integer> f : results) assertEquals(201, f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertAmount("3", onHand(productId, warehouseId));
        assertEquals(1, jdbc.queryForObject("select count(*) from stock_movement where idempotency_key=?", Integer.class, key));
    }

    @Test
    void balancesAvailabilityAndMovementFiltersWork() {
        String admin = admin();
        JsonNode product = product("1.00", "3.00");
        String productId = product.get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        stock(productId, warehouseId, "5", "1.00");
        adjust(productId, warehouseId, "DECREASE", "2", null);

        JsonNode balances = httpGet(token("VENDEDOR"), "/api/v1/inventory/balances?productId=" + productId + "&warehouseId=" + warehouseId, 200);
        assertEquals(1, balances.get("page").get("totalElements").asInt());
        assertAmount("3", balances.get("content").get(0).get("available"));
        problem(call(get("/api/v1/inventory/balances?sort=secret"), admin, null), 400, "INVALID_QUERY");
        httpGet(admin, "/api/v1/inventory/balances?sort=sku,desc&onlyAvailable=true", 200);

        JsonNode availability = httpGet(token("VENDEDOR"), "/api/v1/inventory/availability?sku=" + product.get("sku").asText().toLowerCase() + "&warehouseId=" + warehouseId + "&quantity=4", 200);
        assertFalse(availability.get("sufficient").asBoolean());
        assertTrue(httpGet(admin, "/api/v1/inventory/availability?productId=" + productId + "&warehouseId=" + warehouseId + "&quantity=3", 200).get("sufficient").asBoolean());
        problem(call(get("/api/v1/inventory/availability?warehouseId=" + warehouseId), admin, null), 400, "INVALID_QUERY");
        problem(call(get("/api/v1/inventory/availability?productId=" + productId), admin, null), 400, "INVALID_PARAMETER");

        JsonNode outs = httpGet(admin, "/api/v1/inventory/movements?productId=" + productId + "&type=ADJUSTMENT_OUT", 200);
        assertEquals(1, outs.get("page").get("totalElements").asInt());
        JsonNode all = httpGet(token("AUDITOR"), "/api/v1/inventory/movements?productId=" + productId + "&warehouseId=" + warehouseId
            + "&from=" + Instant.now().minusSeconds(3600) + "&to=" + Instant.now().plusSeconds(60), 200);
        assertEquals(2, all.get("page").get("totalElements").asInt());
        assertEquals(0, httpGet(admin, "/api/v1/inventory/movements?productId=" + productId + "&to=" + Instant.now().minusSeconds(3600), 200)
            .get("page").get("totalElements").asInt());
        problem(call(get("/api/v1/inventory/movements?from=2026-10-02T00:00:00Z&to=2026-10-01T00:00:00Z"), admin, null), 400, "INVALID_QUERY");
        problem(call(get("/api/v1/inventory/movements?type=NOPE"), admin, null), 400, "INVALID_PARAMETER");
        problem(call(get("/api/v1/inventory/movements/" + UUID.randomUUID()), admin, null), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void physicalCountsPostDifferencesAndRejectStaleSnapshots() {
        String admin = admin();
        String warehouseId = warehouse().get("id").asText();
        String a = product("2.00", "4.00").get("id").asText();
        String b = product("3.00", "6.00").get("id").asText();
        String c = product("1.00", "2.00").get("id").asText();
        stock(a, warehouseId, "10", "2.00");
        stock(b, warehouseId, "4", "3.00");
        String countKey = key();
        Map<String, Object> request = Map.of("warehouseId", warehouseId, "items", List.of(
            Map.of("productId", a, "countedQuantity", 8), Map.of("productId", b, "countedQuantity", 4), Map.of("productId", c, "countedQuantity", 2)));
        JsonNode count = httpPost(admin, "/api/v1/inventory/counts", request, 201, "Idempotency-Key", countKey);
        assertEquals(count.get("id").asText(), httpPost(admin, "/api/v1/inventory/counts", request, 201, "Idempotency-Key", countKey).get("id").asText());
        assertEquals("OPEN", count.get("status").asText());
        assertEquals(3, count.get("items").size());
        problem(call(post("/api/v1/inventory/counts"), admin, Map.of("warehouseId", warehouseId, "items", List.of(
            Map.of("productId", a, "countedQuantity", 1), Map.of("productId", a, "countedQuantity", 2))), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");

        String id = count.get("id").asText();
        JsonNode posted = httpPost(admin, "/api/v1/inventory/counts/" + id + "/post", null, 200);
        assertEquals("POSTED", posted.get("status").asText());
        assertEquals("POSTED", httpPost(admin, "/api/v1/inventory/counts/" + id + "/post", null, 200).get("status").asText(), "posting is idempotent");
        assertAmount("8", onHand(a, warehouseId));
        assertAmount("4", onHand(b, warehouseId));
        assertAmount("2", onHand(c, warehouseId));
        assertEquals(2, jdbc.queryForObject("select count(*) from stock_movement where reference_type='INVENTORY_COUNT' and reference_id=?::uuid", Integer.class, id));
        problem(call(post("/api/v1/inventory/counts/" + id + "/cancel"), admin, null), 409, "CONFLICT");

        JsonNode stale = httpPost(admin, "/api/v1/inventory/counts", Map.of("warehouseId", warehouseId, "items", List.of(Map.of("productId", a, "countedQuantity", 8))),
            201, "Idempotency-Key", key());
        adjust(a, warehouseId, "DECREASE", "1", null);
        problem(call(post("/api/v1/inventory/counts/" + stale.get("id").asText() + "/post"), admin, null), 409, "CONFLICT");
        assertEquals("CANCELLED", httpPost(admin, "/api/v1/inventory/counts/" + stale.get("id").asText() + "/cancel", null, 200).get("status").asText());

        assertTrue(httpGet(token("AUDITOR"), "/api/v1/inventory/counts?warehouseId=" + warehouseId + "&status=POSTED", 200).get("page").get("totalElements").asInt() >= 1);
        problem(call(post("/api/v1/inventory/counts"), token("AUDITOR"), request, "Idempotency-Key", key()), 403, "ACCESS_DENIED");
    }

    @Test
    void reorderRulesProduceSuggestionsFromAvailableStock() {
        String admin = admin();
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "2.00").get("id").asText();
        String supplierId = supplier().get("id").asText();
        stock(productId, warehouseId, "3", "1.00");
        JsonNode rule = httpPost(admin, "/api/v1/inventory/reorder-rules", Map.of("productId", productId, "warehouseId", warehouseId,
            "minimumQuantity", 5, "targetQuantity", 20, "leadTimeDays", 3, "preferredSupplierId", supplierId), 201);
        problem(call(post("/api/v1/inventory/reorder-rules"), admin, Map.of("productId", productId, "warehouseId", warehouseId,
            "minimumQuantity", 1, "targetQuantity", 2, "leadTimeDays", 0)), 409, "CONFLICT");
        problem(call(post("/api/v1/inventory/reorder-rules"), admin, Map.of("productId", product("1", "2").get("id").asText(), "warehouseId", warehouseId,
            "minimumQuantity", 5, "targetQuantity", 2, "leadTimeDays", 0)), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/inventory/reorder-rules"), admin, Map.of("productId", product("1", "2").get("id").asText(), "warehouseId", warehouseId,
            "minimumQuantity", 1, "targetQuantity", 2, "leadTimeDays", 0, "preferredSupplierId", customer().get("id").asText())), 404, "RESOURCE_NOT_FOUND");

        JsonNode suggestion = suggestionFor(productId);
        assertAmount("17", suggestion.get("suggestedQuantity"));
        assertEquals(supplierId, suggestion.get("preferredSupplierId").asText());
        assertNotNull(suggestionForReport(productId, warehouseId));

        httpPut(admin, "/api/v1/inventory/reorder-rules/" + rule.get("id").asText(), Map.of("productId", productId, "warehouseId", warehouseId,
            "minimumQuantity", 2, "targetQuantity", 10, "leadTimeDays", 1), 200);
        assertNull(suggestionFor(productId), "available 3 > minimum 2");
        problem(call(put("/api/v1/inventory/reorder-rules/" + rule.get("id").asText()), admin, Map.of("productId", productId, "warehouseId", warehouse().get("id").asText(),
            "minimumQuantity", 2, "targetQuantity", 10, "leadTimeDays", 1)), 409, "CONFLICT");
        httpPost(admin, "/api/v1/inventory/reorder-rules/" + rule.get("id").asText() + "/deactivate", null, 204);
        assertEquals(1, httpGet(token("AUDITOR"), "/api/v1/inventory/reorder-rules?warehouseId=" + warehouseId + "&active=false", 200).size());
    }

    @Test
    void ledgerTablesRejectUpdatesAndDeletesInPostgres() {
        String productId = product("1.00", "2.00").get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        String movementId = stock(productId, warehouseId, "2", "1.00").get("id").asText();
        assertThrows(DataAccessException.class, () -> jdbc.update("update stock_movement set quantity=99 where id=?::uuid", movementId));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from stock_movement where id=?::uuid", movementId));
        assertThrows(DataAccessException.class, () -> jdbc.update("update inventory_valuation_entry set total_cost=0 where stock_movement_id=?::uuid", movementId));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from inventory_valuation_entry where stock_movement_id=?::uuid", movementId));
        JsonNode count = httpPost(admin(), "/api/v1/inventory/counts", Map.of("warehouseId", warehouseId, "items", List.of(Map.of("productId", productId, "countedQuantity", 1))),
            201, "Idempotency-Key", key());
        String lineId = count.get("items").get(0).get("id").asText();
        assertThrows(DataAccessException.class, () -> jdbc.update("update inventory_count_line set counted_quantity=5 where id=?::uuid", lineId));
        assertThrows(DataAccessException.class, () -> jdbc.update("update stock_balance set on_hand=-1 where product_id=?::uuid", productId));
        assertAmount("2", onHand(productId, warehouseId));
    }

    private JsonNode suggestionFor(String productId) {
        for (JsonNode s : httpGet(token("AUDITOR"), "/api/v1/inventory/replenishment-suggestions", 200))
            if (s.get("productId").asText().equals(productId)) return s;
        return null;
    }

    private JsonNode suggestionForReport(String productId, String warehouseId) {
        for (JsonNode s : httpGet(admin(), "/api/v1/reports/low-stock?warehouseId=" + warehouseId, 200))
            if (s.get("productId").asText().equals(productId)) return s;
        return null;
    }
}
