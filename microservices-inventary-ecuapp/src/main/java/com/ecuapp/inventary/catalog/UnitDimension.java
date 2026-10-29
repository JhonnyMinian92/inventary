package com.ecuapp.inventary.catalog;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * How stock is measured. The labels are what non-technical users see when choosing or creating a unit of measure;
 * product characteristics such as size or weight belong to category properties, not here.
 */
public enum UnitDimension {
    COUNT("Por cantidad (piezas)", "Se cuenta en piezas enteras: 1, 2, 3…", "Unidad, caja, paquete, docena", false),
    MASS("Por peso", "Se vende o compra pesando el producto.", "Kilogramo, gramo, libra", true),
    VOLUME("Por volumen (líquidos)", "Se mide en litros o mililitros.", "Litro, mililitro, galón", true),
    LENGTH("Por largo", "Se corta o mide en metros.", "Metro, centímetro, rollo", true),
    AREA("Por superficie", "Se mide en metros cuadrados.", "Metro cuadrado", true),
    TIME("Por tiempo", "Se cobra por horas o días (servicios).", "Hora, día", true),
    OTHER("Otra forma", "Cuando ninguna de las anteriores aplica.", "Servicio, juego, kit", false);

    private final String label;
    private final String description;
    private final String examples;
    private final boolean defaultAllowsFractions;

    UnitDimension(String label, String description, String examples, boolean defaultAllowsFractions) {
        this.label = label;
        this.description = description;
        this.examples = examples;
        this.defaultAllowsFractions = defaultAllowsFractions;
    }

    public static Optional<UnitDimension> parse(String value) {
        if (value == null) return Optional.empty();
        String code = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(d -> d.name().equals(code)).findFirst();
    }

    public String label() { return label; }
    public String description() { return description; }
    public String examples() { return examples; }
    public boolean defaultAllowsFractions() { return defaultAllowsFractions; }
}
