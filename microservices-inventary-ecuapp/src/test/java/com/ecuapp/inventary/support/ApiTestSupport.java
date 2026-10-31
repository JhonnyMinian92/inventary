package com.ecuapp.inventary.support;

import com.ecuapp.inventary.auth.AppUser;
import com.ecuapp.inventary.auth.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Base class for HTTP contract tests: real Spring context, real filters (correlation, rate limit, audit, security),
 * real JWT issued by POST /api/v1/auth/login and a real PostgreSQL database. Every test creates its own data
 * with random business keys, so suites can run repeatedly against the same database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {
    public static final String PASSWORD = "Test-Pass-2026-Strong!";
    public static final String INTEGRATION_KEY = "test-integration-key-0123456789-abcdefghij";
    private static final Map<String, String> ROLE_TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, String> ROLE_USERS = new ConcurrentHashMap<>();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected AppUserRepository users;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JdbcTemplate jdbc;

    // ---------------------------------------------------------------- identities

    /** Shared user per role (created once per JVM). */
    protected String token(String role) {
        return ROLE_TOKENS.computeIfAbsent(role, r -> {
            String username = createUser(r);
            ROLE_USERS.put(r, username);
            return login(username, PASSWORD);
        });
    }

    protected String username(String role) {
        token(role);
        return ROLE_USERS.get(role);
    }

    protected String admin() { return token("SUPER_ADMIN"); }

    /** Creates a brand-new user with the given role (useful for segregation-of-duties tests). */
    protected String createUser(String role) {
        String username = ("t-" + role.toLowerCase(Locale.ROOT).replace('_', '-') + "-" + suffix()).toLowerCase(Locale.ROOT);
        AppUser user=users.saveAndFlush(new AppUser(username, encoder.encode(PASSWORD), role));
        jdbc.update("""
            insert into user_warehouse_scope(id,user_id,warehouse_id,reason,granted_by,valid_from,active,created_at)
            select gen_random_uuid(),?,w.id,'Ámbito de fixture automatizado','TEST_FIXTURE',now(),true,now()
            from warehouse w
            on conflict (user_id,warehouse_id) where active=true do nothing
            """,user.getId());
        return username;
    }

    protected String newUserToken(String role) {
        return login(createUser(role), PASSWORD);
    }

    protected String login(String username, String password) {
        try {
            MvcResult result = loginAttempt(username, password);
            assertEquals(200, result.getResponse().getStatus(), () -> "login failed: " + body(result));
            return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Full browser flow: single-use challenge + encrypted credentials, from a random client address. */
    protected MvcResult loginAttempt(String username, String password) {
        return loginAttempt(username, password, randomIp());
    }

    protected MvcResult loginAttempt(String username, String password, RequestPostProcessor client) {
        try {
            Map<String, Object> body = Map.of("encrypted", encryptCredentials(loginChallenge(client), username, password));
            return mvc.perform(post("/api/v1/auth/login").with(client).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected JsonNode loginChallenge(RequestPostProcessor client) {
        try {
            MvcResult result = mvc.perform(post("/api/v1/auth/login-challenge").with(client)).andReturn();
            assertEquals(200, result.getResponse().getStatus(), () -> "challenge failed: " + body(result));
            return json.readTree(result.getResponse().getContentAsString());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Client half of ECDH-ES+HKDF-SHA256+A256GCM, written independently of the server class (javax.crypto only)
     * so the tests check the wire contract that the browser implements with WebCrypto.
     */
    protected Map<String, String> encryptCredentials(JsonNode challenge, String username, String password) throws Exception {
        String challengeId = challenge.get("challengeId").asText();
        assertEquals("ECDH-ES+HKDF-SHA256+A256GCM", challenge.get("algorithm").asText());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair client = generator.generateKeyPair();
        PublicKey server = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(challenge.get("publicKey").asText())));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(client.getPrivate());
        agreement.doPhase(server, true);
        byte[] salt = challengeId.getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(agreement.generateSecret());
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update("inventary/login/v1".getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) 1);
        byte[] key = mac.doFinal();
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(salt);
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);
        byte[] ciphertext = cipher.doFinal(json.writeValueAsBytes(credentials));
        Base64.Encoder b64 = Base64.getEncoder();
        return Map.of("challengeId", challengeId, "publicKey", b64.encodeToString(client.getPublic().getEncoded()),
            "iv", b64.encodeToString(iv), "ciphertext", b64.encodeToString(ciphertext));
    }

    protected static RequestPostProcessor randomIp() {
        String ip = "10." + ThreadLocalRandom.current().nextInt(1, 250) + "." + ThreadLocalRandom.current().nextInt(1, 250) + "."
            + ThreadLocalRandom.current().nextInt(1, 250);
        return request -> { request.setRemoteAddr(ip); return request; };
    }

    protected static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    protected static String key() { return "it-" + UUID.randomUUID(); }

    // ---------------------------------------------------------------- HTTP helpers

    protected MvcResult call(MockHttpServletRequestBuilder request, String token, Object body, String... headers) {
        try {
            if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            for (int i = 0; i + 1 < headers.length; i += 2) request.header(headers[i], headers[i + 1]);
            if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
            return mvc.perform(request).andReturn();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected JsonNode expect(MvcResult result, int status) {
        assertEquals(status, result.getResponse().getStatus(), () -> result.getRequest().getMethod() + " " + result.getRequest().getRequestURI()
            + " -> " + body(result));
        try {
            String content = result.getResponse().getContentAsString();
            return content.isBlank() ? json.nullNode() : json.readTree(content);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected JsonNode httpGet(String token, String url, int status) { return expect(call(get(url), token, null), status); }
    protected JsonNode httpPost(String token, String url, Object body, int status, String... headers) { return expect(call(post(url), token, body, headers), status); }
    protected JsonNode httpPut(String token, String url, Object body, int status, String... headers) { return expect(call(put(url), token, body, headers), status); }
    protected JsonNode httpPatch(String token, String url, Object body, int status) { return expect(call(patch(url), token, body), status); }
    protected JsonNode httpDelete(String token, String url, int status) { return expect(call(delete(url), token, null), status); }

    /** Asserts the RFC 9457 contract: problem+json, status, stable code, request id and no internal details. */
    protected JsonNode problem(MvcResult result, int status, String code) {
        JsonNode body = expect(result, status);
        assertTrue(Objects.requireNonNullElse(result.getResponse().getContentType(), "").startsWith("application/problem+json"),
            () -> "content type was " + result.getResponse().getContentType());
        assertEquals(status, body.get("status").asInt());
        if (code != null) assertEquals(code, body.path("code").asText(), () -> body.toString());
        assertFalse(body.path("requestId").asText().isBlank(), "requestId must be present");
        String text = body.toString().toLowerCase(Locale.ROOT);
        assertFalse(text.contains("exception") || text.contains("sql") || text.contains("stacktrace") || text.contains("hibernate"),
            () -> "problem leaks internals: " + text);
        return body;
    }

    protected static String body(MvcResult result) {
        try { return result.getResponse().getContentAsString(); } catch (Exception ex) { return "<unreadable>"; }
    }

    protected static BigDecimal dec(JsonNode node) {
        assertNotNull(node, "missing numeric value");
        assertFalse(node.isMissingNode() || node.isNull(), "missing numeric value");
        return new BigDecimal(node.asText());
    }

    protected static void assertAmount(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(dec(actual)), () -> "expected " + expected + " but was " + actual);
    }

    protected static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    // ---------------------------------------------------------------- data builders (through the public API)

    protected JsonNode category() {
        return httpPost(admin(), "/api/v1/catalog/categories", Map.of("name", "Cat " + suffix(), "description", "test"), 201);
    }

    protected JsonNode product(String categoryId, String cost, String price) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sku", "SKU-" + suffix());
        body.put("name", "Producto " + suffix());
        body.put("categoryId", categoryId);
        body.put("costPrice", new BigDecimal(cost));
        body.put("salePrice", new BigDecimal(price));
        return httpPost(admin(), "/api/v1/catalog/products", body, 201);
    }

    protected JsonNode product(String cost, String price) { return product(category().get("id").asText(), cost, price); }

    protected void ensureWarehouseScope(String role,String warehouseId) {
        UUID userId=users.findByUsername(username(role)).orElseThrow().getId();
        jdbc.update("""
            insert into user_warehouse_scope(id,user_id,warehouse_id,reason,granted_by,valid_from,active,created_at)
            values(gen_random_uuid(),?,?,'Ámbito explícito de fixture de integración','TEST_FIXTURE',now(),true,now())
            on conflict (user_id,warehouse_id) where active=true do nothing
            """,userId,UUID.fromString(warehouseId));
    }

    protected JsonNode warehouse() {
        JsonNode created=httpPost(admin(), "/api/v1/warehouses", Map.of("code", "WH" + suffix(), "name", "Almacén " + suffix()), 201);
        for(String role:List.of("AUDITOR","MANAGER_ALMACEN","VENDEDOR")) ensureWarehouseScope(role,created.get("id").asText());
        return created;
    }

    protected JsonNode adjust(String productId, String warehouseId, String direction, String quantity, String unitCost) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("warehouseId", warehouseId);
        body.put("direction", direction);
        body.put("quantity", new BigDecimal(quantity));
        body.put("reason", "Ajuste de prueba");
        if (unitCost != null) body.put("unitCost", new BigDecimal(unitCost));
        return httpPost(admin(), "/api/v1/inventory/adjustments", body, 201, "Idempotency-Key", key());
    }

    protected JsonNode stock(String productId, String warehouseId, String quantity, String unitCost) {
        return adjust(productId, warehouseId, "INCREASE", quantity, unitCost);
    }

    protected JsonNode supplier() {
        return httpPost(admin(), "/api/v1/suppliers", Map.of("code", "SUP" + suffix(), "name", "Proveedor " + suffix()), 201);
    }

    protected JsonNode customer() {
        return httpPost(admin(), "/api/v1/customers", Map.of("code", "CLI" + suffix(), "name", "Cliente " + suffix(), "phone", "0999999999"), 201);
    }

    protected JsonNode sale(String token, String warehouseId, String productId, String quantity) {
        return httpPost(token, "/api/v1/sales", Map.of("warehouseId", warehouseId, "channel", "POS",
            "items", List.of(Map.of("productId", productId, "quantity", new BigDecimal(quantity)))), 201, "Idempotency-Key", key());
    }

    protected JsonNode balance(String productId, String warehouseId) {
        return httpGet(admin(), "/api/v1/inventory/balances/" + productId + "/" + warehouseId, 200);
    }

    protected BigDecimal onHand(String productId, String warehouseId) {
        return dec(balance(productId, warehouseId).get("onHand"));
    }

    /** Creates, submits and approves a purchase order (approval by a distinct SUPER_ADMIN). Returns the order JSON. */
    protected JsonNode approvedOrder(String supplierId, String warehouseId, List<Map<String, Object>> lines) {
        String creator = token("MANAGER_ALMACEN");
        JsonNode order = httpPost(creator, "/api/v1/purchase-orders", Map.of("supplierId", supplierId, "warehouseId", warehouseId, "items", lines),
            201, "Idempotency-Key", key());
        String id = order.get("id").asText();
        httpPost(creator, "/api/v1/purchase-orders/" + id + "/submit", null, 200);
        return httpPost(admin(), "/api/v1/purchase-orders/" + id + "/approve", null, 200);
    }

    protected JsonNode receive(String orderId, List<Map<String, Object>> lines) {
        return httpPost(token("MANAGER_ALMACEN"), "/api/v1/purchase-orders/" + orderId + "/receipts", Map.of("items", lines), 201, "Idempotency-Key", key());
    }

    protected static Map<String, Object> line(String field, String id, String quantity) {
        return Map.of(field, id, "quantity", new BigDecimal(quantity));
    }
}
