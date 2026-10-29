package com.ecuapp.inventary.catalog;

import java.util.List;

/** How often a subscription generates an invoice. */
public enum BillingFrequency {
    BIWEEKLY("Cada 15 días", "Dos cobros por mes, al inicio de cada quincena."),
    MONTHLY("Cada 30 días", "Un cobro por mes, al inicio de cada mes del plan."),
    AT_END("Al finalizar", "Un solo cobro por todo el plan cuando termina.");

    /** Plan lengths offered to users, in months. */
    public static final List<Integer> TERMS = List.of(1, 3, 6, 12);

    private final String label;
    private final String description;

    BillingFrequency(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }
    public String description() { return description; }
}
