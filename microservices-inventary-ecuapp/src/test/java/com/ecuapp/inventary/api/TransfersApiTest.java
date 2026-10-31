package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class TransfersApiTest extends ApiTestSupport {
    private String source;
    private String destination;
    private String productId;

    private JsonNode draft(String quantity) {
        source = warehouse().get("id").asText();
        destination = warehouse().get("id").asText();
        productId = product("1.00", "3.00").get("id").asText();
        stock(productId, source, "5", "2.40");
        return httpPost(token("MANAGER_ALMACEN"), "/api/v1/inventory/transfers", Map.of("sourceWarehouseId", source, "destinationWarehouseId", destination,
            "items", List.of(Map.of("productId", productId, "quantity", new BigDecimal(quantity)))), 201, "Idempotency-Key", key());
    }

    @Test
    void dispatchAndReceiptMoveStockAndCostWithoutChangingTotals() {
        String manager = token("MANAGER_ALMACEN");
        JsonNode transfer = draft("2");
        String id = transfer.get("id").asText();
        assertEquals("DRAFT", transfer.get("status").asText());
        String dispatchKey = key();
        assertEquals("IN_TRANSIT", httpPost(manager, "/api/v1/inventory/transfers/" + id + "/dispatch", null, 200, "Idempotency-Key", dispatchKey).get("status").asText());
        assertEquals("IN_TRANSIT", httpPost(manager, "/api/v1/inventory/transfers/" + id + "/dispatch", null, 200, "Idempotency-Key", dispatchKey).get("status").asText());
        assertAmount("3", onHand(productId, source));
        String receiveKey = key();
        JsonNode received = httpPost(manager, "/api/v1/inventory/transfers/" + id + "/receive", null, 200, "Idempotency-Key", receiveKey);
        assertEquals("COMPLETED", received.get("status").asText());
        assertEquals(id, httpPost(manager, "/api/v1/inventory/transfers/" + id + "/receive", null, 200, "Idempotency-Key", receiveKey).get("id").asText());
        assertAmount("3", onHand(productId, source));
        assertAmount("2", onHand(productId, destination));
        assertAmount("5", onHand(productId, source).add(onHand(productId, destination)));

        JsonNode in = httpGet(admin(), "/api/v1/inventory/movements?referenceId=" + id + "&type=TRANSFER_IN", 200).get("content").get(0);
        assertAmount("2.40", in.get("unitCost"));
        JsonNode valuation = httpGet(admin(), "/api/v1/reports/valuation?productId=" + productId + "&warehouseId=" + destination, 200).get("items").get(0);
        assertAmount("4.80", valuation.get("totalValue"));
        problem(call(post("/api/v1/inventory/transfers/" + id + "/cancel"), manager, Map.of("reason", "tarde"), "Idempotency-Key", key()), 409, "CONFLICT");
        assertEquals(1, httpGet(token("AUDITOR"), "/api/v1/inventory/transfers?status=COMPLETED&sourceWarehouseId=" + source, 200).get("page").get("totalElements").asInt());
    }

    @Test
    void cancellingInTransitCompensatesTheDispatchExactlyOnce() {
        String manager = token("MANAGER_ALMACEN");
        String id = draft("2").get("id").asText();
        httpPost(manager, "/api/v1/inventory/transfers/" + id + "/dispatch", null, 200, "Idempotency-Key", key());
        String cancelKey = key();
        JsonNode cancelled = httpPost(manager, "/api/v1/inventory/transfers/" + id + "/cancel", Map.of("reason", "Camión averiado"), 200, "Idempotency-Key", cancelKey);
        assertEquals("CANCELLED", cancelled.get("status").asText());
        assertEquals("Camión averiado", cancelled.get("cancellationReason").asText());
        httpPost(manager, "/api/v1/inventory/transfers/" + id + "/cancel", Map.of("reason", "Camión averiado"), 200, "Idempotency-Key", cancelKey);
        httpPost(manager, "/api/v1/inventory/transfers/" + id + "/cancel", Map.of("reason", "otra vez"), 200, "Idempotency-Key", key());
        assertAmount("5", onHand(productId, source));
        assertEquals(1, jdbc.queryForObject("select count(*) from stock_movement where reference_id=?::uuid and idempotency_key like 'TRFC-IN-%'", Integer.class, id));
        problem(call(post("/api/v1/inventory/transfers/" + id + "/receive"), manager, null, "Idempotency-Key", key()), 409, "CONFLICT");
        problem(call(post("/api/v1/inventory/transfers/" + id + "/cancel"), manager, Map.of("reason", ""), "Idempotency-Key", key()), 400, "VALIDATION_FAILED");
    }

    @Test
    void reversalCompensatesBothWarehousesOnceAndRequiresDestinationStock() {
        String manager = token("MANAGER_ALMACEN");
        String id = draft("2").get("id").asText();
        httpPost(manager, "/api/v1/inventory/transfers/" + id + "/dispatch", null, 200, "Idempotency-Key", key());
        httpPost(manager, "/api/v1/inventory/transfers/" + id + "/receive", null, 200, "Idempotency-Key", key());
        // Consume one unit at destination: the reversal can no longer take back 2 units.
        adjust(productId, destination, "DECREASE", "1", null);
        problem(call(post("/api/v1/inventory/transfers/" + id + "/reverse"), manager, Map.of("reason", "Error"), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        stock(productId, destination, "1", "2.40");
        String reverseKey = key();
        JsonNode reversed = httpPost(manager, "/api/v1/inventory/transfers/" + id + "/reverse", Map.of("reason", "Destino equivocado"), 200, "Idempotency-Key", reverseKey);
        assertEquals("REVERSED", reversed.get("status").asText());
        httpPost(manager, "/api/v1/inventory/transfers/" + id + "/reverse", Map.of("reason", "Destino equivocado"), 200, "Idempotency-Key", reverseKey);
        assertAmount("5", onHand(productId, source));
        assertAmount("0", onHand(productId, destination));
        assertEquals(2, jdbc.queryForObject("select count(*) from stock_movement where reference_type='STOCK_TRANSFER_REVERSAL' and reference_id=?::uuid", Integer.class, id));
    }

    @Test
    void transferValidation() {
        String manager = token("MANAGER_ALMACEN");
        String warehouse = warehouse().get("id").asText();
        String product = product("1", "2").get("id").asText();
        problem(call(post("/api/v1/inventory/transfers"), manager, Map.of("sourceWarehouseId", warehouse, "destinationWarehouseId", warehouse,
            "items", List.of(Map.of("productId", product, "quantity", 1))), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        String other = warehouse().get("id").asText();
        JsonNode noStock = httpPost(manager, "/api/v1/inventory/transfers", Map.of("sourceWarehouseId", warehouse, "destinationWarehouseId", other,
            "items", List.of(Map.of("productId", product, "quantity", 1))), 201, "Idempotency-Key", key());
        problem(call(post("/api/v1/inventory/transfers/" + noStock.get("id").asText() + "/dispatch"), manager, null, "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        assertEquals("DRAFT", httpGet(manager, "/api/v1/inventory/transfers/" + noStock.get("id").asText(), 200).get("status").asText());
        problem(call(get("/api/v1/inventory/transfers/" + UUID.randomUUID()), manager, null), 404, "RESOURCE_NOT_FOUND");
        problem(call(post("/api/v1/inventory/transfers"), token("VENDEDOR"), Map.of("sourceWarehouseId", warehouse, "destinationWarehouseId", other,
            "items", List.of(Map.of("productId", product, "quantity", 1))), "Idempotency-Key", key()), 403, "ACCESS_DENIED");
        problem(call(get("/api/v1/inventory/transfers?sort=requestHash"), manager, null), 400, "INVALID_QUERY");
    }

    @Test
    void sourceAndDestinationWarehouseOperatorsCanProcessTheirOwnTransferSide() {
        String administrator=admin();
        String sourceWarehouse=warehouse().get("id").asText();
        String destinationWarehouse=warehouse().get("id").asText();
        String product=product("1.00","3.00").get("id").asText();
        stock(product,sourceWarehouse,"4","2.40");
        JsonNode transfer=httpPost(administrator,"/api/v1/inventory/transfers",
            Map.of("sourceWarehouseId",sourceWarehouse,"destinationWarehouseId",destinationWarehouse,
                "items",List.of(Map.of("productId",product,"quantity",new BigDecimal("2")))),
            201,"Idempotency-Key",key());
        String id=transfer.get("id").asText();

        String sourceUser="source."+suffix().toLowerCase();
        String destinationUser="destination."+suffix().toLowerCase();
        var source=users.saveAndFlush(new com.ecuapp.inventary.auth.AppUser(sourceUser,encoder.encode(PASSWORD),"MANAGER_ALMACEN"));
        var destination=users.saveAndFlush(new com.ecuapp.inventary.auth.AppUser(destinationUser,encoder.encode(PASSWORD),"MANAGER_ALMACEN"));
        httpPost(administrator,"/api/v1/admin/users/"+source.getId()+"/warehouse-scopes",
            Map.of("warehouseId",sourceWarehouse,"reason","Responsable de despacho"),201);
        httpPost(administrator,"/api/v1/admin/users/"+destination.getId()+"/warehouse-scopes",
            Map.of("warehouseId",destinationWarehouse,"reason","Responsable de recepción"),201);
        String sourceToken=login(sourceUser,PASSWORD);
        String destinationToken=login(destinationUser,PASSWORD);

        httpGet(sourceToken,"/api/v1/inventory/transfers/"+id,200);
        httpGet(destinationToken,"/api/v1/inventory/transfers/"+id,200);
        assertEquals(1,httpGet(sourceToken,"/api/v1/inventory/transfers",200).get("page").get("totalElements").asInt());
        assertEquals(1,httpGet(destinationToken,"/api/v1/inventory/transfers",200).get("page").get("totalElements").asInt());

        httpPost(sourceToken,"/api/v1/inventory/transfers/"+id+"/dispatch",null,200,"Idempotency-Key",key());
        problem(call(post("/api/v1/inventory/transfers/"+id+"/receive"),sourceToken,null,"Idempotency-Key",key()),404,"RESOURCE_NOT_FOUND");
        httpPost(destinationToken,"/api/v1/inventory/transfers/"+id+"/receive",null,200,"Idempotency-Key",key());
        problem(call(post("/api/v1/inventory/transfers/"+id+"/dispatch"),destinationToken,null,"Idempotency-Key",key()),404,"RESOURCE_NOT_FOUND");
        assertAmount("2",onHand(product,destinationWarehouse));
    }
}
