package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
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
}
