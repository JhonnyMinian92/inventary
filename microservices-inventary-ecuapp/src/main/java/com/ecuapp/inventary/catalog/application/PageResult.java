package com.ecuapp.inventary.catalog.application;

import java.util.List;

/** Framework-independent page result returned by application use cases. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
