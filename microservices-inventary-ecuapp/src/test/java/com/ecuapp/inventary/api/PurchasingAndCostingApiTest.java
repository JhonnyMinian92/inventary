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

class PurchasingAndCostingApiTest extends ApiTestSupport {

    @Test
    void suppliersFollowPermissionMatrixAndUniqueness() {
        String code = "sup" + suffix().toLowerCase();
        JsonNode supplier = httpPost(token("MANAGER_ALMACEN"), "/api/v1/suppliers",
            Map.of("code", code, "name", "Distribuidora", "taxId", "17" + suffix(), "email", "Ventas@Proveedor.EC"), 201);
        assertEquals(code.toUpperCase(), supplier.get("code").asText());
        assertEquals("ventas@proveedor.ec", supplier.get("email").asText());
        problem(call(post("/api/v1/suppliers"), admin(), Map.of("code", code, "name", "dup")), 409, "CONFLICT");
        problem(call(post("/api/v1/suppliers"), admin(), Map.of("code", "S" + suffix(), "name", "dup tax", "taxId", supplier.get("taxId").asText())), 409, "CONFLICT");
        problem(call(post("/api/v1/suppliers"), admin(), Map.of("code", "S" + suffix(), "name", "x", "email", "no-es-email")), 400, "VALIDATION_FAILED");
        problem(call(post("/api/v1/suppliers"), token("VENDEDOR"), Map.of("code", "S" + suffix(), "name", "x")), 403, "ACCESS_DENIED");
        problem(call(get("/api/v1/suppliers"), token("VENDEDOR"), null), 403, "ACCESS_DENIED");
        assertEquals(1, httpGet(token("AUDITOR"), "/api/v1/suppliers?q=" + code.substring(3, 9), 200).get("page").get("totalElements").asInt());
        String id = supplier.get("id").asText();
        problem(call(put("/api/v1/suppliers/" + id), admin(), Map.of("code", "OTRO", "name", "x")), 409, "CONFLICT");
        httpPost(admin(), "/api/v1/suppliers/" + id + "/deactivate", null, 204);
        problem(call(post("/api/v1/purchase-orders"), token("MANAGER_ALMACEN"), Map.of("supplierId", id, "warehouseId", warehouse().get("id").asText(),
            "items", List.of(Map.of("productId", product("1", "2").get("id").asText(), "quantity", 1, "unitCost", 1))), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        httpPost(admin(), "/api/v1/suppliers/" + id + "/activate", null, 204);
        // Customers are a separate list; a supplier id is not a customer.
        problem(call(get("/api/v1/customers/" + id), admin(), null), 404, "RESOURCE_NOT_FOUND");
        httpGet(token("VENDEDOR"), "/api/v1/customers", 200);
    }

    @Test
    void purchaseOrderLifecycleEnforcesSegregationAndIdempotentTransitions() {
        String manager = token("MANAGER_ALMACEN");
        String supplierId = supplier().get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "2.00").get("id").asText();
        String key = key();
        Map<String, Object> body = Map.of("supplierId", supplierId, "warehouseId", warehouseId, "notes", "OC de prueba",
            "items", List.of(Map.of("productId", productId, "quantity", 10, "unitCost", new BigDecimal("1.25"))));
        JsonNode order = httpPost(manager, "/api/v1/purchase-orders", body, 201, "Idempotency-Key", key);
        assertEquals(order.get("id").asText(), httpPost(manager, "/api/v1/purchase-orders", body, 201, "Idempotency-Key", key).get("id").asText());
        problem(call(post("/api/v1/purchase-orders"), manager, Map.of("supplierId", supplierId, "warehouseId", warehouseId,
            "items", List.of(Map.of("productId", productId, "quantity", 1, "unitCost", 1))), "Idempotency-Key", key), 409, "CONFLICT");
        assertAmount("12.50", order.get("total"));
        assertEquals("DRAFT", order.get("status").asText());
        String id = order.get("id").asText();

        problem(call(post("/api/v1/purchase-orders/" + id + "/approve"), admin(), null), 409, "CONFLICT");
        assertEquals("SUBMITTED", httpPost(manager, "/api/v1/purchase-orders/" + id + "/submit", null, 200).get("status").asText());
        assertEquals("SUBMITTED", httpPost(manager, "/api/v1/purchase-orders/" + id + "/submit", null, 200).get("status").asText());
        problem(call(post("/api/v1/purchase-orders/" + id + "/approve"), manager, null), 403, "ACCESS_DENIED");

        // An administrator who created/submitted the order cannot approve it (segregation of duties).
        String selfAdmin = newUserToken("SUPER_ADMIN");
        JsonNode own = httpPost(selfAdmin, "/api/v1/purchase-orders", Map.of("supplierId", supplierId, "warehouseId", warehouseId,
            "items", List.of(Map.of("productId", productId, "quantity", 1, "unitCost", 1))), 201, "Idempotency-Key", key());
        httpPost(selfAdmin, "/api/v1/purchase-orders/" + own.get("id").asText() + "/submit", null, 200);
        problem(call(post("/api/v1/purchase-orders/" + own.get("id").asText() + "/approve"), selfAdmin, null), 409, "CONFLICT");
        assertEquals("CANCELLED", httpPost(selfAdmin, "/api/v1/purchase-orders/" + own.get("id").asText() + "/cancel", null, 200).get("status").asText());
        assertEquals("CANCELLED", httpPost(selfAdmin, "/api/v1/purchase-orders/" + own.get("id").asText() + "/cancel", null, 200).get("status").asText());

        JsonNode approved = httpPost(admin(), "/api/v1/purchase-orders/" + id + "/approve", null, 200);
        assertEquals("APPROVED", approved.get("status").asText());
        assertEquals(username("SUPER_ADMIN"), approved.get("approvedBy").asText());
        assertEquals("APPROVED", httpPost(admin(), "/api/v1/purchase-orders/" + id + "/approve", null, 200).get("status").asText());
        JsonNode list = httpGet(token("AUDITOR"), "/api/v1/purchase-orders?status=APPROVED&supplierId=" + supplierId + "&from=" + Instant.now().minusSeconds(600), 200);
        assertEquals(1, list.get("page").get("totalElements").asInt());
        problem(call(get("/api/v1/purchase-orders?sort=notes"), admin(), null), 400, "INVALID_QUERY");
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where resource_id=?::uuid and action='PURCHASE_ORDER_APPROVED'", Integer.class, id));

        // An approved order with no receipts can be cancelled; repeating the transition is idempotent.
        assertEquals("CANCELLED", httpPost(manager, "/api/v1/purchase-orders/" + id + "/cancel", null, 200).get("status").asText());
        assertEquals("CANCELLED", httpPost(manager, "/api/v1/purchase-orders/" + id + "/cancel", null, 200).get("status").asText());
    }

    @Test
    void partialReceiptsAccumulateNeverExceedOrderAndAreIdempotent() {
        String supplierId = supplier().get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "2.00").get("id").asText();
        JsonNode order = approvedOrder(supplierId, warehouseId, List.of(Map.of("productId", productId, "quantity", 5, "unitCost", 2)));
        String orderId = order.get("id").asText();
        String lineId = order.get("items").get(0).get("id").asText();
        String key = key();
        Map<String, Object> first = Map.of("items", List.of(line("purchaseOrderLineId", lineId, "2")));
        JsonNode receipt = httpPost(token("MANAGER_ALMACEN"), "/api/v1/purchase-orders/" + orderId + "/receipts", first, 201, "Idempotency-Key", key);
        assertEquals(receipt.get("id").asText(), httpPost(token("MANAGER_ALMACEN"), "/api/v1/purchase-orders/" + orderId + "/receipts", first, 201, "Idempotency-Key", key).get("id").asText());
        assertEquals("PARTIALLY_RECEIVED", httpGet(admin(), "/api/v1/purchase-orders/" + orderId, 200).get("status").asText());
        problem(call(post("/api/v1/purchase-orders/" + orderId + "/receipts"), admin(), Map.of("items", List.of(line("purchaseOrderLineId", lineId, "3.001"))),
            "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/purchase-orders/" + orderId + "/receipts"), admin(), Map.of("items", List.of(line("purchaseOrderLineId", UUID.randomUUID().toString(), "1"))),
            "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        receive(orderId, List.of(line("purchaseOrderLineId", lineId, "3")));
        JsonNode received = httpGet(admin(), "/api/v1/purchase-orders/" + orderId, 200);
        assertEquals("RECEIVED", received.get("status").asText());
        assertAmount("0", received.get("items").get(0).get("remainingQuantity"));
        assertAmount("5", onHand(productId, warehouseId));
        problem(call(post("/api/v1/purchase-orders/" + orderId + "/receipts"), admin(), Map.of("items", List.of(line("purchaseOrderLineId", lineId, "1"))),
            "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");

        JsonNode receipts = httpGet(token("AUDITOR"), "/api/v1/goods-receipts?purchaseOrderId=" + orderId, 200);
        assertEquals(2, receipts.get("page").get("totalElements").asInt());
        JsonNode detail = httpGet(token("AUDITOR"), "/api/v1/goods-receipts/" + receipt.get("id").asText(), 200);
        assertAmount("4.00", detail.get("total"));
        assertEquals(supplierId, detail.get("supplierId").asText());
        assertEquals(2, jdbc.queryForObject("select count(*) from outbox_event where event_type='GOODS_RECEIPT_POSTED' and payload->>'purchaseOrderId'=?", Integer.class, orderId));
    }

    @Test
    void movingAverageCostFollowsReceiptsAndSalesUseHistoricalCost() {
        String supplierId = supplier().get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        String productId = product("9.99", "20.00").get("id").asText();
        JsonNode first = approvedOrder(supplierId, warehouseId, List.of(Map.of("productId", productId, "quantity", 10, "unitCost", 10)));
        receive(first.get("id").asText(), List.of(line("purchaseOrderLineId", first.get("items").get(0).get("id").asText(), "10")));
        JsonNode second = approvedOrder(supplierId, warehouseId, List.of(Map.of("productId", productId, "quantity", 10, "unitCost", 14)));
        receive(second.get("id").asText(), List.of(line("purchaseOrderLineId", second.get("items").get(0).get("id").asText(), "10")));

        JsonNode sale = sale(token("VENDEDOR"), warehouseId, productId, "5");
        JsonNode movement = httpGet(admin(), "/api/v1/inventory/movements?referenceType=SALE&referenceId=" + sale.get("id").asText(), 200).get("content").get(0);
        // Average of 10 @ 10.00 and 10 @ 14.00 = 12.00, not the catalog cost 9.99.
        assertAmount("12.00", movement.get("unitCost"));
        assertAmount("60.0000", httpGet(admin(), "/api/v1/inventory/movements/" + movement.get("id").asText(), 200).get("valuedTotalCost"));

        JsonNode valuation = httpGet(admin(), "/api/v1/reports/valuation?productId=" + productId + "&warehouseId=" + warehouseId, 200).get("items").get(0);
        assertAmount("15", valuation.get("onHand"));
        assertAmount("180.00", valuation.get("totalValue"));
        assertAmount("12", valuation.get("averageCost"));
        assertTrue(valuation.get("reconciled").asBoolean());

        // Cancelling the sale returns the goods at the cost they left with (12.00).
        String cancelKey = key();
        httpPost(token("VENDEDOR"), "/api/v1/sales/" + sale.get("id").asText() + "/request-cancellation", Map.of("reason", "Cliente desistió"), 200, "Idempotency-Key", cancelKey);
        httpPost(admin(), "/api/v1/sales/" + sale.get("id").asText() + "/approve-cancellation", null, 200, "Idempotency-Key", key());
        JsonNode after = httpGet(admin(), "/api/v1/reports/valuation?productId=" + productId + "&warehouseId=" + warehouseId, 200).get("items").get(0);
        assertAmount("240.00", after.get("totalValue"));
        assertAmount("20", after.get("onHand"));
    }

    @Test
    void fifoPolicyConsumesOldestLayersAndPurchaseReturnsLeaveAtReceiptCost() {
        String previous = jdbc.queryForObject("select method from inventory_costing_config where singleton_key='DEFAULT'", String.class);
        jdbc.update("update inventory_costing_config set method='FIFO' where singleton_key='DEFAULT'");
        try {
            assertEquals("FIFO", httpGet(admin(), "/api/v1/costing/policy", 200).get("method").asText());
            String supplierId = supplier().get("id").asText();
            String warehouseId = warehouse().get("id").asText();
            String productId = product("1.00", "50.00").get("id").asText();
            JsonNode a = approvedOrder(supplierId, warehouseId, List.of(Map.of("productId", productId, "quantity", 10, "unitCost", 10)));
            JsonNode receiptA = receive(a.get("id").asText(), List.of(line("purchaseOrderLineId", a.get("items").get(0).get("id").asText(), "10")));
            JsonNode b = approvedOrder(supplierId, warehouseId, List.of(Map.of("productId", productId, "quantity", 10, "unitCost", 14)));
            JsonNode receiptB = receive(b.get("id").asText(), List.of(line("purchaseOrderLineId", b.get("items").get(0).get("id").asText(), "10")));

            JsonNode sale = sale(token("VENDEDOR"), warehouseId, productId, "12");
            JsonNode movement = httpGet(admin(), "/api/v1/inventory/movements?referenceType=SALE&referenceId=" + sale.get("id").asText(), 200).get("content").get(0);
            JsonNode detail = httpGet(admin(), "/api/v1/inventory/movements/" + movement.get("id").asText(), 200);
            // FIFO: 10 @ 10.00 + 2 @ 14.00 = 128.00
            assertAmount("128.0000", detail.get("valuedTotalCost"));
            assertEquals(2, detail.get("valuation").size());

            // Returning 3 units of the second receipt removes them at 14.00 from that receipt's layer.
            JsonNode purchaseReturn = httpPost(token("MANAGER_ALMACEN"), "/api/v1/purchase-returns", Map.of("goodsReceiptId", receiptB.get("id").asText(),
                "reason", "Defectuoso", "items", List.of(line("receiptLineId", receiptB.get("items").get(0).get("id").asText(), "3"))), 201, "Idempotency-Key", key());
            JsonNode returnMovement = httpGet(admin(), "/api/v1/inventory/movements?referenceType=PURCHASE_RETURN&referenceId=" + purchaseReturn.get("id").asText(), 200)
                .get("content").get(0);
            assertAmount("14.00", returnMovement.get("unitCost"));
            JsonNode valuation = httpGet(admin(), "/api/v1/reports/valuation?productId=" + productId + "&warehouseId=" + warehouseId, 200).get("items").get(0);
            assertAmount("5", valuation.get("onHand"));
            assertAmount("70.00", valuation.get("totalValue"));
            assertNotNull(receiptA);
        } finally {
            jdbc.update("update inventory_costing_config set method=? where singleton_key='DEFAULT'", previous);
        }
    }

    @Test
    void costingPolicyIsProtectedAndCannotChangeAfterValuedMovements() {
        JsonNode policy = httpGet(admin(), "/api/v1/costing/policy", 200);
        String current = policy.get("method").asText();
        problem(call(get("/api/v1/costing/policy"), token("VENDEDOR"), null), 403, "ACCESS_DENIED");
        problem(call(put("/api/v1/costing/policy"), token("MANAGER_ALMACEN"), Map.of("method", "FIFO")), 403, "ACCESS_DENIED");
        assertEquals(current, httpPut(admin(), "/api/v1/costing/policy", Map.of("method", current), 200).get("method").asText());
        String other = "FIFO".equals(current) ? "MOVING_AVERAGE" : "FIFO";
        problem(call(put("/api/v1/costing/policy"), admin(), Map.of("method", other)), 409, "CONFLICT");
        problem(call(put("/api/v1/costing/policy"), admin(), Map.of("method", "LIFO")), 400, "MALFORMED_JSON");
    }

    @Test
    void purchaseReturnsCannotExceedReceivedQuantityEvenConcurrently() throws Exception {
        String supplierId = supplier().get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "2.00").get("id").asText();
        JsonNode order = approvedOrder(supplierId, warehouseId, List.of(Map.of("productId", productId, "quantity", 4, "unitCost", 3)));
        JsonNode receipt = receive(order.get("id").asText(), List.of(line("purchaseOrderLineId", order.get("items").get(0).get("id").asText(), "4")));
        String receiptId = receipt.get("id").asText();
        String receiptLineId = receipt.get("items").get(0).get("id").asText();
        problem(call(post("/api/v1/purchase-returns"), admin(), Map.of("goodsReceiptId", receiptId, "reason", "x",
            "items", List.of(line("receiptLineId", receiptLineId, "4.001"))), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/purchase-returns"), admin(), Map.of("goodsReceiptId", receiptId, "reason", "x",
            "items", List.of(line("receiptLineId", UUID.randomUUID().toString(), "1"))), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");

        String admin = admin();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) results.add(pool.submit(() -> {
            start.await();
            return call(post("/api/v1/purchase-returns"), admin, Map.of("goodsReceiptId", receiptId, "reason", "Concurrente",
                "items", List.of(line("receiptLineId", receiptLineId, "3"))), "Idempotency-Key", key()).getResponse().getStatus();
        }));
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, statuses.stream().filter(s -> s == 201).count(), "only one return of 3 fits in 4 received: " + statuses);
        assertAmount("1", onHand(productId, warehouseId));
        assertAmount("3", jdbc.queryForObject("select coalesce(sum(quantity),0) from purchase_return_line where source_goods_receipt_line_id=?::uuid",
            BigDecimal.class, receiptLineId));
        assertTrue(httpGet(token("AUDITOR"), "/api/v1/purchase-returns?goodsReceiptId=" + receiptId, 200).get("page").get("totalElements").asInt() == 1);
    }
}
