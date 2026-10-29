package com.ecuapp.inventary.catalog.application;

import com.ecuapp.inventary.catalog.BillingFrequency;
import com.ecuapp.inventary.catalog.ProductKind;
import com.ecuapp.inventary.catalog.ServiceBilling;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Application-layer result; controllers decide how it is represented over HTTP (cost is filtered by permission). */
public record ProductResult(
    UUID id,
    String sku,
    String name,
    String description,
    UUID categoryId,
    String categoryName,
    BigDecimal costPrice,
    BigDecimal salePrice,
    BigDecimal marginPercent,
    String currency,
    String imageUrl,
    ProductKind kind,
    UUID unitOfMeasureId,
    Map<String, Object> attributes,
    int attributeSchemaVersion,
    ServiceBilling serviceBilling,
    BillingFrequency billingFrequency,
    Integer termMonths,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
