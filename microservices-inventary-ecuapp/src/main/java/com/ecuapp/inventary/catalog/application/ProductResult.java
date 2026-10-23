package com.ecuapp.inventary.catalog.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Application-layer result; controllers decide how it is represented over HTTP. */
public record ProductResult(
    UUID id,
    String sku,
    String name,
    String description,
    UUID categoryId,
    String categoryName,
    BigDecimal salePrice,
    String currency,
    String imageUrl,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
