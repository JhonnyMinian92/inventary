package com.ecuapp.inventary.subscriptions;

import com.ecuapp.inventary.catalog.BillingFrequency;
import com.ecuapp.inventary.common.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the billing calendar of a subscription. Periods are half-open [start, end).
 * <ul>
 * <li>MONTHLY: one charge per month of the plan, billed when the month starts.</li>
 * <li>BIWEEKLY: each month is split at day 15 into two charges, billed when each half starts; the monthly price is split
 * so both halves add up exactly (the first half takes the extra cent).</li>
 * <li>AT_END: a single charge for the whole plan, billed the day it ends.</li>
 * </ul>
 */
public final class SubscriptionSchedule {
    public record Period(int sequence, LocalDate start, LocalDate end, LocalDate billingDate, BigDecimal amount) {}

    private SubscriptionSchedule() {}

    public static List<Period> build(LocalDate start, int termMonths, BillingFrequency frequency, BigDecimal monthlyPrice) {
        if (start == null) throw new BusinessRuleException("Indique la fecha de inicio de la suscripción.");
        if (!BillingFrequency.TERMS.contains(termMonths)) throw new BusinessRuleException("La duración debe ser de 1, 3, 6 o 12 meses.");
        if (frequency == null) throw new BusinessRuleException("Indique cada cuánto se cobra la suscripción.");
        if (monthlyPrice == null || monthlyPrice.signum() < 0) throw new BusinessRuleException("El precio mensual no puede ser negativo.");
        BigDecimal monthly = monthlyPrice.setScale(2, RoundingMode.HALF_UP);
        LocalDate end = start.plusMonths(termMonths);
        List<Period> periods = new ArrayList<>();
        switch (frequency) {
            case MONTHLY -> {
                for (int i = 0; i < termMonths; i++) {
                    LocalDate from = start.plusMonths(i);
                    periods.add(new Period(i + 1, from, start.plusMonths(i + 1), from, monthly));
                }
            }
            case BIWEEKLY -> {
                BigDecimal firstHalf = monthly.divide(BigDecimal.valueOf(2), 2, RoundingMode.CEILING);
                BigDecimal secondHalf = monthly.subtract(firstHalf);
                for (int i = 0; i < termMonths; i++) {
                    LocalDate from = start.plusMonths(i);
                    LocalDate middle = from.plusDays(15);
                    periods.add(new Period(i * 2 + 1, from, middle, from, firstHalf));
                    periods.add(new Period(i * 2 + 2, middle, start.plusMonths(i + 1), middle, secondHalf));
                }
            }
            case AT_END -> periods.add(new Period(1, start, end, end, monthly.multiply(BigDecimal.valueOf(termMonths))));
        }
        return periods;
    }

    public static BigDecimal total(List<Period> periods) {
        return periods.stream().map(Period::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
