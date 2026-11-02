package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;\nimport java.util.concurrent.CountDownLatch;\nimport java.util.concurrent.ExecutorService;\nimport java.util.concurrent.Executors;\nimport java.util.concurrent.Future;\nimport java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class LoginEncryptionApiTest extends ApiTestSupport {

    @Test
    void challengeIsPublicUncacheableAndUsesAFreshP256Key() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login-challenge").with(randomIp())).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(String.valueOf(result.getResponse().getHeader("Cache-Control")).contains("no-store"));
        JsonNode first = json.readTree(result.getResponse().getContentAsString());
        JsonNode second = loginChallenge(randomIp());
        assertEquals("ECDH-ES+HKDF-SHA256+A256GCM", first.get("algorithm").asText());
        assertNotEquals(first.get("challengeId").asText(), second.get("challengeId").asText());
        assertNotEquals(first.get("publicKey").asText(), second.get("publicKey").asText());
        assertEquals(91, Base64.getDecoder().decode(first.get("publicKey").asText()).length, "P-256 SubjectPublicKeyInfo");
        assertFalse(first.get("expiresAt").asText().isBlank());
    }

    @Test
    void encryptedLoginSucceedsOnceAndCannotBeReplayed() throws Exception {
        String username = createUser("VENDEDOR");
        RequestPostProcessor client = randomIp();
        String body = json.writeValueAsString(Map.of("encrypted", encryptCredentials(loginChallenge(client), username, PASSWORD)));
        JsonNode token = expect(postLogin(body, client), 200);
        assertFalse(token.get("accessToken").asText().isBlank());
        httpGet(token.get("accessToken").asText(), "/api/v1/auth/me", 200);
        problem(postLogin(body, randomIp()), 400, "LOGIN_CHALLENGE_INVALID");
    }

    @Test
    void plaintextMixedTamperedAndUnknownChallengesAreRejected() throws Exception {
        String username = createUser("VENDEDOR");
        problem(postLogin(json.writeValueAsString(Map.of("username", username, "password", PASSWORD)), randomIp()), 400, "LOGIN_ENCRYPTION_REQUIRED");

        Map<String, String> encrypted = encryptCredentials(loginChallenge(randomIp()), username, PASSWORD);
        problem(postLogin(json.writeValueAsString(Map.of("username", username, "encrypted", encrypted)), randomIp()), 400, "LOGIN_PAYLOAD_INVALID");

        Map<String, String> tampered = new HashMap<>(encryptCredentials(loginChallenge(randomIp()), username, PASSWORD));
        byte[] ciphertext = Base64.getDecoder().decode(tampered.get("ciphertext"));
        ciphertext[0] ^= 0x01;
        tampered.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext));
        problem(postLogin(json.writeValueAsString(Map.of("encrypted", tampered)), randomIp()), 400, "LOGIN_CHALLENGE_INVALID");

        Map<String, String> unknown = new HashMap<>(encryptCredentials(loginChallenge(randomIp()), username, PASSWORD));
        unknown.put("challengeId", "desafio-inexistente");
        problem(postLogin(json.writeValueAsString(Map.of("encrypted", unknown)), randomIp()), 400, "LOGIN_CHALLENGE_INVALID");

        problem(postLogin(json.writeValueAsString(Map.of("encrypted", Map.of("challengeId", "x"))), randomIp()), 400, "VALIDATION_FAILED");
    }

    @Test
    void repeatedFailuresLockTheAccountTemporarilyEvenWithTheRightPassword() {
        String username = createUser("VENDEDOR");
        for (int i = 0; i < 5; i++) problem(loginAttempt(username, "Wrong-Pass-123!"), 401, "INVALID_CREDENTIALS");
        MvcResult locked = loginAttempt(username, PASSWORD);
        problem(locked, 429, "LOGIN_TEMPORARILY_LOCKED");
        assertTrue(Integer.parseInt(locked.getResponse().getHeader("Retry-After")) > 0);
        Integer audited = jdbc.queryForObject("select count(*) from audit_event where action='USER_LOGIN_LOCKED' and actor=?", Integer.class, username);
        assertEquals(1, audited);
        // Unknown accounts lock the same way, so a lockout does not reveal whether a username exists.
        String ghost = "ghost-" + suffix().toLowerCase();
        for (int i = 0; i < 5; i++) problem(loginAttempt(ghost, "Wrong-Pass-123!"), 401, "INVALID_CREDENTIALS");
        problem(loginAttempt(ghost, "Wrong-Pass-123!"), 429, "LOGIN_TEMPORARILY_LOCKED");
    }


    @Test
    void concurrentFailuresCannotBypassTheAccountLockout() throws Exception {
        String username = createUser("VENDEDOR");
        int parallelAttempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(parallelAttempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> attempts = new java.util.ArrayList<>();
            for (int i = 0; i < parallelAttempts; i++) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    return loginAttempt(username, "Wrong-Pass-123!", randomIp()).getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> attempt : attempts) {
                int status = attempt.get(45, TimeUnit.SECONDS);
                assertTrue(status == 401 || status == 429, "unexpected concurrent login status: " + status);
            }
            problem(loginAttempt(username, PASSWORD), 429, "LOGIN_TEMPORARILY_LOCKED");
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private MvcResult postLogin(String body, RequestPostProcessor client) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(client).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }
}
