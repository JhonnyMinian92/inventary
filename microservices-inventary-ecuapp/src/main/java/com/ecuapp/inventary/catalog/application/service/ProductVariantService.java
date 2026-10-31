package com.ecuapp.inventary.catalog.application.service;

import com.ecuapp.inventary.catalog.*;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ProductVariantService {
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final UnitOfMeasureRepository units;
    private final CategoryAttributeDefinitionRepository definitions;
    private final AttributeSchemaValidator validator;
    private final AuditService audit;

    public ProductVariantService(ProductVariantRepository variants, ProductRepository products, UnitOfMeasureRepository units,
                                 CategoryAttributeDefinitionRepository definitions, AttributeSchemaValidator validator, AuditService audit) {
        this.variants = variants;
        this.products = products;
        this.units = units;
        this.definitions = definitions;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CatalogStructureDtos.VariantView> list(UUID productId) {
        if (!products.existsById(productId)) throw new ResourceNotFoundException("Producto", productId);
        return variants.findByProductIdOrderBySkuAsc(productId).stream().map(CatalogStructureDtos.VariantView::from).toList();
    }

    @Transactional(readOnly = true)
    public CatalogStructureDtos.VariantView get(UUID variantId) {
        return CatalogStructureDtos.VariantView.from(require(variantId));
    }

    @Transactional
    @CacheEvict(cacheNames = "publicProductList", allEntries = true)
    public CatalogStructureDtos.VariantView create(UUID productId, CatalogStructureDtos.VariantWriteRequest request, String actor) {
        Product product = products.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Producto", productId));
        if (!product.isActive()) throw new BusinessRuleException("No se pueden crear variantes de un producto inactivo.");
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (!sku.matches("^[A-Z0-9][A-Z0-9._-]{0,63}$")) throw new BusinessRuleException("El SKU de la variante solo admite letras, números, punto, guion y guion bajo.");
        if (variants.existsBySkuIgnoreCase(sku) || products.existsBySkuIgnoreCase(sku)) throw new ConflictException("Ya existe un producto o variante con ese SKU.");
        String barcode = clean(request.barcode());
        if (barcode != null && variants.existsByBarcode(barcode)) throw new ConflictException("El código de barras ya está asignado a otra variante.");
        UnitOfMeasure unit = units.findById(request.unitOfMeasureId() == null ? product.getUnitOfMeasureId() : request.unitOfMeasureId())
            .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", request.unitOfMeasureId()));
        if (!unit.isActive()) throw new BusinessRuleException("La unidad de medida está inactiva.");
        Map<String, Object> options = validateOptions(product, request.optionValues());
        ensureUniqueCombination(product.getId(), options, null);
        ProductVariant variant = variants.saveAndFlush(new ProductVariant(product, sku, barcode, options, unit, price(request.salePrice())));
        audit.record(actor, "PRODUCT_VARIANT_CREATED", "PRODUCT_VARIANT", variant.getId(), Map.of("productId", productId.toString(), "sku", sku));
        return CatalogStructureDtos.VariantView.from(variant);
    }

    @Transactional
    @CacheEvict(cacheNames = "publicProductList", allEntries = true)
    public CatalogStructureDtos.VariantView update(UUID variantId, CatalogStructureDtos.VariantUpdateRequest request, String actor) {
        ProductVariant variant = require(variantId);
        String barcode = clean(request.barcode());
        if (barcode != null && !barcode.equals(variant.getBarcode()) && variants.existsByBarcode(barcode))
            throw new ConflictException("El código de barras ya está asignado a otra variante.");
        Map<String, Object> options = validateOptions(variant.getProduct(), request.optionValues());
        if (variant.isActive()) ensureUniqueCombination(variant.getProduct().getId(), options, variantId);
        variant.update(barcode, options, price(request.salePrice()));
        variants.flush();
        audit.record(actor, "PRODUCT_VARIANT_UPDATED", "PRODUCT_VARIANT", variantId, Map.of("sku", variant.getSku()));
        return CatalogStructureDtos.VariantView.from(variant);
    }

    @Transactional
    @CacheEvict(cacheNames = "publicProductList", allEntries = true)
    public void deactivate(UUID variantId, String actor) {
        ProductVariant variant = require(variantId);
        if (!variant.isActive()) return;
        variant.deactivate();
        audit.record(actor, "PRODUCT_VARIANT_DEACTIVATED", "PRODUCT_VARIANT", variantId, Map.of("sku", variant.getSku()));
    }

    /** Option values must be defined by the category schema when it exists (only scalar, non-list types). */
    private Map<String, Object> validateOptions(Product product, Map<String, Object> raw) {
        List<CategoryAttributeDefinition> schema = definitions.findByCategoryIdAndActiveTrueOrderByAttributeKeyAsc(product.getCategory().getId());
        Map<String, Object> options = validator.validateValues(raw, schema, false);
        if (options.isEmpty()) throw new BusinessRuleException("La variante debe indicar al menos un valor de opción.");
        if (options.values().stream().anyMatch(v -> v instanceof List<?>))
            throw new BusinessRuleException("Las opciones de variante deben ser valores simples.");
        return options;
    }

    private void ensureUniqueCombination(UUID productId, Map<String, Object> options, UUID ignoreId) {
        boolean duplicate = variants.findByProductIdAndActiveTrue(productId).stream()
            .anyMatch(v -> !v.getId().equals(ignoreId) && normalized(v.getOptionValues()).equals(normalized(options)));
        if (duplicate) throw new ConflictException("Ya existe una variante activa con la misma combinación de opciones.");
    }

    private Map<String, String> normalized(Map<String, Object> values) {
        Map<String, String> result = new TreeMap<>();
        values.forEach((k, v) -> result.put(k, String.valueOf(v)));
        return result;
    }

    private ProductVariant require(UUID id) {
        return variants.findById(id).orElseThrow(() -> new ResourceNotFoundException("Variante", id));
    }

    private BigDecimal price(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String clean(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
