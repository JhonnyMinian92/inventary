package com.ecuapp.inventary.subscriptions;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One charge of a subscription. Once invoiced it points to the sale generated for it. */
@Entity
@Table(name = "subscription_installment")
public class SubscriptionInstallment {
    public enum Status { SCHEDULED, INVOICED, CANCELLED }

    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "subscription_id", nullable = false) private Subscription subscription;
    @Column(nullable = false) private int sequence;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Column(name = "billing_date", nullable = false) private LocalDate billingDate;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.SCHEDULED;
    @Column(name = "sale_id") private UUID saleId;
    @Column(name = "invoiced_at") private Instant invoicedAt;

    protected SubscriptionInstallment() {}

    SubscriptionInstallment(Subscription subscription, SubscriptionSchedule.Period period) {
        this.id = UUID.randomUUID();
        this.subscription = subscription;
        this.sequence = period.sequence();
        this.periodStart = period.start();
        this.periodEnd = period.end();
        this.billingDate = period.billingDate();
        this.amount = period.amount();
    }

    void markInvoiced(UUID saleId) {
        if (status != Status.SCHEDULED) throw new IllegalStateException("La cuota ya no está pendiente.");
        this.status = Status.INVOICED;
        this.saleId = saleId;
        this.invoicedAt = Instant.now();
    }

    void cancel() {
        if (status == Status.SCHEDULED) status = Status.CANCELLED;
    }

    /** True when the given day falls inside this charge's period. */
    boolean covers(LocalDate day) { return !day.isBefore(periodStart) && day.isBefore(periodEnd); }

    public UUID getId() { return id; }
    public Subscription getSubscription() { return subscription; }
    public int getSequence() { return sequence; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public LocalDate getBillingDate() { return billingDate; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public UUID getSaleId() { return saleId; }
    public Instant getInvoicedAt() { return invoicedAt; }
}
