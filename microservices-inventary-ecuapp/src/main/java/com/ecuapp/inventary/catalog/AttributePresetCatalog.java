package com.ecuapp.inventary.catalog;

import com.ecuapp.inventary.catalog.api.rest.dto.CatalogStructureDtos;

import com.ecuapp.inventary.catalog.CategoryAttributeDefinition.ValueType;
import com.ecuapp.inventary.common.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Ready-made product properties and category templates offered to non-technical users.
 * The frontend only renders this catalog; keys, types and options are owned here so every client stays consistent.
 */
@Component
public class AttributePresetCatalog {
    private static final List<CatalogStructureDtos.ValueTypeView> VALUE_TYPES = List.of(
        new CatalogStructureDtos.ValueTypeView(ValueType.ENUM, "Elegir una opción", "El usuario elige una sola opción de una lista.", "Talla: S, M, L", true),
        new CatalogStructureDtos.ValueTypeView(ValueType.STRING_LIST, "Elegir varias opciones", "El usuario marca todas las opciones que apliquen.", "Colores disponibles: Rojo, Azul", true),
        new CatalogStructureDtos.ValueTypeView(ValueType.STRING, "Texto corto", "El usuario escribe un texto libre.", "Marca: Samsung", false),
        new CatalogStructureDtos.ValueTypeView(ValueType.NUMBER, "Número", "El usuario escribe una cantidad o medida.", "Garantía: 12 meses", false),
        new CatalogStructureDtos.ValueTypeView(ValueType.BOOLEAN, "Sí o No", "El usuario responde Sí o No.", "¿Requiere receta? Sí", false),
        new CatalogStructureDtos.ValueTypeView(ValueType.DATE, "Fecha", "El usuario elige una fecha del calendario.", "Vence: 31/12/2026", false));

