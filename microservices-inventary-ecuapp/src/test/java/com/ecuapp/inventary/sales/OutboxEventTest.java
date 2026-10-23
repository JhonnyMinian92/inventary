package com.ecuapp.inventary.sales;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventTest {
    @Test
    void claimPublishesOutsidePendingStateAndFailedAttemptReturnsToBackoff() {
        OutboxEvent event = new OutboxEvent("SALE", UUID.randomUUID(), "SALE_CREATED",
                Map.of("saleNumber", "VTA-1"));
        Instant lease = Instant.now().plus(Duration.ofMinutes(10));

        assertTrue(event.claim(lease));

        assertEquals("PROCESSING", event.getStatus());
        assertEquals(1, event.getAttempts());
        assertEquals(lease, event.getNextAttemptAt());

        event.markFailed();

        assertEquals("PENDING", event.getStatus());
        assertEquals(1, event.getAttempts(), "Claim increments attempts exactly once");
        assertTrue(event.getNextAttemptAt().isAfter(Instant.now()));
    }

    @Test
    void publishedEventsCannotBePublishedTwiceWithoutANewClaim() {
        OutboxEvent event = new OutboxEvent("SALE", UUID.randomUUID(), "SALE_CREATED",
                Map.of("saleNumber", "VTA-2"));
        event.claim(Instant.now().plus(Duration.ofMinutes(10)));
        event.markPublished();

        assertEquals("PUBLISHED", event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertThrows(IllegalStateException.class, () -> event.markPublished());
    }

    @Test
    void activeLeasePreventsDuplicateClaim() {
        OutboxEvent event = new OutboxEvent("SALE", UUID.randomUUID(), "SALE_CREATED",
                Map.of("saleNumber", "VTA-3"));
        event.claim(Instant.now().plus(Duration.ofMinutes(10)));

        assertThrows(IllegalStateException.class,
                () -> event.claim(Instant.now().plus(Duration.ofMinutes(20))));
    }
}
