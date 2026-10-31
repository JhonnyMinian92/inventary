package com.ecuapp.inventary.api;

import com.ecuapp.inventary.auth.AppUser;
import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class WarehouseScopesApiTest extends ApiTestSupport {
    @Test
    void listAndDetailsAreRestrictedToExplicitWarehouseAssignments() {
        String administrator=admin();
        JsonNode first=warehouse(),second=warehouse();
        String username="scoped."+suffix().toLowerCase();
        AppUser user=users.saveAndFlush(new AppUser(username,encoder.encode(PASSWORD),"MANAGER_ALMACEN"));
        String token=login(username,PASSWORD);

        JsonNode grant=httpPost(administrator,"/api/v1/admin/users/"+user.getId()+"/warehouse-scopes",
            Map.of("warehouseId",first.get("id").asText(),"reason","Responsable temporal de la bodega A"),201);
        assertTrue(grant.path("effective").asBoolean());
        JsonNode page=httpGet(token,"/api/v1/warehouses",200);
        assertEquals(1,page.path("page").path("totalElements").asInt());
        assertEquals(first.get("id").asText(),page.path("content").get(0).path("id").asText());
        httpGet(token,"/api/v1/warehouses/"+first.get("id").asText(),200);
        problem(call(get("/api/v1/warehouses/"+second.get("id").asText()),token,null),404,"RESOURCE_NOT_FOUND");
        problem(call(get("/api/v1/inventory/balances/"+UUID.randomUUID()+"/"+second.get("id").asText()),token,null),404,"RESOURCE_NOT_FOUND");
        problem(call(get("/api/v1/inventory/availability?warehouseId="+second.get("id").asText()+"&productId="+UUID.randomUUID()+"&sku=unknown"),token,null),404,"RESOURCE_NOT_FOUND");

        httpDelete(administrator,"/api/v1/admin/users/"+user.getId()+"/warehouse-scopes/"+grant.get("id"),204);
        problem(call(get("/api/v1/warehouses/"+first.get("id").asText()),token,null),404,"RESOURCE_NOT_FOUND");
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where resource_id=?::uuid and action='USER_WAREHOUSE_SCOPE_GRANTED'",Integer.class,UUID.fromString(grant.get("id").asText())));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where resource_id=?::uuid and action='USER_WAREHOUSE_SCOPE_REVOKED'",Integer.class,UUID.fromString(grant.get("id").asText())));
    }

    @Test
    void unprivilegedGrantAndInvalidDatesAreRejected() throws Exception {
        String administrator=admin();
        String username="limited."+suffix().toLowerCase();
        AppUser user=users.saveAndFlush(new AppUser(username,encoder.encode(PASSWORD),"VENDEDOR"));
        JsonNode warehouse=warehouse();
        var denied=call(post("/api/v1/admin/users/"+user.getId()+"/warehouse-scopes")
            .contentType("application/json").content(json.writeValueAsString(Map.of(
                "warehouseId",warehouse.get("id").asText(),"reason","No autorizado"))),token("AUDITOR"),null);
        problem(denied,403,"ACCESS_DENIED");
        var invalid=call(post("/api/v1/admin/users/"+user.getId()+"/warehouse-scopes")
            .contentType("application/json").content(json.writeValueAsString(Map.of(
                "warehouseId",warehouse.get("id").asText(),"reason","Vigencia incorrecta",
                "validFrom","2026-10-30T00:00:00Z","validUntil","2026-10-29T00:00:00Z"))),administrator,null);
        problem(invalid,422,"BUSINESS_RULE_VIOLATION");
    }

    @Test
    void auditLogIsAppendOnlyIncludingTruncate() {
        warehouse();
        UUID eventId=jdbc.queryForObject("select id from audit_event where action='WAREHOUSE_CREATED' order by occurred_at desc limit 1",UUID.class);
        assertThrows(DataAccessException.class,()->jdbc.update("update audit_event set action='TAMPERED' where id=?",eventId));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from audit_event where id=?",eventId));
        assertThrows(DataAccessException.class,()->jdbc.execute("truncate table audit_event"));
    }

    @Test
    void expiredWarehouseScopeIsRevokedAndAuditedBeforeAReplacementIsGranted() {
        String administrator=admin();
        JsonNode warehouse=warehouse();
        String username="expiry."+suffix().toLowerCase();
        AppUser user=users.saveAndFlush(new AppUser(username,encoder.encode(PASSWORD),"VENDEDOR"));
        Instant validFrom=Instant.now().minusSeconds(300);
        Instant validUntil=Instant.now().minusSeconds(120);

        JsonNode expired=httpPost(administrator,"/api/v1/admin/users/"+user.getId()+"/warehouse-scopes",
            Map.of("warehouseId",warehouse.get("id").asText(),"reason","Asignación temporal ya vencida",
                "validFrom",validFrom.toString(),"validUntil",validUntil.toString()),201);
        assertFalse(expired.path("effective").asBoolean());

        JsonNode replacement=httpPost(administrator,"/api/v1/admin/users/"+user.getId()+"/warehouse-scopes",
            Map.of("warehouseId",warehouse.get("id").asText(),"reason","Nueva asignación vigente"),201);
        assertTrue(replacement.path("effective").asBoolean());
        assertEquals(1,jdbc.queryForObject("select count(*) from user_warehouse_scope where id=?::uuid and active=false and revoked_by='SYSTEM_EXPIRY'",
            Integer.class,UUID.fromString(expired.get("id").asText())));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where resource_id=?::uuid and action='USER_WAREHOUSE_SCOPE_EXPIRED'",
            Integer.class,UUID.fromString(expired.get("id").asText())));
    }

    @Test
    void auditReadDoesNotExposeFinancialCostFieldsWithoutFinancialPermission() {
        JsonNode warehouse=warehouse();
        JsonNode product=product("2.50","5.00");
        JsonNode movement=stock(product.get("id").asText(),warehouse.get("id").asText(),"3","2.50");
        String auditor=token("AUDITOR");

        JsonNode page=httpGet(auditor,"/api/v1/inventory/movements?warehouseId="+warehouse.get("id").asText(),200);
        assertEquals(1,page.path("content").size());
        assertTrue(page.path("content").get(0).path("unitCost").isNull());

        JsonNode detail=httpGet(auditor,"/api/v1/inventory/movements/"+movement.get("id").asText(),200);
        assertTrue(detail.path("movement").path("unitCost").isNull());
        assertTrue(detail.path("valuedTotalCost").isNull());
        assertEquals(0,detail.path("valuation").size());

        JsonNode financial=httpGet(admin(),"/api/v1/inventory/movements/"+movement.get("id").asText(),200);
        assertFalse(financial.path("movement").path("unitCost").isNull());
        assertFalse(financial.path("valuedTotalCost").isNull());
    }
}