    private static final List<CatalogStructureDtos.AttributePresetView> PRESETS = List.of(
        enumPreset("talla", "Talla", "Talla de ropa.", "XS", "S", "M", "L", "XL", "XXL"),
        enumPreset("talla_calzado", "Talla de calzado", "Número de calzado.", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45"),
        enumPreset("color", "Color", "Color principal del producto.", "Negro", "Blanco", "Gris", "Rojo", "Azul", "Verde", "Amarillo", "Rosado", "Morado", "Naranja", "Café", "Beige", "Multicolor"),
        enumPreset("tamano", "Tamaño", "Tamaño general del producto.", "Pequeño", "Mediano", "Grande", "Extra grande"),
        enumPreset("material", "Material", "Material principal.", "Algodón", "Poliéster", "Cuero", "Tela", "Plástico", "Metal", "Acero inoxidable", "Madera", "Vidrio", "Cerámica", "Papel", "Otro"),
        enumPreset("genero", "Para quién es", "Público al que está dirigido.", "Hombre", "Mujer", "Unisex", "Niño", "Niña", "Bebé"),
        enumPreset("presentacion", "Presentación", "Cómo viene empacado.", "Unidad", "Paquete", "Caja", "Botella", "Lata", "Funda", "Frasco", "Sobre"),
        enumPreset("voltaje", "Voltaje", "Tipo de alimentación eléctrica.", "110 V", "220 V", "Bivoltaje", "Batería", "No aplica"),
        enumPreset("edad_recomendada", "Edad recomendada", "Edad sugerida para el uso.", "0 a 2 años", "3 a 5 años", "6 a 8 años", "9 a 12 años", "13 años o más", "Adultos"),
        textPreset("marca", "Marca", "Marca o fabricante.", 80),
        textPreset("modelo", "Modelo", "Modelo o referencia del fabricante.", 80),
        textPreset("sabor", "Sabor o aroma", "Ej. Fresa, Vainilla, Lavanda.", 60),
        textPreset("contenido", "Contenido", "Cantidad que trae el envase. Ej. 500 ml, 1 kg, 30 tabletas.", 40),
        textPreset("capacidad", "Capacidad", "Ej. 1 litro, 64 GB, 5 personas.", 40),
        textPreset("medidas", "Medidas", "Largo x ancho x alto. Ej. 30 x 20 x 10 cm.", 60),
        textPreset("lote", "Lote", "Número de lote del fabricante.", 40),
        textPreset("registro_sanitario", "Registro sanitario", "Código del registro sanitario.", 60),
        numberPreset("peso_kg", "Peso (kg)", "Peso del producto en kilogramos.", 0, null),
        numberPreset("garantia_meses", "Garantía (meses)", "Meses de garantía que ofrece.", 0, 120),
        numberPreset("duracion_minutos", "Duración (minutos)", "Tiempo que toma el servicio.", 0, 1440),
        new CatalogStructureDtos.AttributePresetView("fecha_vencimiento", "Fecha de vencimiento", "Fecha en que el producto caduca.", ValueType.DATE, Map.of(), List.of()),
        new CatalogStructureDtos.AttributePresetView("requiere_receta", "¿Requiere receta?", "Indica si se vende solo con receta médica.", ValueType.BOOLEAN, Map.of(), List.of()),
        new CatalogStructureDtos.AttributePresetView("importado", "¿Es importado?", "Indica si el producto es importado.", ValueType.BOOLEAN, Map.of(), List.of()));

    private static final List<CatalogStructureDtos.CategoryTemplateView> TEMPLATES = List.of(
        new CatalogStructureDtos.CategoryTemplateView("ropa", "Ropa y accesorios", "Camisetas, pantalones, gorras, bolsos.", List.of("talla", "color", "material", "genero", "marca")),
        new CatalogStructureDtos.CategoryTemplateView("calzado", "Calzado", "Zapatos, zapatillas, sandalias.", List.of("talla_calzado", "color", "material", "genero", "marca")),
        new CatalogStructureDtos.CategoryTemplateView("alimentos", "Alimentos y bebidas", "Snacks, bebidas, abarrotes.", List.of("marca", "sabor", "contenido", "presentacion", "fecha_vencimiento")),
        new CatalogStructureDtos.CategoryTemplateView("tecnologia", "Tecnología y electrónica", "Celulares, accesorios, electrodomésticos.", List.of("marca", "modelo", "color", "capacidad", "garantia_meses", "voltaje")),
        new CatalogStructureDtos.CategoryTemplateView("ferreteria", "Ferretería y construcción", "Herramientas, materiales, repuestos.", List.of("marca", "material", "medidas", "peso_kg")),
        new CatalogStructureDtos.CategoryTemplateView("hogar", "Hogar y decoración", "Muebles, cocina, decoración.", List.of("material", "color", "medidas", "marca")),
        new CatalogStructureDtos.CategoryTemplateView("belleza", "Belleza y cuidado personal", "Cosméticos, perfumes, cuidado del cabello.", List.of("marca", "sabor", "contenido", "genero", "fecha_vencimiento")),
        new CatalogStructureDtos.CategoryTemplateView("farmacia", "Farmacia y salud", "Medicamentos, vitaminas, insumos médicos.", List.of("marca", "contenido", "presentacion", "fecha_vencimiento", "lote", "requiere_receta", "registro_sanitario")),
        new CatalogStructureDtos.CategoryTemplateView("papeleria", "Papelería y oficina", "Útiles escolares, oficina, impresión.", List.of("marca", "color", "presentacion")),
        new CatalogStructureDtos.CategoryTemplateView("juguetes", "Juguetes", "Juguetes y juegos.", List.of("edad_recomendada", "marca", "material")),
        new CatalogStructureDtos.CategoryTemplateView("servicios", "Servicios", "Mano de obra, instalaciones, mantenimiento.", List.of("duracion_minutos")),
        new CatalogStructureDtos.CategoryTemplateView("general", "Otro / sin propiedades", "Empieza vacío y agrega solo lo que necesites.", List.of()));

    private static final Map<String, CatalogStructureDtos.AttributePresetView> PRESETS_BY_KEY = new LinkedHashMap<>();
    static { PRESETS.forEach(p -> PRESETS_BY_KEY.put(p.key(), p)); }

    public CatalogStructureDtos.AttributePresetsView view() {
        return new CatalogStructureDtos.AttributePresetsView(VALUE_TYPES, PRESETS, TEMPLATES);
    }

    public String templateName(String templateId) {
        return TEMPLATES.stream().filter(t -> t.id().equals(templateId)).map(CatalogStructureDtos.CategoryTemplateView::name).findFirst().orElse(null);
    }

    /** Builds the schema request for a template so it is published through the regular, versioned schema flow. */
    public CatalogStructureDtos.AttributeSchemaRequest templateSchema(String templateId) {
        CatalogStructureDtos.CategoryTemplateView template = TEMPLATES.stream().filter(t -> t.id().equals(templateId)).findFirst()
            .orElseThrow(() -> new BusinessRuleException("La plantilla de categoría no existe."));
        List<CatalogStructureDtos.AttributeDefinitionRequest> definitions = template.presetKeys().stream().map(PRESETS_BY_KEY::get)
            .map(p -> new CatalogStructureDtos.AttributeDefinitionRequest(p.key(), p.displayName(), p.valueType(), false, p.constraints(), p.options()))
            .toList();
        return new CatalogStructureDtos.AttributeSchemaRequest(definitions);
    }

    private static CatalogStructureDtos.AttributePresetView enumPreset(String key, String name, String description, String... options) {
        return new CatalogStructureDtos.AttributePresetView(key, name, description, ValueType.ENUM, Map.of(), List.of((Object[]) options));
    }

    private static CatalogStructureDtos.AttributePresetView textPreset(String key, String name, String description, int maxLength) {
        return new CatalogStructureDtos.AttributePresetView(key, name, description, ValueType.STRING, Map.of("maxLength", maxLength), List.of());
    }

    private static CatalogStructureDtos.AttributePresetView numberPreset(String key, String name, String description, Integer min, Integer max) {
        Map<String, Object> constraints = new LinkedHashMap<>();
        if (min != null) constraints.put("min", min);
        if (max != null) constraints.put("max", max);
        return new CatalogStructureDtos.AttributePresetView(key, name, description, ValueType.NUMBER, Map.copyOf(constraints), List.of());
    }
}
