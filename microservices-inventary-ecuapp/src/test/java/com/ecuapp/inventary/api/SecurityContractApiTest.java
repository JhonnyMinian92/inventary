package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class SecurityContractApiTest extends ApiTestSupport {
    @Autowired JwtEncoder jwtEncoder;

    @Test
    void missingMalformedForeignExpiredOrWrongAudienceTokensAreRejectedWith401Problem() throws Exception {
        problem(call(get("/api/v1/sales"), null, null), 401, "AUTHENTICATION_REQUIRED");
        problem(call(get("/api/v1/sales"), "not-a-jwt", null), 401, "AUTHENTICATION_REQUIRED");

        String username = username("SUPER_ADMIN");
        int version = users.findByUsername(username).orElseThrow().getTokenVersion();
        JwtEncoder foreign = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
            "another-secret-of-at-least-32-bytes-long!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        problem(call(get("/api/v1/sales"), token(foreign, username, version, "inventary-api", Instant.now().plusSeconds(600)), null), 401, null);
        problem(call(get("/api/v1/sales"), token(jwtEncoder, username, version, "inventary-api", Instant.now().minusSeconds(600)), null), 401, null);
        problem(call(get("/api/v1/sales"), token(jwtEncoder, username, version, "other-audience", Instant.now().plusSeconds(600)), null), 401, null);
        // Control: a correctly signed token with the right claims is accepted.
        expect(call(get("/api/v1/sales"), token(jwtEncoder, username, version, "inventary-api", Instant.now().plusSeconds(600)), null), 200);
    }

    @Test
    void deactivationPasswordResetAndLogoutRevokeIssuedTokens() {
        String username = createUser("VENDEDOR");
        String first = login(username, PASSWORD);
        UUID id = users.findByUsername(username).orElseThrow().getId();
        httpGet(first, "/api/v1/auth/me", 200);

        httpPut(admin(), "/api/v1/admin/users/" + id + "/password", Map.of("newPassword", "Otra-Clave-26!"), 204);
        problem(call(get("/api/v1/auth/me"), first, null), 401, null);

        String second = login(username, "Otra-Clave-26!");
        httpPost(second, "/api/v1/auth/logout", null, 204);
        problem(call(get("/api/v1/auth/me"), second, null), 401, null);

        String third = login(username, "Otra-Clave-26!");
        httpPatch(admin(), "/api/v1/admin/users/" + id, Map.of("role", "VENDEDOR", "active", false), 200);
        problem(call(get("/api/v1/auth/me"), third, null), 401, null);
        problem(loginAttempt(username, "Otra-Clave-26!"),
            401, "INVALID_CREDENTIALS");
    }

    @Test
    void loginFailuresAreGenericAndUsernameIsNormalized() {
        String username = createUser("AUDITOR");
        JsonNode wrongPassword = problem(loginAttempt(username, "Wrong-Pass-123!"),
            401, "INVALID_CREDENTIALS");
        JsonNode unknownUser = problem(loginAttempt("nobody-" + suffix(), "Wrong-Pass-123!"),
            401, "INVALID_CREDENTIALS");
        assertEquals(wrongPassword.get("detail").asText(), unknownUser.get("detail").asText());
        assertNotNull(login("  " + username.toUpperCase() + "  ", PASSWORD));
        problem(loginAttempt("", ""), 400, "VALIDATION_FAILED");
    }

    @Test
    void forbiddenOperationsReturn403ProblemWithoutSideEffectsAndAreAudited() {
        String seller = token("VENDEDOR");
        String code = "DENY" + suffix();
        problem(call(post("/api/v1/warehouses"), seller, Map.of("code", code, "name", "No")), 403, "ACCESS_DENIED");
        assertEquals(0, jdbc.queryForObject("select count(*) from warehouse where code=?", Integer.class, code));
        // Route allows inventory:read but the method requires inventory:manage or audit:read.
        problem(call(get("/api/v1/inventory/movements"), seller, null), 403, "ACCESS_DENIED");
        problem(call(get("/api/v1/admin/users"), seller, null), 403, "ACCESS_DENIED");

        Integer audited = jdbc.queryForObject("select count(*) from audit_event where actor=? and action='HTTP_REQUEST_FAILED' and outcome='DENIED'",
            Integer.class, username("VENDEDOR"));
        assertTrue(audited != null && audited >= 3, "denied requests must be audited");
    }

    @Test
    void requestIdIsEchoedWhenSafeAndRegeneratedWhenUnsafe() {
        MvcResult safe = call(get("/api/v1/warehouses/" + UUID.randomUUID()), admin(), null, "X-Request-ID", "client-req-123");
        JsonNode body = problem(safe, 404, "RESOURCE_NOT_FOUND");
        assertEquals("client-req-123", safe.getResponse().getHeader("X-Request-ID"));
        assertEquals("client-req-123", body.get("requestId").asText());

        MvcResult unsafe = call(get("/api/v1/auth/me"), admin(), null, "X-Request-ID", "bad value\r\ninjected");
        assertNotEquals("bad value\r\ninjected", unsafe.getResponse().getHeader("X-Request-ID"));
        assertDoesNotThrow(() -> UUID.fromString(unsafe.getResponse().getHeader("X-Request-ID")));
    }

    @Test
    void malformedRequestsReturnStable400Family() throws Exception {
        String admin = admin();
        problem(call(post("/api/v1/warehouses"), admin, "{not json"), 400, "MALFORMED_JSON");
        problem(call(post("/api/v1/warehouses"), admin, Map.of("code", "X" + suffix(), "name", "n", "unexpected", true)), 400, "MALFORMED_JSON");
        JsonNode validation = problem(call(post("/api/v1/warehouses"), admin, Map.of("code", "", "name", "")), 400, "VALIDATION_FAILED");
        assertTrue(validation.get("errors").has("code"));
        assertTrue(validation.get("errors").has("name"));
        problem(call(get("/api/v1/warehouses/not-a-uuid"), admin, null), 400, "INVALID_PARAMETER");
        problem(call(post("/api/v1/inventory/adjustments"), admin, Map.of("productId", UUID.randomUUID(), "warehouseId", UUID.randomUUID(),
            "direction", "INCREASE", "quantity", 1, "reason", "x")), 400, "MISSING_HEADER");
        problem(call(get("/api/v1/warehouses?sort=password,asc"), admin, null), 400, "INVALID_QUERY");
        // Spring normalizes negative page numbers to the first page instead of failing.
        assertEquals(0, httpGet(admin, "/api/v1/warehouses?page=-1", 200).get("page").get("number").asInt());
        assertEquals("no debe estar vacío", problem(call(post("/api/v1/warehouses"), admin, Map.of("code", "OK" + suffix(), "name", "")), 400, "VALIDATION_FAILED")
            .get("errors").get("name").asText());
        JsonNode clamped = httpGet(admin, "/api/v1/warehouses?size=1000", 200);
        assertEquals(100, clamped.get("page").get("size").asInt());

        MvcResult unsupported = mvc.perform(post("/api/v1/warehouses").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
            .contentType(MediaType.TEXT_PLAIN).content("code=x")).andReturn();
        problem(unsupported, 415, "UNSUPPORTED_MEDIA_TYPE");
        problem(call(delete("/api/v1/warehouses"), admin, null), 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void oversizedBodiesAreRejectedBeforeParsing() {
        String big = "{\"code\":\"" + "A".repeat(70_000) + "\",\"name\":\"x\"}";
        problem(call(post("/api/v1/warehouses"), admin(), big), 413, "PAYLOAD_TOO_LARGE");
    }

    @Test
    void loginIsRateLimitedPerClientAddressWithRetryAfterAndAudit() throws Exception {
        String ip = "192.0.2." + (int) (Math.random() * 200 + 10);
        MvcResult last = null;
        for (int i = 0; i < 6; i++) {
            last = loginAttempt("nobody", "Wrong-Pass-1!", r -> { r.setRemoteAddr(ip); return r; });
            if (i < 5) assertEquals(401, last.getResponse().getStatus());
        }
        problem(last, 429, "RATE_LIMIT_EXCEEDED");
        assertNotNull(last.getResponse().getHeader("Retry-After"));
        assertTrue(Integer.parseInt(last.getResponse().getHeader("Retry-After")) > 0);
        Integer audited = jdbc.queryForObject("select count(*) from audit_event where action='HTTP_RATE_LIMITED' and correlation_id=?",
            Integer.class, last.getResponse().getHeader("X-Request-ID"));
        assertEquals(1, audited);
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        MvcResult allowed = mvc.perform(options("/api/v1/sales").header("Origin", "http://localhost:4200")
            .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Authorization,Idempotency-Key")).andReturn();
        assertEquals(200, allowed.getResponse().getStatus());
        assertEquals("http://localhost:4200", allowed.getResponse().getHeader("Access-Control-Allow-Origin"));

        MvcResult denied = mvc.perform(options("/api/v1/sales").header("Origin", "https://evil.example")
            .header("Access-Control-Request-Method", "POST")).andReturn();
        assertEquals(403, denied.getResponse().getStatus());
        assertNull(denied.getResponse().getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void securityHeadersHealthInfoMetricsAndOpenApi() throws Exception {
        MvcResult health = mvc.perform(get("/actuator/health")).andReturn();
        assertEquals(200, health.getResponse().getStatus());
        JsonNode healthBody = json.readTree(health.getResponse().getContentAsString());
        assertEquals("UP", healthBody.get("status").asText());
        assertFalse(healthBody.has("components"), "health must not expose component details");
        assertEquals(200, mvc.perform(get("/actuator/health/readiness")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/actuator/health/liveness")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/actuator/info")).andReturn().getResponse().getStatus());
        assertEquals(403, call(get("/actuator/metrics"), token("VENDEDOR"), null).getResponse().getStatus());
        assertEquals(200, call(get("/actuator/metrics/inventary.outbox.events"), token("SOPORTE_TI"), null).getResponse().getStatus());

        MvcResult me = call(get("/api/v1/auth/me"), admin(), null);
        assertEquals("nosniff", me.getResponse().getHeader("X-Content-Type-Options"));
        assertEquals("DENY", me.getResponse().getHeader("X-Frame-Options"));

        JsonNode api = json.readTree(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString());
        for (String path : List.of("/api/v1/reports/inventory-balance", "/api/v1/sales", "/api/v1/whatsapp-orders/{id}/confirm-payment",
            "/api/v1/costing/policy", "/api/v1/catalog/products/{productId}/variants", "/api/v1/operations/outbox"))
            assertTrue(api.get("paths").has(path), "OpenAPI must document " + path);
    }

    private String token(JwtEncoder encoder, String subject, int version, String audience, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("inventary").audience(List.of(audience)).subject(subject)
            .issuedAt(expiresAt.minusSeconds(3600)).expiresAt(expiresAt).claim("ver", version).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void successfulSensitiveReadsAreAuditedWithoutCapturingResponseData() {
        String requestId = "sensitive-read-" + suffix();
        MvcResult response = call(get("/api/v1/admin/users"), admin(), null,
            "X-Request-ID", requestId);

        assertEquals(200, response.getResponse().getStatus());
        Integer events = jdbc.queryForObject("""
            select count(*) from audit_event
            where action='SENSITIVE_DATA_READ' and outcome='SUCCESS'
              and actor=? and correlation_id=? and metadata->>'path'='/api/v1/admin/users'
              and metadata->>'method'='GET' and metadata->>'status'='200'
            """, Integer.class, username("SUPER_ADMIN"), requestId);
        assertEquals(1, events, "successful sensitive reads must be correlated to the authenticated actor");

        Integer metadataFields = jdbc.queryForObject("""
            select (select count(*) from jsonb_object_keys(audit_event.metadata)) from audit_event
            where action='SENSITIVE_DATA_READ' and correlation_id=?
            """, Integer.class, requestId);
        assertEquals(3, metadataFields, "audit metadata must contain only method, path and status; never response or request data");
    }

}
