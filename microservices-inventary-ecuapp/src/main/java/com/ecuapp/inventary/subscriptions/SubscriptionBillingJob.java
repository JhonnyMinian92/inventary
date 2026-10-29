package com.ecuapp.inventary.subscriptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Generates the invoices of subscription charges whose billing date has arrived and closes finished plans.
 * Each subscription is processed in its own transaction; a failure (e.g. inactive customer) is recorded on the
 * subscription so it is visible to users, and the job retries on the next run. Invoice keys make retries safe.
 */
@Component
public class SubscriptionBillingJob {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingJob.class);
    private final SubscriptionService service;

    public SubscriptionBillingJob(SubscriptionService service) { this.service = service; }

    @Scheduled(cron = "${app.subscriptions.billing-cron:0 5 * * * *}", zone = "${app.reports.time-zone:America/Guayaquil}")
    public void run() { runFor(service.today()); }

    /** Returns the number of invoices generated. */
    public int runFor(LocalDate day) {
        int invoices = 0;
        for (UUID id : service.dueSubscriptionIds(day)) {
            try {
                invoices += service.billDue(id, day);
            } catch (RuntimeException ex) {
                log.warn("Subscription billing failed subscriptionId={} reason={}", id, ex.getMessage());
                service.recordBillingError(id, "No se pudo generar la factura: " + ex.getMessage());
            }
        }
        for (UUID id : service.finishedSubscriptionIds(day)) service.completeIfFinished(id, day);
        return invoices;
    }
}
