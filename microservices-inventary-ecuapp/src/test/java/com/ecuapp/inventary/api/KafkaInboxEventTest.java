package com.ecuapp.inventary.api;

import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.sales.KafkaInboxEventService;
import com.ecuapp.inventary.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KafkaInboxEventTest extends ApiTestSupport {
    @Autowired KafkaInboxEventService inbox;
    @Autowired CacheManager cacheManager;

    @Test
    void duplicateEventIsIgnoredButSameIdWithDifferentContentIsRejected() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        String original = envelope(eventId, aggregateId, 12.50);
        Cache publicProducts = cacheManager.getCache("publicProductList");
        if (publicProducts != null) publicProducts.put("stale", "cached");

        assertEquals(KafkaInboxEventService.Result.PROCESSED, inbox.process(original));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM kafka_inbox_event WHERE event_id = ?", Integer.class, eventId));
        if (publicProducts != null) assertNull(publicProducts.get("stale"), "A new domain event must invalidate the public catalog cache.");

        if (publicProducts != null) publicProducts.put("fresh", "keep");
        assertEquals(KafkaInboxEventService.Result.DUPLICATE, inbox.process(original));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM kafka_inbox_event WHERE event_id = ?", Integer.class, eventId));
        if (publicProducts != null) assertEquals("keep", publicProducts.get("fresh").get(), "A duplicate must not repeat side effects.");

        String modified = envelope(eventId, aggregateId, 99.99);
        assertThrows(ConflictException.class, () -> inbox.process(modified),
            "Reusing an event ID with different content must never be treated as a harmless retry.");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM kafka_inbox_event WHERE event_id = ?", Integer.class, eventId));
    }

    @Test
    void malformedOrOversizedEventsAreRejectedBeforeTheyCanMutateTheInbox() {
        assertThrows(IllegalArgumentException.class, () -> inbox.process("{not-json"));
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        String tooLarge = envelope(eventId, aggregateId, 1.0) + " ".repeat(1_048_576);
        assertThrows(IllegalArgumentException.class, () -> inbox.process(tooLarge));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM kafka_inbox_event WHERE event_id = ?", Integer.class, eventId));
    }

    private String envelope(UUID eventId, UUID aggregateId, double amount) {
        return """
            {"eventId":"%s","aggregateType":"SALE","aggregateId":"%s","eventType":"SALE_CREATED","occurredAt":"2026-10-27T00:00:00Z","payload":{"saleId":"%s","total":%s}}
            """.formatted(eventId, aggregateId, aggregateId, amount).trim();
    }
}
