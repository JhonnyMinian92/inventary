package com.ecuapp.inventary.subscriptions;

import com.ecuapp.inventary.catalog.BillingFrequency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SubscriptionDtos {
    private SubscriptionDtos() {}

    /**
     * billingFrequency, termMonths and monthlyPrice default to the service plan; startDate defaults to today.
     * prepay invoices the whole plan at once; a discount above zero requires the sales:discount permission.
     */
    public record CreateRequest(@NotNull UUID customerId, @NotNull UUID productId, @NotNull UUID warehouseId, LocalDate startDate,
                                BillingFrequency billingFrequency, Integer termMonths,
                                @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal monthlyPrice,
                                boolean prepay, @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent) {}

    public record PreviewRequest(@NotNull BillingFrequency billingFrequency, @NotNull Integer termMonths,
                                 @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal monthlyPrice, LocalDate startDate) {}

    public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record PrepayRequest(@DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent) {}

    /** periodEnd is the last day covered (inclusive), as users read it. */
    public record InstallmentView(UUID id, int sequence, LocalDate periodStart, LocalDate periodEnd, LocalDate billingDate, BigDecimal amount,
                                  SubscriptionInstallment.Status status, UUID saleId, String saleNumber) {}

    public record PreviewView(List<InstallmentView> installments, int installmentCount, BigDecimal total, LocalDate endDate, String summary) {}

    public record View(UUID id, String subscriptionNumber, Subscription.Status status, UUID customerId, String customerName,
                       UUID productId, String productName, UUID warehouseId, String warehouseCode,
                       BillingFrequency billingFrequency, String billingFrequencyLabel, int termMonths, BigDecimal monthlyPrice,
                       int installmentCount, BigDecimal totalAmount, BigDecimal invoicedAmount, String currency,
                       LocalDate startDate, LocalDate endDate, LocalDate nextBillingDate, BigDecimal nextAmount,
                       boolean prepaid, BigDecimal prepaidDiscountPercent, BigDecimal prepaidAmount,
                       String cancellationReason, String cancelledBy, Instant cancelledAt, String lastBillingError,
                       String createdBy, Instant createdAt, List<InstallmentView> installments) {}

    /** What cancelling today would mean: charges invoiced now (current period) and charges that are dropped. */
    public record CancellationPreview(List<InstallmentView> chargedNow, BigDecimal amountChargedNow, int cancelledCount,
                                      BigDecimal amountCancelled, String message) {}

    public record OptionView(String code, String label, String description) {}

    public record TermView(int months, String label) {}

    public record OptionsView(List<OptionView> billingModes, List<OptionView> frequencies, List<TermView> terms, boolean canDiscount) {}
}
