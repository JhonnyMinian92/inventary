package com.ecuapp.inventary.reports;

import com.ecuapp.inventary.common.InvalidQueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryBalanceReportServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    @Mock
    private InventoryBalanceReportRepository repository;
    private InventoryBalanceReportService service;

    @BeforeEach
    void setUp() {
        service = new InventoryBalanceReportService(repository, ZONE.getId());
    }

    @Test
    void rejectsReversedDateRangeWithoutQueryingRepository() {
        LocalDate today = LocalDate.now(ZONE);
        assertThrows(InvalidQueryException.class,
                () -> service.get(today, today.minusDays(1), UUID.randomUUID(), null, null, PageRequest.of(0, 50)));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsFutureEndDateWithoutQueryingRepository() {
        LocalDate today = LocalDate.now(ZONE);
        assertThrows(InvalidQueryException.class,
                () -> service.get(today, today.plusDays(1), UUID.randomUUID(), null, null, PageRequest.of(0, 50)));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsUnknownTimeZoneAndMissingWarehouse() {
        LocalDate today = LocalDate.now(ZONE);
        assertThrows(InvalidQueryException.class,
                () -> service.get(today, today, UUID.randomUUID(), null, "Mars/Olympus", PageRequest.of(0, 50)));
        assertThrows(InvalidQueryException.class,
                () -> service.get(today, today, null, null, null, PageRequest.of(0, 50)));
        verifyNoInteractions(repository);
    }

    @Test
    void limitsDateRangeTo366InclusiveCalendarDaysInBusinessTimeZone() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate first = today.minusDays(365);
        UUID warehouseId = UUID.randomUUID();
        when(repository.count(eq(warehouseId), isNull(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(repository.find(eq(warehouseId), isNull(), any(Instant.class), any(Instant.class),
                any(Instant.class), eq(50), eq(0L))).thenReturn(List.of());

        InventoryBalanceReport report = service.get(first, today, warehouseId, null, null, PageRequest.of(0, 50));

        assertEquals(first, report.from());
        assertEquals("America/Guayaquil", report.zone());
        verify(repository).find(eq(warehouseId), isNull(),
                eq(first.atStartOfDay(ZONE).toInstant()),
                eq(today.plusDays(1).atStartOfDay(ZONE).toInstant()),
                any(Instant.class), eq(50), eq(0L));
        assertThrows(InvalidQueryException.class,
                () -> service.get(first.minusDays(1), today, warehouseId, null, null, PageRequest.of(0, 50)));
    }

    @Test
    void capsPageSizeAndKeepsProductFilterAndOffset() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate first = today.minusDays(3);
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        InventoryBalanceReportRow row = new InventoryBalanceReportRow(productId, "SKU-1", "Producto",
                "USD", BigDecimal.ZERO, new BigDecimal("15.000"), new BigDecimal("30.00"),
                new BigDecimal("10.000"), new BigDecimal("50.00"), new BigDecimal("5.000"),
                new BigDecimal("5.000"), BigDecimal.ZERO, new BigDecimal("5.000"),
                BigDecimal.ZERO, new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));
        when(repository.count(eq(warehouseId), eq(productId), any(Instant.class), any(Instant.class))).thenReturn(201L);
        when(repository.find(eq(warehouseId), eq(productId), any(Instant.class), any(Instant.class),
                any(Instant.class), eq(100), eq(200L))).thenReturn(List.of(row));

        InventoryBalanceReport report = service.get(first, today, warehouseId, productId, "UTC", PageRequest.of(2, 250));

        assertEquals(100, report.size());
        assertEquals(3, report.totalPages());
        assertEquals("UTC", report.zone());
        assertEquals(productId, report.items().get(0).productId());
    }
}
