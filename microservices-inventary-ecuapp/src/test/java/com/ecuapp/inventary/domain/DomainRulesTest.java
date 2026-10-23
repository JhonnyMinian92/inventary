package com.ecuapp.inventary.domain;

import com.ecuapp.inventary.auth.PasswordPolicy;
import com.ecuapp.inventary.catalog.AttributeSchemaValidator;
import com.ecuapp.inventary.catalog.CategoryAttributeDefinition;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.InvalidQueryException;
import com.ecuapp.inventary.common.Pages;
import com.ecuapp.inventary.reports.ReportPeriod;
import com.ecuapp.inventary.sales.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainRulesTest {

    @Test
    void passwordPolicyRequiresComplexityAndExcludesUsername() {
        PasswordPolicy policy = new PasswordPolicy();
        assertDoesNotThrow(() -> policy.validateForUser("Segura-Clave-2026!", "maria"));
        assertThrows(BusinessRuleException.class, () -> policy.validateForUser("corta1!A", "maria"));
        assertThrows(BusinessRuleException.class, () -> policy.validateForUser("sin-mayusculas-2026!", "maria"));
        assertThrows(BusinessRuleException.class, () -> policy.validateForUser("SinNumeros-Clave!", "maria"));
        assertThrows(BusinessRuleException.class, () -> policy.validateForUser("SinSimbolos2026Clave", "maria"));
        assertThrows(BusinessRuleException.class, () -> policy.validateForUser("Maria-Clave-2026!", "maria"));
        assertThrows(BusinessRuleException.class, () -> policy.validate("A".repeat(129) + "a1!", 12));
    }

    @Test
    void pagesClampSizeMapAllowlistedSortAndRejectUnknownProperties() {
        Pageable mapped = Pages.of(PageRequest.of(2, 500, Sort.by(Sort.Direction.DESC, "sku")), Map.of("sku", "product.sku"), Sort.by("name"));
        assertEquals(100, mapped.getPageSize());
        assertEquals(2, mapped.getPageNumber());
        assertEquals(Sort.Direction.DESC, mapped.getSort().getOrderFor("product.sku").getDirection());
        assertEquals(Sort.by("name"), Pages.of(PageRequest.of(0, 0 + 1), Map.of(), Sort.by("name")).getSort());
        assertThrows(InvalidQueryException.class, () -> Pages.of(PageRequest.of(0, 10, Sort.by("password")), Map.of("sku", "sku"), Sort.unsorted()));
    }

    @Test
    void attributeValidatorChecksTypesOptionsAndRanges() {
        AttributeSchemaValidator validator = new AttributeSchemaValidator();
        assertThrows(BusinessRuleException.class, () -> validator.validateDefinition("Bad Key", CategoryAttributeDefinition.ValueType.STRING, null, null));
        assertThrows(BusinessRuleException.class, () -> validator.validateDefinition("tipo", CategoryAttributeDefinition.ValueType.ENUM, null, List.of()));
        assertThrows(BusinessRuleException.class, () -> validator.validateDefinition("tipo", CategoryAttributeDefinition.ValueType.NUMBER, Map.of("min", "x"), null));
        assertThrows(BusinessRuleException.class, () -> validator.validateDefinition("tipo", CategoryAttributeDefinition.ValueType.STRING, Map.of("pattern", "(["), null));
        assertThrows(BusinessRuleException.class, () -> validator.validateDefinition("tipo", CategoryAttributeDefinition.ValueType.ENUM, null, List.of("a", "a")));
        assertDoesNotThrow(() -> validator.validateDefinition("tipo", CategoryAttributeDefinition.ValueType.ENUM, null, List.of("a", "b")));
        // Without a schema only simple scalar values are accepted.
        assertEquals(Map.of("marca", "ACME"), validator.validateValues(Map.of("marca", "ACME"), List.of(), true));
        assertThrows(BusinessRuleException.class, () -> validator.validateValues(Map.of("lista", List.of("x")), List.of(), true));
    }

    @Test
    void reportPeriodUsesBusinessTimeZoneBoundaries() {
        ZoneId zone = ZoneId.of("America/Guayaquil");
        LocalDate day = LocalDate.now(zone).minusDays(1);
        ReportPeriod period = ReportPeriod.of(day, day, null, zone);
        assertEquals(day.atStartOfDay(zone).toInstant(), period.fromInclusive());
        assertEquals(day.plusDays(1).atStartOfDay(zone).toInstant(), period.toExclusive());
        assertEquals(Instant.parse(day + "T05:00:00Z"), period.fromInclusive(), "Ecuador is UTC-5");
        assertThrows(InvalidQueryException.class, () -> ReportPeriod.of(day, day, "Invalid/Zone", zone));
        assertThrows(InvalidQueryException.class, () -> ReportPeriod.of(null, day, null, zone));
    }

    @Test
    void outboxBackoffFailsAfterMaxAttemptsAndCanBeRequeued() {
        OutboxEvent event = new OutboxEvent("SALE", UUID.randomUUID(), "SALE_COMPLETED", Map.of("k", "v"));
        Instant before = Instant.now();
        event.markFailed("Timeout");
        assertEquals("PENDING", event.getStatus());
        assertTrue(event.getNextAttemptAt().isAfter(before));
        for (int i = 1; i < OutboxEvent.MAX_ATTEMPTS; i++) event.markFailed("Timeout");
        assertEquals("FAILED", event.getStatus());
        assertTrue(event.requeue());
        assertEquals("PENDING", event.getStatus());
        assertEquals(0, event.getAttempts());
        event.markPublished();
        assertFalse(event.requeue(), "published events are never replayed from the outbox");
    }

    @Test
    void domainExceptionRequiresStableCode() {
        assertThrows(NullPointerException.class, () -> new DomainException(null, "x"));
        assertEquals(DomainErrorCode.INSUFFICIENT_STOCK, new DomainException(DomainErrorCode.INSUFFICIENT_STOCK, "x").code());
    }
}
