package com.ecuapp.inventary.catalog.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Application-layer input; deliberately independent of HTTP and persistence frameworks. */
public record ProductCommand(
    String sku,
    String name,
    String description,
    UUID categoryId,
    BigDecimal costPrice,
    BigDecimal salePrice,
    String currency,
    String imageUrl
) {}
