package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class PartnerRolesApiTest extends ApiTestSupport {
    @Test
    void oneBusinessPartnerCanChangeBetweenSupplierCustomerAndBothWithoutDuplicatingIdentity() {
        String taxId="179"+String.format("%010d",Math.floorMod(System.nanoTime(),10_000_000_000L));
        JsonNode supplier=httpPost(token("MANAGER_ALMACEN"),"/api/v1/suppliers",
            Map.of("code","role"+suffix(),"name","Proveedor y cliente","taxId",taxId),201);
        String id=supplier.path("id").asText();

        problem(call(put("/api/v1/partners/"+id+"/roles"),token("VENDEDOR"),Map.of("roles",List.of("CUSTOMER"))),403,"ACCESS_DENIED");
        JsonNode both=httpPut(admin(),"/api/v1/partners/"+id+"/roles",Map.of("roles",List.of("CUSTOMER","SUPPLIER")),200);
        assertEquals("BOTH",both.path("type").asText());
        assertEquals(id,httpGet(admin(),"/api/v1/suppliers/"+id,200).path("id").asText());
        assertEquals(id,httpGet(admin(),"/api/v1/customers/"+id,200).path("id").asText());
        assertEquals(1,jdbc.queryForObject("select count(*) from business_partner where tax_id=?",Integer.class,taxId));

        JsonNode customer=httpPut(admin(),"/api/v1/partners/"+id+"/roles",Map.of("roles",List.of("CUSTOMER")),200);
        assertEquals("CUSTOMER",customer.path("type").asText());
        problem(call(get("/api/v1/suppliers/"+id),admin(),null),404,"RESOURCE_NOT_FOUND");
        assertEquals(id,httpGet(admin(),"/api/v1/customers/"+id,200).path("id").asText());
        assertEquals(2,jdbc.queryForObject("select count(*) from audit_event where resource_id=?::uuid and action='BUSINESS_PARTNER_ROLES_UPDATED'",Integer.class,id));
    }

    @Test
    void roleRequestMustContainOnlyCustomerAndOrSupplier() {
        String id=supplier().path("id").asText();
        problem(call(put("/api/v1/partners/"+id+"/roles"),admin(),Map.of("roles",List.of("BOTH"))),422,"BUSINESS_RULE_VIOLATION");
        problem(call(put("/api/v1/partners/"+id+"/roles"),admin(),Map.of("roles",List.of())),400,"VALIDATION_FAILED");
    }
}
