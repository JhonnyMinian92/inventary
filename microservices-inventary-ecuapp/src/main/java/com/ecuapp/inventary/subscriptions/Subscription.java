package com.ecuapp.inventary.subscriptions;

import com.ecuapp.inventary.catalog.BillingFrequency;
import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.partners.BusinessPartner;
import com.ecuapp.inventary.warehouse.Warehouse;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** A customer's subscription to a service: a fixed plan whose charges are invoiced automatically. */
@Entity
@Table(name = "subscription", uniqueConstraints = @UniqueConstraint(name = "uk_subscription_number", columnNames = "subscription_number"))
public class Subscription {
    public enum Status { ACTIVE, CANCELLED, COMPLETED }

    @Id private UUID id;
    @Column(name = "subscription_number", nullable = false, length = 48) private String subscriptionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.ACTIVE;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false) private BusinessPartner customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "warehouse_id", nullable = false) private Warehouse warehouse;
    @Enumerated(EnumType.STRING) @Column(name = "billing_frequency", nullable = false, length = 16) private BillingFrequency billingFrequency;
    @Column(name = "term_months", nullable = false) private int termMonths;
    @Column(name = "monthly_price", nullable = false, precision = 14, scale = 2) private BigDecimal monthlyPrice;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(nullable = false) private boolean prepaid;
    @Column(name = "prepaid_discount_percent", precision = 5, scale = 2) private BigDecimal prepaidDiscountPercent;
    @Column(name = "prepaid_amount", precision = 14, scale = 2) private BigDecimal prepaidAmount;
    @Column(name = "cancellation_reason", length = 1000) private String cancellationReason;
    @Column(name = "cancelled_by", length = 80) private String cancelledBy;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "last_billing_error", length = 500) private String lastBillingError;
    @Column(name = "created_by", nullable = false, length = 80) private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;
    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("sequence ASC")
    private List<SubscriptionInstallment> installments = new ArrayList<>();

    protected Subscription() {}

    Subscription(String number, BusinessPartner customer, Product product, Warehouse warehouse, BillingFrequency frequency, int termMonths,
                 BigDecimal monthlyPrice, LocalDate startDate, String createdBy) {
        this.id = UUID.randomUUID();
        this.subscriptionNumber = number;
        this.customer = customer;
        this.product = product;
        this.warehouse = warehouse;
        this.billingFrequency = frequency;
        this.termMonths = termMonths;
        this.monthlyPrice = monthlyPrice;
        this.currency = product.getCurrency();
        this.startDate = startDate;
        this.endDate = startDate.plusMonths(termMonths);
        this.createdBy = createdBy;
        List<SubscriptionSchedule.Period> periods = SubscriptionSchedule.build(startDate, termMonths, frequency, monthlyPrice);
        periods.forEach(period -> installments.add(new SubscriptionInstallment(this, period)));
        this.totalAmount = SubscriptionSchedule.total(periods);
    }

    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    List<SubscriptionInstallment> scheduled() {
        return installments.stream().filter(i -> i.getStatus() == SubscriptionInstallment.Status.SCHEDULED)
            .sorted(Comparator.comparingInt(SubscriptionInstallment::getSequence)).toList();
    }

    void markPrepaid(BigDecimal discountPercent, BigDecimal amount) {
        this.prepaid = true;
        this.prepaidDiscountPercent = discountPercent;
        this.prepaidAmount = amount;
    }

    void cancel(String reason, String actor) {
        this.status = Status.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledBy = actor;
        this.cancelledAt = Instant.now();
        installments.forEach(SubscriptionInstallment::cancel);
    }

    /** An active subscription finishes once its plan has ended and nothing is left to invoice. */
    void completeIfDone(LocalDate day) {
        if (status == Status.ACTIVE && scheduled().isEmpty() && !day.isBefore(endDate)) status = Status.COMPLETED;
    }

    void recordBillingError(String message) { this.lastBillingError = message == null ? null : message.length() > 500 ? message.substring(0, 500) : message; }

    public UUID getId() { return id; }
    public String getSubscriptionNumber() { return subscriptionNumber; }
    public Status getStatus() { return status; }
    public BusinessPartner getCustomer() { return customer; }
    public Product getProduct() { return product; }
    public Warehouse getWarehouse() { return warehouse; }
    public BillingFrequency getBillingFrequency() { return billingFrequency; }
    public int getTermMonths() { return termMonths; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isPrepaid() { return prepaid; }
    public BigDecimal getPrepaidDiscountPercent() { return prepaidDiscountPercent; }
    public BigDecimal getPrepaidAmount() { return prepaidAmount; }
    public String getCancellationReason() { return cancellationReason; }
    public String getCancelledBy() { return cancelledBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getLastBillingError() { return lastBillingError; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<SubscriptionInstallment> getInstallments() { return installments; }
}
