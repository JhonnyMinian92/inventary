package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Executable access matrix for the five canonical roles. Each row lists the roles that must be allowed;
 * every other role must receive 403. "Allowed" means the request passed authorization (2xx or a business 404).
 */
class RoleMatrixApiTest extends ApiTestSupport {
    private static final List<String> ROLES = List.of("SUPER_ADMIN", "AUDITOR", "MANAGER_ALMACEN", "VENDEDOR", "SOPORTE_TI");

    private record Row(String name, Supplier<MockHttpServletRequestBuilder> request, Set<String> allowed, String... headers) {}

    @Test
    void canonicalRolesMatchTheDocumentedPermissionMatrix() {
        String random = UUID.randomUUID().toString();
        List<Row> rows = List.of(
            new Row("admin users", () -> get("/api/v1/admin/users"), Set.of("SUPER_ADMIN")),
            new Row("audit events", () -> get("/api/v1/audit-events"), Set.of("SUPER_ADMIN", "AUDITOR")),
            new Row("catalog products", () -> get("/api/v1/catalog/products"), Set.of("SUPER_ADMIN", "AUDITOR", "MANAGER_ALMACEN", "VENDEDOR")),
            new Row("create category", () -> post("/api/v1/catalog/categories").contentType("application/json").content("{\"name\":\"" + suffix() + "\"}"),
                Set.of("SUPER_ADMIN", "MANAGER_ALMACEN")),
            new Row("inventory balances", () -> get("/api/v1/inventory/balances"), Set.of("SUPER_ADMIN", "AUDITOR", "MANAGER_ALMACEN", "VENDEDOR")),
            new Row("inventory movements", () -> get("/api/v1/inventory/movements"), Set.of("SUPER_ADMIN", "AUDITOR", "MANAGER_ALMACEN")),
            new Row("inventory counts", () -> get("/api/v1/inventory/counts"), Set.of("SUPER_ADMIN", "AUDITOR", "MANAGER_ALMACEN")),
            new Row("warehouses", () -> get("/api/v1/warehouses"), Set.of("SUPER_ADMIN", "AUDITOR", "MANAGER_ALMACEN", "VENDEDOR")),
            new Row("purchase orders", () -> get("/api/v1/purchase-orders"), Set.of("SUPER_ADMIN", "AUDITOR", "MANAGER_ALMACEN")),
            new Row("approve purchase order", () -> post("/api/v1/purchase-orders/" + random + "/approve"), Set.of("SUPER_ADMIN")),
            new Row("suppliers", () -> get("/api/v1/suppliers"), Set.of("SUPER_ADMIN", "AUDITOR", "MANAGER_ALMACEN")),
            new Row("customers", () -> get("/api/v1/customers"), Set.of("SUPER_ADMIN", "AUDITOR", "MANAGER_ALMACEN", "VENDEDOR")),
            new Row("sales", () -> get("/api/v1/sales"), Set.of("SUPER_ADMIN", "AUDITOR", "VENDEDOR")),
            new Row("approve sale cancellation", () -> post("/api/v1/sales/" + random + "/approve-cancellation"), Set.of("SUPER_ADMIN"), "Idempotency-Key", "k-" + random),
            new Row("whatsapp orders", () -> get("/api/v1/whatsapp-orders"), Set.of("SUPER_ADMIN", "AUDITOR", "VENDEDOR")),
            new Row("financial reports", () -> get("/api/v1/reports/valuation"), Set.of("SUPER_ADMIN")),
            new Row("costing policy", () -> get("/api/v1/costing/policy"), Set.of("SUPER_ADMIN")),
            new Row("outbox operations", () -> get("/api/v1/operations/outbox/stats"), Set.of("SUPER_ADMIN", "SOPORTE_TI")),
            new Row("actuator info", () -> get("/actuator/info"), Set.of("SUPER_ADMIN", "SOPORTE_TI")),
            new Row("graphql", () -> post("/graphql").contentType("application/json").content("{\"query\":\"{ activeCategories { totalElements } }\"}"),
                Set.of("SUPER_ADMIN", "AUDITOR", "MANAGER_ALMACEN", "VENDEDOR")));

        List<String> mismatches = new ArrayList<>();
        for (Row row : rows) {
            for (String role : ROLES) {
                int status = call(row.request().get(), token(role), null, row.headers()).getResponse().getStatus();
                boolean allowed = status != 401 && status != 403;
                if (allowed != row.allowed().contains(role)) mismatches.add(row.name() + " / " + role + " -> " + status);
            }
        }
        assertEquals(List.of(), mismatches);
    }
}
