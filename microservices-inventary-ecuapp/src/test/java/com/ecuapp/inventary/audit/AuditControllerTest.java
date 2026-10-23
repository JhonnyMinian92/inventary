package com.ecuapp.inventary.audit;

import com.ecuapp.inventary.common.InvalidQueryException;
import com.ecuapp.inventary.common.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {
    @Mock
    private AuditEventRepository repository;

    @Test
    void clampsPageSizeAndUsesNewestFirstOrdering() {
        when(repository.search(isNull(), isNull(), isNull(), isNull(), any(Instant.class), any(Instant.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        AuditController controller = new AuditController(repository);

        controller.list(null, null, null, null, null, null,
            PageRequest.of(0, 500, Sort.by(Sort.Direction.ASC, "actor")));

        verify(repository).search(isNull(), isNull(), isNull(), isNull(), eq(TimeRange.MIN), eq(TimeRange.MAX),
            eq(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "occurredAt"))));
    }

    @Test
    void rejectsInvertedTimeRange() {
        AuditController controller = new AuditController(repository);
        Instant from = Instant.parse("2026-10-02T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T00:00:00Z");

        assertThrows(InvalidQueryException.class,
            () -> controller.list(null, null, null, null, from, to, PageRequest.of(0, 20)));
        verifyNoInteractions(repository);
    }

    @Test
    void passesAllowlistedFiltersToRepository() {
        when(repository.search(eq("auditor"), eq("HTTP_REQUEST_FAILED"), eq("HTTP_REQUEST"),
            eq("FAILURE"), any(Instant.class), any(Instant.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        AuditController controller = new AuditController(repository);
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-02T00:00:00Z");

        controller.list(" auditor ", "HTTP_REQUEST_FAILED", "HTTP_REQUEST", "FAILURE", from, to,
            PageRequest.of(0, 20));

        verify(repository).search(eq("auditor"), eq("HTTP_REQUEST_FAILED"), eq("HTTP_REQUEST"),
            eq("FAILURE"), eq(from), eq(to), any(Pageable.class));
    }
}
