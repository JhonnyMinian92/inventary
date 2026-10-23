package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.*;

/** Units of measure, unit conversions and versioned attribute schemas per category. */
@Service
public class CatalogStructureService {
    private final UnitOfMeasureRepository units;
    private final UnitConversionRepository conversions;
    private final CategoryRepository categories;
    private final CategoryAttributeDefinitionRepository definitions;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final AttributeSchemaValidator validator;
    private final AuditService audit;

    public CatalogStructureService(UnitOfMeasureRepository units, UnitConversionRepository conversions, CategoryRepository categories,
                                   CategoryAttributeDefinitionRepository definitions, ProductRepository products,
                                   ProductVariantRepository variants, AttributeSchemaValidator validator, AuditService audit) {
        this.units = units;
        this.conversions = conversions;
        this.categories = categories;
        this.definitions = definitions;
        this.products = products;
        this.variants = variants;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CatalogStructureDtos.UnitView> listUnits(Boolean active) {
        return units.findAll(Sort.by("code")).stream().filter(u -> active == null || u.isActive() == active)
            .map(CatalogStructureDtos.UnitView::from).toList();
    }

    @Transactional
    public CatalogStructureDtos.UnitView createUnit(CatalogStructureDtos.UnitCreateRequest request, String actor) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (units.existsByCodeIgnoreCase(code)) throw new ConflictException("Ya existe una unidad con ese código.");
        UnitOfMeasure unit = units.saveAndFlush(new UnitOfMeasure(UUID.randomUUID(), code, request.name().trim(),
            request.dimension().trim().toUpperCase(Locale.ROOT), request.decimalScale().shortValue()));
        audit.record(actor, "UNIT_OF_MEASURE_CREATED", "UNIT_OF_MEASURE", unit.getId(), Map.of("code", code, "dimension", unit.getDimension()));
        return CatalogStructureDtos.UnitView.from(unit);
    }

    @Transactional
    public CatalogStructureDtos.UnitView updateUnit(UUID id, CatalogStructureDtos.UnitUpdateRequest request, String actor) {
        UnitOfMeasure unit = units.findById(id).orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", id));
        if (!request.active() && unit.isActive()) {
            if (UnitOfMeasure.DEFAULT_UNIT_ID.equals(id)) throw new BusinessRuleException("La unidad base EA no se puede desactivar.");
            if (products.existsByUnitOfMeasureIdAndActiveTrue(id) || variants.existsByUnitOfMeasureIdAndActiveTrue(id))
                throw new ConflictException("La unidad está asignada a productos o variantes activos.");
        }
        unit.update(request.name().trim(), request.decimalScale().shortValue(), request.active());
        audit.record(actor, "UNIT_OF_MEASURE_UPDATED", "UNIT_OF_MEASURE", id, Map.of("code", unit.getCode(), "active", request.active()));
        return CatalogStructureDtos.UnitView.from(unit);
    }

    @Transactional(readOnly = true)
    public List<CatalogStructureDtos.ConversionView> listConversions() {
        return conversions.findAll().stream().map(CatalogStructureDtos.ConversionView::from)
            .sorted(Comparator.comparing(CatalogStructureDtos.ConversionView::fromUnitCode).thenComparing(CatalogStructureDtos.ConversionView::toUnitCode))
            .toList();
    }

    @Transactional
    public CatalogStructureDtos.ConversionView createConversion(CatalogStructureDtos.ConversionRequest request, String actor) {
        if (request.fromUnitId().equals(request.toUnitId())) throw new BusinessRuleException("Las unidades de una conversión deben ser distintas.");
        UnitOfMeasure from = units.findById(request.fromUnitId()).orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", request.fromUnitId()));
        UnitOfMeasure to = units.findById(request.toUnitId()).orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", request.toUnitId()));
        if (!from.isActive() || !to.isActive()) throw new BusinessRuleException("Ambas unidades deben estar activas.");
        if (conversions.findByFromUnitIdAndToUnitId(from.getId(), to.getId()).isPresent())
            throw new ConflictException("La conversión entre esas unidades ya existe.");
        UnitConversion conversion = conversions.saveAndFlush(new UnitConversion(from, to, request.factor().setScale(9, RoundingMode.UNNECESSARY)));
        audit.record(actor, "UNIT_CONVERSION_CREATED", "UNIT_CONVERSION", conversion.getId(),
            Map.of("from", from.getCode(), "to", to.getCode(), "factor", conversion.getFactor().toPlainString()));
        return CatalogStructureDtos.ConversionView.from(conversion);
    }

    @Transactional(readOnly = true)
    public CatalogStructureDtos.AttributeSchemaView schema(UUID categoryId) {
        requireCategory(categoryId);
        List<CategoryAttributeDefinition> active = definitions.findByCategoryIdAndActiveTrueOrderByAttributeKeyAsc(categoryId);
        int version = active.isEmpty() ? definitions.maxSchemaVersion(categoryId) : active.get(0).getSchemaVersion();
        return new CatalogStructureDtos.AttributeSchemaView(categoryId, version,
            active.stream().map(CatalogStructureDtos.AttributeDefinitionView::from).toList());
    }

    /** Publishes a new schema version; previous definitions are retired but kept for traceability. */
    @Transactional
    @CacheEvict(cacheNames = "publicProductList", allEntries = true)
    public CatalogStructureDtos.AttributeSchemaView replaceSchema(UUID categoryId, CatalogStructureDtos.AttributeSchemaRequest request, String actor) {
        Category category = categories.findForUpdate(categoryId).orElseThrow(() -> new ResourceNotFoundException("Categoría", categoryId));
        Set<String> keys = new HashSet<>();
        for (CatalogStructureDtos.AttributeDefinitionRequest d : request.definitions()) {
            String key = d.key().trim();
            if (!keys.add(key)) throw new BusinessRuleException("La clave de atributo " + key + " está repetida.");
            validator.validateDefinition(key, d.valueType(), d.constraints(), d.options());
        }
        definitions.findByCategoryIdAndActiveTrueOrderByAttributeKeyAsc(categoryId).forEach(CategoryAttributeDefinition::retire);
        definitions.flush();
        int version = definitions.maxSchemaVersion(categoryId) + 1;
        List<CategoryAttributeDefinition> created = new ArrayList<>();
        for (CatalogStructureDtos.AttributeDefinitionRequest d : request.definitions()) {
            created.add(definitions.save(new CategoryAttributeDefinition(category, d.key().trim(), d.displayName().trim(), d.valueType(),
                d.required(), d.constraints(), d.options(), version)));
        }
        definitions.flush();
        audit.record(actor, "CATEGORY_ATTRIBUTE_SCHEMA_PUBLISHED", "CATEGORY", categoryId,
            Map.of("schemaVersion", version, "attributes", created.stream().map(CategoryAttributeDefinition::getAttributeKey).sorted().toList()));
        return new CatalogStructureDtos.AttributeSchemaView(categoryId, version,
            created.stream().sorted(Comparator.comparing(CategoryAttributeDefinition::getAttributeKey))
                .map(CatalogStructureDtos.AttributeDefinitionView::from).toList());
    }

    private Category requireCategory(UUID id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Categoría", id));
    }
}
