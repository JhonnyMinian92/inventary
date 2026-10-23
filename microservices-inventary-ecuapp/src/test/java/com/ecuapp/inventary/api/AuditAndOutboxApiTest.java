package com.ecuapp.inventary.api;

import com.ecuapp.inventary.sales.OutboxRelay;
import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class AuditAndOutboxApiTest extends ApiTestSupport {
    @Autowired OutboxRelay relay;

    @Test
    void auditEventsAreFilterableReadOnlyAndNeverContainSecrets() {
        String warehouseCode = "AUD" + suffix();
        JsonNode warehouse = httpPost(admin(), "/api/v1/warehouses", Map.of("code", warehouseCode, "name", "Auditado"), 201);
        String auditor = token("AUDITOR");
        JsonNode events = httpGet(auditor, "/api/v1/audit-events?action=WAREHOUSE_CREATED&resourceType=WAREHOUSE&actor=" + username("SUPER_ADMIN")
            + "&outcome=SUCCESS&from=" + Instant.now().minusSeconds(600) + "&size=100", 200);
        JsonNode match = null;
        for (JsonNode e : events.get("content")) if (warehouse.get("id").asText().equals(e.get("resourceId").asText())) match = e;
        assertNotNull(match, "creation must be audited");
        assertEquals(warehouseCode, match.get("metadata").get("code").asText());
        assertEquals(match.get("id").asText(), httpGet(auditor, "/api/v1/audit-events/" + match.get("id").asText(), 200).get("id").asText());

        MvcResult denied = call(get("/api/v1/admin/users"), auditor, null, "X-Request-ID", "audit-corr-" + suffix());
        problem(denied, 403, "ACCESS_DENIED");
        JsonNode failures = httpGet(auditor, "/api/v1/audit-events?action=HTTP_REQUEST_FAILED&outcome=DENIED&actor=" + username("AUDITOR") + "&size=100", 200);
        boolean correlated = false;
        for (JsonNode e : failures.get("content"))
            if (denied.getResponse().getHeader("X-Request-ID").equals(e.path("correlationId").asText())) correlated = true;
        assertTrue(correlated, "failed requests are audited with their request id");

        problem(call(get("/api/v1/audit-events"), token("VENDEDOR"), null), 403, "ACCESS_DENIED");
        problem(call(get("/api/v1/audit-events?outcome=MAYBE"), auditor, null), 400, "VALIDATION_FAILED");
        problem(call(get("/api/v1/audit-events?from=2026-10-02T00:00:00Z&to=2026-10-01T00:00:00Z"), auditor, null), 400, "INVALID_QUERY");
        assertEquals(100, httpGet(auditor, "/api/v1/audit-events?size=500", 200).get("page").get("size").asInt());
        problem(call(post("/api/v1/audit-events"), admin(), Map.of()), 405, "METHOD_NOT_ALLOWED");
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_event where metadata::text ilike '%bearer%' or metadata::text ilike '%Test-Pass-2026%'"
            + " or metadata::text ilike '%" + INTEGRATION_KEY + "%'", Integer.class));
    }

    @Test
    void relayPublishesRetriesFailsAndCanBeRedriven() {
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "2.00").get("id").asText();
        stock(productId, warehouseId, "2", "1.00");
        String saleId = sale(token("VENDEDOR"), warehouseId, productId, "1").get("id").asText();
        UUID eventId = jdbc.queryForObject("select id from outbox_event where aggregate_id=?::uuid and event_type='SALE_COMPLETED'", UUID.class, saleId);

        // Delivery fails for our event: it is rescheduled with backoff and the error is recorded.
        relay.relay((key, json) -> { if (key.equals(eventId.toString())) throw new IllegalStateException("broker down"); }, 10_000, Duration.ofSeconds(30));
        Map<String, Object> row = jdbc.queryForMap("select status, attempts, last_error, next_attempt_at from outbox_event where id=?", eventId);
        assertEquals("PENDING", row.get("status"));
        assertEquals(1, row.get("attempts"));
        assertEquals("IllegalStateException", row.get("last_error"));

        // After the last allowed attempt the event becomes FAILED and needs a manual redrive.
        jdbc.update("update outbox_event set attempts=9, next_attempt_at=now() where id=?", eventId);
        relay.relay((key, json) -> { if (key.equals(eventId.toString())) throw new IllegalStateException("broker down"); }, 10_000, Duration.ofSeconds(30));
        assertEquals("FAILED", jdbc.queryForObject("select status from outbox_event where id=?", String.class, eventId));

        String ops = token("SOPORTE_TI");
        JsonNode stats = httpGet(ops, "/api/v1/operations/outbox/stats", 200);
        assertTrue(stats.get("failed").asLong() >= 1);
        JsonNode failed = httpGet(ops, "/api/v1/operations/outbox?status=failed&aggregateType=sale&aggregateId=" + saleId, 200);
        assertEquals(1, failed.get("page").get("totalElements").asInt());
        problem(call(get("/api/v1/operations/outbox?status=NOPE"), ops, null), 400, "INVALID_QUERY");
        problem(call(get("/api/v1/operations/outbox/stats"), token("VENDEDOR"), null), 403, "ACCESS_DENIED");

        JsonNode requeued = httpPost(ops, "/api/v1/operations/outbox/" + eventId + "/retry", null, 200);
        assertEquals("PENDING", requeued.get("status").asText());
        assertEquals(0, requeued.get("attempts").asInt());
        List<String> sent = new CopyOnWriteArrayList<>();
        relay.relay((key, json) -> {
            sent.add(key);
            if (key.equals(eventId.toString())) {
                JsonNode envelope = this.json.readTree(json);
                assertEquals(eventId.toString(), envelope.get("eventId").asText());
                assertEquals("SALE_COMPLETED", envelope.get("eventType").asText());
            }
        }, 10_000, Duration.ofSeconds(30));
        assertTrue(sent.contains(eventId.toString()));
        assertEquals("PUBLISHED", jdbc.queryForObject("select status from outbox_event where id=?", String.class, eventId));
        problem(call(post("/api/v1/operations/outbox/" + eventId + "/retry"), ops, null), 409, "CONFLICT");
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where resource_id=? and action='OUTBOX_EVENT_REQUEUED'", Integer.class, eventId));
    }

    @Test
    void expiredLeasesAreReclaimedAndConcurrentRelaysNeverSendTheSameEventTwice() throws Exception {
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "2.00").get("id").asText();
        stock(productId, warehouseId, "20", "1.00");
        List<UUID> ours = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String saleId = sale(token("VENDEDOR"), warehouseId, productId, "1").get("id").asText();
            ours.add(jdbc.queryForObject("select id from outbox_event where aggregate_id=?::uuid and event_type='SALE_COMPLETED'", UUID.class, saleId));
        }
        // Simulates a node that crashed after claiming: PROCESSING with an expired lease.
        jdbc.update("update outbox_event set status='PROCESSING', locked_until=now() - interval '1 minute' where id=?", ours.get(0));

        Map<String, Integer> sends = new ConcurrentHashMap<>();
        OutboxRelay.Sender counting = (key, json) -> { sends.merge(key, 1, Integer::sum); Thread.sleep(2); };
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> runs = new ArrayList<>();
        for (int i = 0; i < 3; i++) runs.add(pool.submit(() -> { start.await(); return relay.relay(counting, 25, Duration.ofSeconds(60)); }));
        start.countDown();
        for (Future<Integer> run : runs) run.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        // Drain whatever is left after the concurrent rounds.
        while (relay.relay(counting, 500, Duration.ofSeconds(60)) > 0) { }
        for (UUID id : ours) {
            assertEquals(1, sends.getOrDefault(id.toString(), 0), "event " + id + " must be sent exactly once");
            assertEquals("PUBLISHED", jdbc.queryForObject("select status from outbox_event where id=?", String.class, id));
        }
        assertTrue(sends.values().stream().allMatch(count -> count == 1), "no event may be sent twice by concurrent relays");
    }
}
