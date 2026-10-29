package com.ecuapp.inventary.catalog;

/** How a service is charged. Labels are what non-technical users see. */
public enum ServiceBilling {
    ONE_TIME("Una sola vez", "Se vende, se factura y se paga una vez, como cualquier venta.", "Instalación, reparación, corte de cabello"),
    SUBSCRIPTION("Por suscripción", "Se cobra de forma periódica durante un plazo fijo; las facturas se generan solas.", "Mantenimiento mensual, clases, plan de soporte");

    private final String label;
    private final String description;
    private final String examples;

    ServiceBilling(String label, String description, String examples) {
        this.label = label;
        this.description = description;
        this.examples = examples;
    }

    public String label() { return label; }
    public String description() { return description; }
    public String examples() { return examples; }
}
