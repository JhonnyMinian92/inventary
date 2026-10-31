package com.ecuapp.inventary.audit;

import com.ecuapp.inventary.auth.WarehouseScopeService;
import com.ecuapp.inventary.common.InvalidQueryException;
import com.ecuapp.inventary.common.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {
    @Mock private AuditEventRepository repository;
    @Mock private WarehouseScopeService scopes;
    @Mock private Authentication authentication;

    private AuditController unrestrictedController() {
        when(authentication.getName()).thenReturn("audit-admin");
        when(scopes.accessibleWarehouseIds("audit-admin")).thenReturn(null);
        when(scopes.canReadFinancialData("audit-admin")).thenReturn(true);
        return new AuditController(repository, scopes);
    }

    @Test
    void clampsPageSizeAndUsesNewestFirstOrdering() {
        when(repository.search(isNull(), isNull(), isNull(), isNull(),
            any(Instant.class), any(Instant.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        AuditController controller = unrestrictedController();

        controller.list(null, null, null, null, null, null,
            PageRequest.of(0, 500, Sort.by(Sort.Direction.ASC, "actor")), authentication);

        verify(repository).search(isNull(), isNull(), isNull(), isNull(), eq(TimeRange.MIN), eq(TimeRange.MAX),
            eq(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "occurredAt"))));
    }

    @Test
    void rejectsInvertedTimeRangeBeforeResolvingActorOrScope() {
        AuditController controller = new AuditController(repository, scopes);
        Instant from = Instant.parse("2026-10-02T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T00:00:00Z");

        assertThrows(InvalidQueryException.class,
            () -> controller.list(null, null, null, null, from, to, PageRequest.of(0, 20), authentication));
        verifyNoInteractions(repository, scopes, authentication);
    }

    @Test
    void passesAllowlistedFiltersToRepository() {
        when(repository.search(eq("auditor"), eq("HTTP_REQUEST_FAILED"), eq("HTTP_REQUEST"),
            eq("FAILURE"), any(Instant.class), any(Instant.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        AuditController controller = unrestrictedController();
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-02T00:00:00Z");

        controller.list(" auditor ", "HTTP_REQUEST_FAILED", "HTTP_REQUEST", "FAILURE", from, to,
            PageRequest.of(0, 20), authentication);

        verify(repository).search(eq("auditor"), eq("HTTP_REQUEST_FAILED"), eq("HTTP_REQUEST"),
            eq("FAILURE"), eq(from), eq(to), any(Pageable.class));
    }

    @Test
    void emptyWarehouseScopeReturnsNoAuditEventsWithoutQueryingRepository() {
        when(authentication.getName()).thenReturn("scoped-auditor");
        when(scopes.accessibleWarehouseIds("scoped-auditor")).thenReturn(Set.of());
        when(scopes.canReadFinancialData("scoped-auditor")).thenReturn(false);
        AuditController controller = new AuditController(repository, scopes);

        Page<AuditDtos.View> page = controller.list(null, null, null, null, null, null,
            PageRequest.of(0, 20), authentication);

        assertTrue(page.isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void scopedAuditSearchUsesWarehouseIdsAndKeepsPageFilters() {
        UUID warehouseId = UUID.randomUUID();
        when(authentication.getName()).thenReturn("scoped-auditor");
        when(scopes.accessibleWarehouseIds("scoped-auditor")).thenReturn(Set.of(warehouseId));
        when(scopes.canReadFinancialData("scoped-auditor")).thenReturn(false);
        when(repository.searchScoped(isNull(), isNull(), isNull(), isNull(),
            any(Instant.class), any(Instant.class), eq(Set.of(warehouseId.toString())), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        AuditController controller = new AuditController(repository, scopes);

        controller.list(null, null, null, null, null, null, PageRequest.of(2, 20), authentication);

        verify(repository).searchScoped(isNull(), isNull(), isNull(), isNull(),
            eq(TimeRange.MIN), eq(TimeRange.MAX), eq(Set.of(warehouseId.toString())),
            eq(PageRequest.of(2, 20)));
        verify(repository, never()).search(any(), any(), any(), any(), any(), any(), any());
    }
}
