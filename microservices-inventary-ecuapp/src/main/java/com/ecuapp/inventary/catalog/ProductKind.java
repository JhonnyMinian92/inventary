package com.ecuapp.inventary.catalog;

/**
 * Controls whether a product participates in physical stock operations.
 * Labels and descriptions are what non-technical users see when choosing the kind.
 */
public enum ProductKind {
    STOCKED_GOOD("Producto que guardo y cuento", "Llevamos la cuenta de cuántos tienes: suma al comprar y resta al vender.", "Ropa, bebidas, celulares, herramientas"),
    NON_STOCKED_GOOD("Producto sin contar existencias", "Lo vendes, pero no necesitas saber cuántos quedan (se prepara o se pide al momento).", "Comida preparada, productos por encargo"),
    SERVICE("Servicio", "No es un objeto: cobras por tu trabajo o tu tiempo. Nunca afecta el inventario.", "Instalación, reparación, corte de cabello, envío");

    private final String label;
    private final String description;
    private final String examples;

    ProductKind(String label, String description, String examples) {
        this.label = label;
        this.description = description;
        this.examples = examples;
    }

    public String label() { return label; }
    public String description() { return description; }
    public String examples() { return examples; }
}
