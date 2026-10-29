package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.text.Normalizer;
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
    private final AttributePresetCatalog presets;

    public CatalogStructureService(UnitOfMeasureRepository units, UnitConversionRepository conversions, CategoryRepository categories,
                                   CategoryAttributeDefinitionRepository definitions, ProductRepository products,
                                   ProductVariantRepository variants, AttributeSchemaValidator validator, AuditService audit,
                                   AttributePresetCatalog presets) {
        this.units = units;
        this.conversions = conversions;
        this.categories = categories;
        this.definitions = definitions;
        this.products = products;
        this.variants = variants;
        this.validator = validator;
        this.audit = audit;
        this.presets = presets;
    }

    public List<CatalogStructureDtos.UnitDimensionView> unitDimensions() {
        return Arrays.stream(UnitDimension.values()).map(CatalogStructureDtos.UnitDimensionView::from).toList();
    }

    public CatalogStructureDtos.AttributePresetsView attributePresets() {
        return presets.view();
    }

    @Transactional(readOnly = true)
    public List<CatalogStructureDtos.UnitView> listUnits(Boolean active) {
        return units.findAll(Sort.by("code")).stream().filter(u -> active == null || u.isActive() == active)
            .map(CatalogStructureDtos.UnitView::from).toList();
    }

    @Transactional
    public CatalogStructureDtos.UnitView createUnit(CatalogStructureDtos.UnitCreateRequest request, String actor) {
        UnitDimension dimension = UnitDimension.parse(request.dimension())
            .orElseThrow(() -> new BusinessRuleException("Elija cómo se mide la unidad: cantidad, peso, volumen, largo, superficie, tiempo u otra."));
        String code;
        if (request.code() == null || request.code().isBlank()) {
            code = generateUnitCode(request.name());
        } else {
            code = request.code().trim().toUpperCase(Locale.ROOT);
            if (units.existsByCodeIgnoreCase(code)) throw new ConflictException("Ya existe una unidad con ese código.");
        }
        int decimalScale = request.decimalScale() != null ? request.decimalScale()
            : Boolean.TRUE.equals(request.allowsFractions()) || (request.allowsFractions() == null && dimension.defaultAllowsFractions()) ? 3 : 0;
        UnitOfMeasure unit = units.saveAndFlush(new UnitOfMeasure(UUID.randomUUID(), code, request.name().trim(),
            dimension.name(), (short) decimalScale));
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

    @Transactional
    public void deleteUnit(UUID id, String actor) {
        UnitOfMeasure unit = units.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida", id));
        if (UnitOfMeasure.DEFAULT_UNIT_ID.equals(id)) {
            throw new ConflictException("La unidad base EA no se puede eliminar físicamente.");
        }
        if (unit.isActive()) {
            throw new ConflictException("Desactive la unidad antes de intentar su eliminación física.");
        }
        if (products.existsByUnitOfMeasureId(id) || variants.existsByUnitOfMeasureId(id)) {
            throw new ConflictException("No se puede eliminar una unidad que aún está asignada a productos o variantes, incluso si están inactivos.");
        }
        if (conversions.existsForUnit(id)) {
            throw new ConflictException("No se puede eliminar una unidad utilizada por conversiones registradas.");
        }
        String code = unit.getCode();
        try {
            units.deleteAndFlush(unit);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("La unidad conserva referencias históricas y no puede eliminarse físicamente.");
        }
        audit.record(actor, "UNIT_OF_MEASURE_DELETED", "UNIT_OF_MEASURE", id, Map.of("code", code));
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
        Set<String> names = new HashSet<>();
        for (CatalogStructureDtos.AttributeDefinitionRequest d : request.definitions()) {
            if (d.key() == null || d.key().isBlank()) continue;
            String key = d.key().trim();
            if (!keys.add(key)) throw new BusinessRuleException("La clave de atributo " + key + " está repetida.");
        }
        List<String> resolvedKeys = new ArrayList<>();
        for (CatalogStructureDtos.AttributeDefinitionRequest d : request.definitions()) {
            if (!names.add(d.displayName().trim().toLowerCase(Locale.ROOT)))
                throw new BusinessRuleException("La propiedad \"" + d.displayName().trim() + "\" está repetida.");
            String key = d.key() == null || d.key().isBlank() ? uniqueAttributeKey(d.displayName(), keys) : d.key().trim();
            validator.validateDefinition(key, d.valueType(), d.constraints(), d.options());
            resolvedKeys.add(key);
        }
        definitions.findByCategoryIdAndActiveTrueOrderByAttributeKeyAsc(categoryId).forEach(CategoryAttributeDefinition::retire);
        definitions.flush();
        int version = definitions.maxSchemaVersion(categoryId) + 1;
        List<CategoryAttributeDefinition> created = new ArrayList<>();
        for (int i = 0; i < request.definitions().size(); i++) {
            CatalogStructureDtos.AttributeDefinitionRequest d = request.definitions().get(i);
            created.add(definitions.save(new CategoryAttributeDefinition(category, resolvedKeys.get(i), d.displayName().trim(), d.valueType(),
                d.required(), d.constraints(), d.options(), version)));
        }
        definitions.flush();
        audit.record(actor, "CATEGORY_ATTRIBUTE_SCHEMA_PUBLISHED", "CATEGORY", categoryId,
            Map.of("schemaVersion", version, "attributes", created.stream().map(CategoryAttributeDefinition::getAttributeKey).sorted().toList()));
        return new CatalogStructureDtos.AttributeSchemaView(categoryId, version,
            created.stream().sorted(Comparator.comparing(CategoryAttributeDefinition::getAttributeKey))
                .map(CatalogStructureDtos.AttributeDefinitionView::from).toList());
    }

    /** Publishes the properties of a ready-made template as the category schema. */
    @Transactional
    public CatalogStructureDtos.AttributeSchemaView applyTemplate(UUID categoryId, String templateId, String actor) {
        return replaceSchema(categoryId, presets.templateSchema(templateId), actor);
    }

    /** Derives a technical key from the visible name ("Talla de ropa" -> "talla_de_ropa"), unique within the schema. */
    private String uniqueAttributeKey(String displayName, Set<String> used) {
        String base = Normalizer.normalize(displayName.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("(^_+|_+$)", "");
        if (base.isEmpty()) base = "campo";
        if (!Character.isLetter(base.charAt(0))) base = "campo_" + base;
        if (base.length() > 70) base = base.substring(0, 70).replaceAll("_+$", "");
        String key = base;
        for (int n = 2; !used.add(key); n++) key = base + "_" + n;
        return key;
    }

    /** Derives a unit code from its name ("Caja de 12" -> "CAJA_DE_12"), adding a suffix when it already exists. */
    private String generateUnitCode(String name) {
        String base = Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_+|_+$)", "");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) base = "U" + base;
        if (base.length() > 20) base = base.substring(0, 20).replaceAll("_+$", "");
        String code = base;
        for (int n = 2; units.existsByCodeIgnoreCase(code); n++) {
            if (n > 999) throw new ConflictException("No se pudo generar un código para la unidad. Use otro nombre.");
            code = base + "_" + n;
        }
        return code;
    }

    private Category requireCategory(UUID id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Categoría", id));
    }
}
