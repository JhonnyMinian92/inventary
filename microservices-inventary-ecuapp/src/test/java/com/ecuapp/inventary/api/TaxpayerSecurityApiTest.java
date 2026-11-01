package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.ecuapp.inventary.auth.AppUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class TaxpayerSecurityApiTest extends ApiTestSupport {
    @Test
    void taxDataReadsUseDedicatedPermissionAndDoNotLeakToWarehouseManagers() {
        String auditor=token("AUDITOR");
        JsonNode page=httpGet(auditor,"/api/v1/taxpayers?page=0&size=10",200);
        assertTrue(page.path("content").isArray());

        String manager=newUserToken("MANAGER_ALMACEN");
        problem(call(get("/api/v1/taxpayers"),manager,null),403,"ACCESS_DENIED");
        String seller=newUserToken("VENDEDOR");
        problem(call(get("/api/v1/taxpayers"),seller,null),403,"ACCESS_DENIED");
    }

    @Test
    void onlyTaxpayerManagersCanImportAndManualImportNeverClaimsOfficialVerification() {
        String taxId="179"+String.format("%010d",Math.floorMod(System.nanoTime(),10_000_000_000L));
        Map<String,Object> payload=Map.of(
            "taxId",taxId,
            "legalName","Contribuyente de prueba",
            "status","ESTADO RECIBIDO",
            "sourceReference","CONTROLLED_TEST_FIXTURE",
            "establishments",List.of(Map.of("code","001","status","ESTADO DESCONOCIDO"))
        );
        String manager=newUserToken("MANAGER_ALMACEN");
        problem(call(post("/api/v1/taxpayers/import"),manager,payload),403,"ACCESS_DENIED");
        assertEquals(0,jdbc.queryForObject("select count(*) from taxpayer_profile where tax_id=?",Integer.class,taxId));

        JsonNode imported=httpPost(admin(),"/api/v1/taxpayers/import",payload,201);
        assertEquals(taxId,imported.path("taxId").asText());
        assertEquals("MANUAL_REVIEW",imported.path("freshnessStatus").asText());
        assertEquals("ESTADO RECIBIDO",imported.path("statusRaw").asText());
        JsonNode establishment=httpGet(admin(),"/api/v1/taxpayers/"+imported.path("id").asText()+"/establishments",200);
        assertEquals("001",establishment.get(0).path("code").asText());
        assertEquals("UNKNOWN",establishment.get(0).path("statusNormalized").asText());
        JsonNode snapshots=httpGet(admin(),"/api/v1/taxpayers/"+imported.path("id").asText()+"/snapshots",200);
        assertEquals(1,snapshots.path("page").path("totalElements").asInt());
    }
    @Test
    void taxpayerRecordsRequireAnExplicitPerRucScopeAndRevocationTakesEffectImmediately() {
        String auditorUsername = "tax-scope-" + suffix().toLowerCase();
        AppUser auditorUser = users.saveAndFlush(new AppUser(auditorUsername, encoder.encode(PASSWORD), "AUDITOR"));
        String auditor = login(auditorUsername, PASSWORD);
        String taxId = "179" + String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L));
        Map<String,Object> payload = Map.of(
            "taxId", taxId, "legalName", "Contribuyente con ámbito", "status", "ACTIVO",
            "sourceReference", "CONTROLLED_TEST_FIXTURE",
            "establishments", List.of(Map.of("code", "001", "status", "ABIERTO"))
        );
        JsonNode profile = httpPost(admin(), "/api/v1/taxpayers/import", payload, 201);
        String profileId = profile.path("id").asText();

        JsonNode beforeGrant = httpGet(auditor, "/api/v1/taxpayers", 200);
        assertEquals(0, beforeGrant.path("page").path("totalElements").asInt());
        problem(call(get("/api/v1/taxpayers/" + profileId), auditor, null), 404, "RESOURCE_NOT_FOUND");

        JsonNode grant = httpPost(admin(), "/api/v1/admin/users/" + auditorUser.getId() + "/taxpayer-scopes",
            Map.of("taxpayerId", profileId, "reason", "Auditoría tributaria autorizada"), 201);
        assertTrue(grant.path("effective").asBoolean());
        JsonNode afterGrant = httpGet(auditor, "/api/v1/taxpayers", 200);
        assertEquals(1, afterGrant.path("page").path("totalElements").asInt());
        assertEquals(profileId, afterGrant.path("content").get(0).path("id").asText());
        httpGet(auditor, "/api/v1/taxpayers/" + profileId, 200);
        httpGet(auditor, "/api/v1/taxpayers/" + profileId + "/establishments", 200);
        httpGet(auditor, "/api/v1/taxpayers/" + profileId + "/snapshots", 200);

        String otherTaxId = "179" + String.format("%010d", Math.floorMod(System.nanoTime() + 1, 10_000_000_000L));
        JsonNode other = httpPost(admin(), "/api/v1/taxpayers/import", Map.of(
            "taxId", otherTaxId, "legalName", "Otro contribuyente", "status", "ACTIVO",
            "sourceReference", "CONTROLLED_TEST_FIXTURE",
            "establishments", List.of(Map.of("code", "001", "status", "ABIERTO"))
        ), 201);
        problem(call(get("/api/v1/taxpayers/" + other.path("id").asText()), auditor, null), 404, "RESOURCE_NOT_FOUND");

        httpDelete(admin(), "/api/v1/admin/users/" + auditorUser.getId() + "/taxpayer-scopes/" + grant.path("id").asText(), 204);
        problem(call(get("/api/v1/taxpayers/" + profileId), auditor, null), 404, "RESOURCE_NOT_FOUND");
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where actor=? and action='TAXPAYER_SCOPE_GRANTED'",
            Integer.class, username("SUPER_ADMIN")));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where actor=? and action='TAXPAYER_SCOPE_REVOKED'",
            Integer.class, username("SUPER_ADMIN")));
        assertTrue(jdbc.queryForObject("select count(*) from audit_event where action='SENSITIVE_DATA_READ' and metadata->>'path' like '/api/v1/taxpayers%'",
            Integer.class) >= 4);
    }
}
