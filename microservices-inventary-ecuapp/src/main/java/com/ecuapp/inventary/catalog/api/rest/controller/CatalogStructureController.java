package com.ecuapp.inventary.catalog.api.rest.controller;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.catalog.api.rest.dto.*;
import com.ecuapp.inventary.catalog.application.service.*;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Units of measure, unit conversions and product variants. Reads need catalog:read (route rule), writes catalog:manage. */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogStructureController {
    private final CatalogStructureService structure;
    private final ProductVariantService variants;
    private final CategorySuggestionService suggestions;

    public CatalogStructureController(CatalogStructureService structure, ProductVariantService variants, CategorySuggestionService suggestions) {
        this.structure = structure;
        this.variants = variants;
        this.suggestions = suggestions;
    }

    @GetMapping("/units")
    public List<CatalogStructureDtos.UnitView> units(@RequestParam(required = false) Boolean active) {
        return structure.listUnits(active);
    }

    @GetMapping("/unit-dimensions")
    public List<CatalogStructureDtos.UnitDimensionView> unitDimensions() {
        return structure.unitDimensions();
    }

    /** Ready-made properties and category templates; clients render them instead of hardcoding choices. */
    @GetMapping("/attribute-presets")
    public CatalogStructureDtos.AttributePresetsView attributePresets() {
        return structure.attributePresets();
    }

    /** Suggests a template (and a similar existing category) from what the user typed, e.g. the category or product name. */
    @GetMapping("/attribute-presets/suggestion")
    public CatalogStructureDtos.CategorySuggestionView suggestTemplate(@RequestParam(required = false) String text) {
        return suggestions.suggest(text);
    }

    @GetMapping("/product-kinds")
    public List<CatalogStructureDtos.ProductKindView> productKinds() {
        return java.util.Arrays.stream(ProductKind.values()).map(CatalogStructureDtos.ProductKindView::from).toList();
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:manage')")
    public CatalogStructureDtos.UnitView createUnit(@Valid @RequestBody CatalogStructureDtos.UnitCreateRequest request, Authentication auth) {
        return structure.createUnit(request, auth.getName());
    }

    @PutMapping("/units/{id}")
    @PreAuthorize("hasAuthority('catalog:manage')")
    public CatalogStructureDtos.UnitView updateUnit(@PathVariable UUID id, @Valid @RequestBody CatalogStructureDtos.UnitUpdateRequest request, Authentication auth) {
        return structure.updateUnit(id, request, auth.getName());
    }

    @DeleteMapping("/units/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('catalog:manage')")
    public void deleteUnit(@PathVariable UUID id, Authentication auth) {
        structure.deleteUnit(id, auth.getName());
    }

    @GetMapping("/unit-conversions")
    public List<CatalogStructureDtos.ConversionView> conversions() {
        return structure.listConversions();
    }

    @PostMapping("/unit-conversions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:manage')")
    public CatalogStructureDtos.ConversionView createConversion(@Valid @RequestBody CatalogStructureDtos.ConversionRequest request, Authentication auth) {
        return structure.createConversion(request, auth.getName());
    }

    @GetMapping("/products/{productId}/variants")
    public List<CatalogStructureDtos.VariantView> variants(@PathVariable UUID productId) {
        return variants.list(productId);
    }

    @PostMapping("/products/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:manage')")
    public CatalogStructureDtos.VariantView createVariant(@PathVariable UUID productId, @Valid @RequestBody CatalogStructureDtos.VariantWriteRequest request, Authentication auth) {
        return variants.create(productId, request, auth.getName());
    }

    @GetMapping("/variants/{variantId}")
    public CatalogStructureDtos.VariantView variant(@PathVariable UUID variantId) {
        return variants.get(variantId);
    }

    @RequestMapping(value = "/variants/{variantId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('catalog:manage')")
    public CatalogStructureDtos.VariantView updateVariant(@PathVariable UUID variantId, @Valid @RequestBody CatalogStructureDtos.VariantUpdateRequest request, Authentication auth) {
        return variants.update(variantId, request, auth.getName());
    }

    @PostMapping("/variants/{variantId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('catalog:manage')")
    public void deactivateVariant(@PathVariable UUID variantId, Authentication auth) {
        variants.deactivate(variantId, auth.getName());
    }
}
