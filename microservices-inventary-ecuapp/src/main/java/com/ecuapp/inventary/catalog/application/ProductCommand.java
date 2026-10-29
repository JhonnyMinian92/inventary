package com.ecuapp.inventary.catalog.application;

import com.ecuapp.inventary.catalog.BillingFrequency;
import com.ecuapp.inventary.catalog.ProductKind;
import com.ecuapp.inventary.catalog.ServiceBilling;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Application-layer input; deliberately independent of HTTP and persistence frameworks.
 * kind, unitOfMeasureId, attributes, prices and marginPercent are optional: null keeps the current value (or the default on creation).
 */
public record ProductCommand(
    String sku,
    String name,
    String description,
    UUID categoryId,
    BigDecimal costPrice,
    BigDecimal salePrice,
    String currency,
    String imageUrl,
    ProductKind kind,
    UUID unitOfMeasureId,
    Map<String, Object> attributes,
    BigDecimal marginPercent,
    ServiceBilling serviceBilling,
    BillingFrequency billingFrequency,
    Integer termMonths
) {
    public ProductCommand(String sku, String name, String description, UUID categoryId, BigDecimal costPrice,
                          BigDecimal salePrice, String currency, String imageUrl) {
        this(sku, name, description, categoryId, costPrice, salePrice, currency, imageUrl, null, null, null, null, null, null, null);
    }

    public ProductCommand(String sku, String name, String description, UUID categoryId, BigDecimal costPrice,
                          BigDecimal salePrice, String currency, String imageUrl, ProductKind kind, UUID unitOfMeasureId,
                          Map<String, Object> attributes) {
        this(sku, name, description, categoryId, costPrice, salePrice, currency, imageUrl, kind, unitOfMeasureId, attributes, null, null, null, null);
    }
}
