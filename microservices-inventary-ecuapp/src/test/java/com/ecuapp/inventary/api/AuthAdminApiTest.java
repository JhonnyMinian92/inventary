package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class AuthAdminApiTest extends ApiTestSupport {

    @Test
    void meReturnsRoleAndEffectivePermissionsAndPasswordCanBeChanged() {
        String username = createUser("VENDEDOR");
        String token = login(username, PASSWORD);
        JsonNode me = httpGet(token, "/api/v1/auth/me", 200);
        assertEquals("VENDEDOR", me.get("role").asText());
        List<String> permissions = new ArrayList<>();
        me.get("permissions").forEach(p -> permissions.add(p.asText()));
        assertTrue(permissions.containsAll(List.of("sales:manage", "catalog:read")));
        assertFalse(permissions.contains("security:manage"));

        problem(call(put("/api/v1/auth/me/password"), token, Map.of("currentPassword", "Wrong-Pass-2026!", "newPassword", "Nueva-Clave-2026-Ok!")),
            422, "BUSINESS_RULE_VIOLATION");
        problem(call(put("/api/v1/auth/me/password"), token, Map.of("currentPassword", PASSWORD, "newPassword", "debil")), 400, "VALIDATION_FAILED");
        problem(call(put("/api/v1/auth/me/password"), token, Map.of("currentPassword", PASSWORD, "newPassword", "sinmayusculas-123!")),
            422, "BUSINESS_RULE_VIOLATION");
        httpPut(token, "/api/v1/auth/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "Nueva-Clave-2026-Ok!"), 204);
        problem(call(get("/api/v1/auth/me"), token, null), 401, null);
        problem(call(post("/api/v1/auth/login").with(randomIp()), null, Map.of("username", username, "password", PASSWORD)), 401, "INVALID_CREDENTIALS");
        assertNotNull(login(username, "Nueva-Clave-2026-Ok!"));
    }

    @Test
    void userAdministrationValidatesPolicyUniquenessAndRoles() {
        String admin = admin();
        String username = "user." + suffix().toLowerCase();
        JsonNode created = httpPost(admin, "/api/v1/admin/users", Map.of("username", username.toUpperCase(), "password", "Clave-Muy-Segura-2026!", "role", "auditor"), 201);
        assertEquals(username, created.get("username").asText(), "username is normalized to lower case");
        assertEquals("AUDITOR", created.get("role").asText());
        assertFalse(created.has("passwordHash"));
        String id = created.get("id").asText();

        problem(call(post("/api/v1/admin/users"), admin, Map.of("username", username, "password", "Clave-Muy-Segura-2026!", "role", "AUDITOR")), 409, "CONFLICT");
        problem(call(post("/api/v1/admin/users"), admin, Map.of("username", "x" + suffix(), "password", "solominusculas2026", "role", "AUDITOR")),
            422, "BUSINESS_RULE_VIOLATION");
        String other = "pepe" + suffix().toLowerCase();
        problem(call(post("/api/v1/admin/users"), admin, Map.of("username", other, "password", "Mi-" + other + "-2026!", "role", "AUDITOR")),
            422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/admin/users"), admin, Map.of("username", "y" + suffix(), "password", "Clave-Muy-Segura-2026!", "role", "NO_EXISTE")),
            422, "BUSINESS_RULE_VIOLATION");

        assertEquals(id, httpGet(admin, "/api/v1/admin/users/" + id, 200).get("id").asText());
        problem(call(get("/api/v1/admin/users/" + UUID.randomUUID()), admin, null), 404, "RESOURCE_NOT_FOUND");
        JsonNode page = httpGet(admin, "/api/v1/admin/users?size=5&sort=createdAt,desc", 200);
        assertEquals(5, page.get("page").get("size").asInt());
        problem(call(get("/api/v1/admin/users?sort=passwordHash"), admin, null), 400, "INVALID_QUERY");

        JsonNode updated = httpPatch(admin, "/api/v1/admin/users/" + id, Map.of("role", "MANAGER_ALMACEN", "active", true), 200);
        assertEquals("MANAGER_ALMACEN", updated.get("role").asText());
        assertTrue(jdbc.queryForObject("select count(*) from audit_event where resource_id=?::uuid and action in ('USER_CREATED','USER_UPDATED')",
            Integer.class, id) >= 2);
        // Audit metadata never contains the password.
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_event where metadata::text like '%Clave-Muy-Segura%'", Integer.class));
    }

    @Test
    void customRolesAreAssignableAndPermissionChangesApplyImmediately() {
        String admin = admin();
        String code = "ROL_" + suffix();
        JsonNode role = httpPost(admin, "/api/v1/admin/roles", Map.of("code", code, "displayName", "Catálogo lector", "permissionCodes", List.of("catalog:read")), 201);
        assertEquals(List.of("catalog:read"), toList(role.get("permissions")));
        problem(call(post("/api/v1/admin/roles"), admin, Map.of("code", code, "displayName", "dup", "permissionCodes", List.of())), 409, "CONFLICT");
        problem(call(post("/api/v1/admin/roles"), admin, Map.of("code", "ADMIN", "displayName", "x", "permissionCodes", List.of())), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/admin/roles"), admin, Map.of("code", "R" + suffix(), "displayName", "x", "permissionCodes", List.of("security:manage"))),
            422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/admin/roles"), admin, Map.of("code", "R" + suffix(), "displayName", "x", "permissionCodes", List.of("nope:perm"))),
            422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/admin/roles"), admin, Map.of("code", "bad-code", "displayName", "x", "permissionCodes", List.of())), 400, "VALIDATION_FAILED");
        problem(call(put("/api/v1/admin/roles/SUPER_ADMIN/permissions"), admin, Map.of("permissionCodes", List.of("catalog:read"))),
            422, "BUSINESS_RULE_VIOLATION");
        assertTrue(httpGet(admin, "/api/v1/admin/permissions", 200).size() >= 20);

        String username = "custom." + suffix().toLowerCase();
        httpPost(admin, "/api/v1/admin/users", Map.of("username", username, "password", "Clave-Rol-Propio-2026!", "role", code), 201);
        String token = login(username, "Clave-Rol-Propio-2026!");
        httpGet(token, "/api/v1/catalog/products", 200);
        problem(call(get("/api/v1/sales"), token, null), 403, "ACCESS_DENIED");

        httpPut(admin, "/api/v1/admin/roles/" + code + "/permissions", Map.of("permissionCodes", List.of("catalog:read", "sales:read")), 200);
        httpGet(token, "/api/v1/sales", 200);
        assertEquals(2, httpGet(admin, "/api/v1/admin/roles/" + code, 200).get("permissions").size());

        httpPatch(admin, "/api/v1/admin/roles/" + code, Map.of("displayName", "Desactivado", "active", false), 200);
        problem(call(get("/api/v1/catalog/products"), token, null), 401, null);
        problem(call(post("/api/v1/auth/login").with(randomIp()), null, Map.of("username", username, "password", "Clave-Rol-Propio-2026!")), 401, "INVALID_CREDENTIALS");
        problem(call(patch("/api/v1/admin/roles/SUPER_ADMIN"), admin, Map.of("displayName", "x", "active", false)), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(get("/api/v1/admin/roles/NO_EXISTE_" + suffix()), admin, null), 404, "RESOURCE_NOT_FOUND");
    }

    /**
     * Two administrators try to demote each other at the same time while they are the only active SUPER_ADMINs.
     * Exactly one request must win and one active SUPER_ADMIN must always remain.
     */
    @Test
    void lastSuperAdminIsProtectedSequentiallyAndUnderConcurrency() throws Exception {
        String userA = createUser("SUPER_ADMIN");
        String userB = createUser("SUPER_ADMIN");
        String tokenA = login(userA, PASSWORD);
        String tokenB = login(userB, PASSWORD);
        UUID idA = users.findByUsername(userA).orElseThrow().getId();
        UUID idB = users.findByUsername(userB).orElseThrow().getId();
        List<UUID> others = jdbc.queryForList("select id from app_user where role='SUPER_ADMIN' and active=true and id not in (?,?)", UUID.class, idA, idB);
        try {
            jdbc.update("update app_user set active=false where id = any(?)", (Object) others.toArray(new UUID[0]));
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> aDemotesB = pool.submit(() -> { start.await(); return demote(tokenA, idB); });
            Future<Integer> bDemotesA = pool.submit(() -> { start.await(); return demote(tokenB, idA); });
            start.countDown();
            List<Integer> statuses = List.of(aDemotesB.get(30, TimeUnit.SECONDS), bDemotesA.get(30, TimeUnit.SECONDS));
            pool.shutdown();
            assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "exactly one demotion may succeed: " + statuses);
            assertTrue(statuses.contains(422) || statuses.contains(401), "the other must be rejected: " + statuses);
            Integer active = jdbc.queryForObject("select count(*) from app_user where role='SUPER_ADMIN' and active=true and id in (?,?)", Integer.class, idA, idB);
            assertEquals(1, active);

            UUID survivor = jdbc.queryForObject("select id from app_user where role='SUPER_ADMIN' and active=true and id in (?,?)", UUID.class, idA, idB);
            String survivorToken = survivor.equals(idA) ? login(userA, PASSWORD) : login(userB, PASSWORD);
            problem(call(patch("/api/v1/admin/users/" + survivor), survivorToken, Map.of("role", "SUPER_ADMIN", "active", false)),
                422, "BUSINESS_RULE_VIOLATION");
            problem(call(patch("/api/v1/admin/users/" + survivor), survivorToken, Map.of("role", "AUDITOR", "active", true)),
                422, "BUSINESS_RULE_VIOLATION");
        } finally {
            jdbc.update("update app_user set active=true where id = any(?)", (Object) others.toArray(new UUID[0]));
        }
    }

    private int demote(String token, UUID target) {
        MvcResult result = call(patch("/api/v1/admin/users/" + target), token, Map.of("role", "AUDITOR", "active", true));
        return result.getResponse().getStatus();
    }

    private static List<String> toList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asText()));
        return values;
    }
}
