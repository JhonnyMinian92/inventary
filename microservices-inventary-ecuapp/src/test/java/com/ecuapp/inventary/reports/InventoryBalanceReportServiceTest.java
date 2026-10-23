package com.ecuapp.inventary.reports;

import com.ecuapp.inventary.common.InvalidQueryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryBalanceReportServiceTest {
    @Mock
    private InventoryBalanceReportRepository repository;

    @InjectMocks
    private InventoryBalanceReportService service;

    @Test
    void rejectsReversedDateRangeWithoutQueryingRepository() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThrows(InvalidQueryException.class,
                () -> service.get(today, today.minusDays(1), UUID.randomUUID(), null, PageRequest.of(0, 50)));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsFutureEndDateWithoutQueryingRepository() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThrows(InvalidQueryException.class,
                () -> service.get(today, today.plusDays(1), UUID.randomUUID(), null, PageRequest.of(0, 50)));
        verifyNoInteractions(repository);
    }

    @Test
    void limitsDateRangeTo366InclusiveCalendarDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate first = today.minusDays(365);
        UUID warehouseId = UUID.randomUUID();
        when(repository.count(eq(warehouseId), isNull(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(repository.find(eq(warehouseId), isNull(), any(Instant.class), any(Instant.class),
                any(Instant.class), eq(50), eq(0L))).thenReturn(List.of());

        InventoryBalanceReport report = service.get(first, today, warehouseId, null, PageRequest.of(0, 50));

        assertEquals(first, report.from());
        assertEquals(today, report.to());
        assertEquals(0, report.totalElements());
        verify(repository).find(eq(warehouseId), isNull(),
                eq(first.atStartOfDay(ZoneOffset.UTC).toInstant()),
                eq(today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()),
                any(Instant.class), eq(50), eq(0L));
    }

    @Test
    void capsPageSizeAndKeepsProductFilterAndOffset() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate first = today.minusDays(3);
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        InventoryBalanceReportRow row = new InventoryBalanceReportRow(productId, "SKU-1", "Producto",
                "USD", BigDecimal.ZERO, new BigDecimal("15.000"), new BigDecimal("30.00"),
                new BigDecimal("10.000"), new BigDecimal("50.00"), new BigDecimal("5.000"),
                new BigDecimal("5.000"), BigDecimal.ZERO, new BigDecimal("5.000"));
        when(repository.count(eq(warehouseId), eq(productId), any(Instant.class), any(Instant.class))).thenReturn(201L);
        when(repository.find(eq(warehouseId), eq(productId), any(Instant.class), any(Instant.class),
                any(Instant.class), eq(100), eq(200L))).thenReturn(List.of(row));

        InventoryBalanceReport report = service.get(first, today, warehouseId, productId, PageRequest.of(2, 250));

        assertEquals(100, report.size());
        assertEquals(3, report.totalPages());
        assertEquals(1, report.items().size());
        assertEquals(productId, report.items().get(0).productId());
        verify(repository).find(eq(warehouseId), eq(productId), any(Instant.class), any(Instant.class),
                any(Instant.class), eq(100), eq(200L));
    }
}
