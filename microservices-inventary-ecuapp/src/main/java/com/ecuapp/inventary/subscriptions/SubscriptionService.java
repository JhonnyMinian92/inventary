package com.ecuapp.inventary.subscriptions;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.partners.BusinessPartner;
import com.ecuapp.inventary.partners.BusinessPartnerRepository;
import com.ecuapp.inventary.partners.PartnerType;
import com.ecuapp.inventary.sales.Sale;
import com.ecuapp.inventary.sales.SaleChannel;
import com.ecuapp.inventary.sales.SaleDtos;
import com.ecuapp.inventary.sales.SaleRepository;
import com.ecuapp.inventary.sales.SaleService;
import com.ecuapp.inventary.warehouse.Warehouse;
import com.ecuapp.inventary.warehouse.WarehouseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Subscriptions to services. Each charge of the plan becomes a regular sale (the invoice) on its billing date; the
 * scheduled {@link SubscriptionBillingJob} generates them, and every invoice uses an idempotency key derived from the
 * charge so retries never bill twice.
 * <ul>
 * <li>Cancelling charges only the current period (already invoiced or invoiced at that moment); later charges are dropped.</li>
 * <li>Prepaying invoices every pending charge at once; only users with sales:discount may apply a discount (default none).</li>
 * </ul>
 */
@Service
public class SubscriptionService {
    private static final String CREATE_SCOPE = "SUBSCRIPTION_CREATE";
    private static final Set<String> SORT = Set.of("createdAt", "startDate", "subscriptionNumber", "status");
    static final String SYSTEM_ACTOR = "sistema";

    private final SubscriptionRepository subscriptions;
    private final ProductRepository products;
    private final BusinessPartnerRepository partners;
    private final WarehouseRepository warehouses;
    private final SaleService saleService;
    private final SaleRepository sales;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final AuditService audit;
    private final ZoneId zone;

    public SubscriptionService(SubscriptionRepository subscriptions, ProductRepository products, BusinessPartnerRepository partners,
                               WarehouseRepository warehouses, SaleService saleService, SaleRepository sales,
                               IdempotencyRecordRepository idempotencyRecords, AuditService audit,
                               @Value("${app.reports.time-zone:America/Guayaquil}") String zone) {
        this.subscriptions = subscriptions;
        this.products = products;
        this.partners = partners;
        this.warehouses = warehouses;
        this.saleService = saleService;
        this.sales = sales;
        this.idempotencyRecords = idempotencyRecords;
        this.audit = audit;
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() { return LocalDate.now(zone); }

    public SubscriptionDtos.OptionsView options(boolean canDiscount) {
        return new SubscriptionDtos.OptionsView(
            Arrays.stream(ServiceBilling.values()).map(b -> new SubscriptionDtos.OptionView(b.name(), b.label(), b.description())).toList(),
            Arrays.stream(BillingFrequency.values()).map(f -> new SubscriptionDtos.OptionView(f.name(), f.label(), f.description())).toList(),
            BillingFrequency.TERMS.stream().map(m -> new SubscriptionDtos.TermView(m, m == 1 ? "1 mes" : m == 12 ? "1 año" : m + " meses")).toList(),
            canDiscount);
    }

    public SubscriptionDtos.PreviewView preview(SubscriptionDtos.PreviewRequest request) {
        LocalDate start = request.startDate() == null ? today() : request.startDate();
        List<SubscriptionSchedule.Period> periods = SubscriptionSchedule.build(start, request.termMonths(), request.billingFrequency(), request.monthlyPrice());
        BigDecimal total = SubscriptionSchedule.total(periods);
        List<SubscriptionDtos.InstallmentView> views = periods.stream().map(p -> new SubscriptionDtos.InstallmentView(null, p.sequence(), p.start(),
            p.end().minusDays(1), p.billingDate(), p.amount(), SubscriptionInstallment.Status.SCHEDULED, null, null)).toList();
        return new SubscriptionDtos.PreviewView(views, views.size(), total, start.plusMonths(request.termMonths()).minusDays(1),
            summary(request.billingFrequency(), periods, total));
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionDtos.View> list(Subscription.Status status, UUID customerId, Pageable pageable) {
        Page<Subscription> page = subscriptions.search(status, customerId, Pages.of(pageable, SORT, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, Sale> invoices = invoicesOf(page.getContent());
        return page.map(s -> view(s, invoices, false));
    }

    @Transactional(readOnly = true)
    public SubscriptionDtos.View get(UUID id) {
        Subscription s = require(id);
        return view(s, invoicesOf(List.of(s)), true);
    }

    @Transactional
    public SubscriptionDtos.View create(String rawKey, SubscriptionDtos.CreateRequest request, String actor, boolean canDiscount) {
        String key = normalizeKey(rawKey);
        String hash = sha256(String.join("|", String.valueOf(request.customerId()), String.valueOf(request.productId()), String.valueOf(request.warehouseId()),
            String.valueOf(request.startDate()), String.valueOf(request.billingFrequency()), String.valueOf(request.termMonths()),
            plain(request.monthlyPrice()), String.valueOf(request.prepay()), plain(request.discountPercent())));
        idempotencyRecords.insertIfAbsent(UUID.randomUUID(), CREATE_SCOPE, key, hash);
        IdempotencyRecord record = idempotencyRecords.findForUpdate(CREATE_SCOPE, key)
            .orElseThrow(() -> new IllegalStateException("No se pudo reservar la clave de idempotencia."));
        if (!hash.equals(record.getRequestHash())) throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
        if (record.getStatus() == IdempotencyRecord.RecordStatus.COMPLETED && record.getResourceId() != null) return get(record.getResourceId());

        BusinessPartner customer = partners.findForUpdateByIdAndTypeIn(request.customerId(), List.of(PartnerType.CUSTOMER, PartnerType.BOTH))
            .orElseThrow(() -> new ResourceNotFoundException("Cliente", request.customerId()));
        if (!customer.isActive()) throw new BusinessRuleException("El cliente está inactivo.");
        Product product = products.findById(request.productId()).orElseThrow(() -> new ResourceNotFoundException("Servicio", request.productId()));
        if (!product.isActive() || product.getKind() != ProductKind.SERVICE || !product.isSubscription())
            throw new BusinessRuleException("Elija un servicio activo que se cobre por suscripción.");
        Warehouse warehouse = warehouses.findById(request.warehouseId()).orElseThrow(() -> new ResourceNotFoundException("Almacén", request.warehouseId()));
        if (!warehouse.isActive()) throw new BusinessRuleException("El almacén o local está inactivo.");

        LocalDate today = today();
        LocalDate start = request.startDate() == null ? today : request.startDate();
        if (start.isBefore(today)) throw new BusinessRuleException("La fecha de inicio no puede ser anterior a hoy.");
        if (start.isAfter(today.plusYears(1))) throw new BusinessRuleException("La fecha de inicio no puede ser mayor a un año.");
        BillingFrequency frequency = request.billingFrequency() == null ? product.getBillingFrequency() : request.billingFrequency();
        int term = request.termMonths() == null ? product.getTermMonths() : request.termMonths();
        BigDecimal monthly = (request.monthlyPrice() == null ? product.getSalePrice() : request.monthlyPrice()).setScale(2, RoundingMode.HALF_UP);

        String number = "SUS-" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Subscription subscription = subscriptions.saveAndFlush(new Subscription(number, customer, product, warehouse, frequency, term, monthly, start, actor));
        if (request.prepay()) prepayPending(subscription, request.discountPercent(), actor, canDiscount, today);
        else invoiceDue(subscription, today, actor);
        audit.record(actor, "SUBSCRIPTION_CREATED", "SUBSCRIPTION", subscription.getId(), Map.of("number", number, "customerId", customer.getId().toString(),
            "productId", product.getId().toString(), "frequency", frequency.name(), "termMonths", term, "total", subscription.getTotalAmount().toPlainString()));
        record.markCompleted("SUBSCRIPTION", subscription.getId(), (short) 201);
        idempotencyRecords.saveAndFlush(record);
        return get(subscription.getId());
    }

    @Transactional(readOnly = true)
    public SubscriptionDtos.CancellationPreview cancellationPreview(UUID id) {
        Subscription s = require(id);
        LocalDate today = today();
        List<SubscriptionInstallment> charged = s.getStatus() == Subscription.Status.ACTIVE ? chargedOnCancel(s, today) : List.of();
        List<SubscriptionInstallment> dropped = s.getStatus() == Subscription.Status.ACTIVE
            ? s.scheduled().stream().filter(i -> !charged.contains(i)).toList() : List.of();
        BigDecimal now = sum(charged), cancelled = sum(dropped);
        String message = charged.isEmpty()
            ? "No se generará ningún cobro adicional" + (dropped.isEmpty() ? "." : "; se anularán " + dropped.size() + " cobros futuros por " + money(cancelled) + ".")
            : "Se facturará ahora el periodo vigente por " + money(now) + (dropped.isEmpty() ? "." : " y se anularán " + dropped.size() + " cobros futuros por " + money(cancelled) + ".");
        return new SubscriptionDtos.CancellationPreview(charged.stream().map(i -> installmentView(i, Map.of())).toList(), now, dropped.size(), cancelled, message);
    }

    @Transactional
    public SubscriptionDtos.View cancel(UUID id, String reason, String actor) {
        Subscription s = subscriptions.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Suscripción", id));
        if (s.getStatus() != Subscription.Status.ACTIVE) throw new ConflictException("Solo se puede cancelar una suscripción activa.");
        LocalDate today = today();
        List<SubscriptionInstallment> charged = chargedOnCancel(s, today);
        for (SubscriptionInstallment installment : charged)
            installment.markInvoiced(invoice(s, installment.getId().toString(), installment.getAmount(), actor));
        s.cancel(reason.trim(), actor);
        audit.record(actor, "SUBSCRIPTION_CANCELLED", "SUBSCRIPTION", s.getId(), Map.of("number", s.getSubscriptionNumber(), "reason", reason.trim(),
            "chargedNow", sum(charged).toPlainString()));
        subscriptions.flush();
        return get(id);
    }

    @Transactional
    public SubscriptionDtos.View prepay(UUID id, BigDecimal discountPercent, String actor, boolean canDiscount) {
        Subscription s = subscriptions.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Suscripción", id));
        if (s.getStatus() != Subscription.Status.ACTIVE) throw new ConflictException("Solo se puede pagar por adelantado una suscripción activa.");
        prepayPending(s, discountPercent, actor, canDiscount, today());
        subscriptions.flush();
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<UUID> dueSubscriptionIds(LocalDate day) { return subscriptions.findIdsWithDueInstallments(day); }

    @Transactional(readOnly = true)
    public List<UUID> finishedSubscriptionIds(LocalDate day) { return subscriptions.findIdsToComplete(day); }

    /** Invoices every charge due on or before the given day; used by the billing job, one transaction per subscription. */
    @Transactional
    public int billDue(UUID id, LocalDate day) {
        Subscription s = subscriptions.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Suscripción", id));
        if (s.getStatus() != Subscription.Status.ACTIVE) return 0;
        int invoiced = invoiceDue(s, day, SYSTEM_ACTOR);
        s.recordBillingError(null);
        return invoiced;
    }

    @Transactional
    public void completeIfFinished(UUID id, LocalDate day) {
        subscriptions.findForUpdate(id).ifPresent(s -> s.completeIfDone(day));
    }

    @Transactional
    public void recordBillingError(UUID id, String message) {
        subscriptions.findForUpdate(id).ifPresent(s -> s.recordBillingError(message));
    }

    private int invoiceDue(Subscription s, LocalDate day, String actor) {
        int count = 0;
        for (SubscriptionInstallment installment : s.scheduled()) {
            if (installment.getBillingDate().isAfter(day)) continue;
            installment.markInvoiced(invoice(s, installment.getId().toString(), installment.getAmount(), actor));
            count++;
        }
        s.completeIfDone(day);
        return count;
    }

    private void prepayPending(Subscription s, BigDecimal discountPercent, String actor, boolean canDiscount, LocalDate today) {
        List<SubscriptionInstallment> pending = s.scheduled();
        if (pending.isEmpty()) throw new ConflictException("La suscripción no tiene cobros pendientes.");
        BigDecimal discount = discountPercent == null ? BigDecimal.ZERO.setScale(2) : discountPercent.setScale(2, RoundingMode.HALF_UP);
        if (discount.signum() > 0 && !canDiscount) throw new AccessDeniedException("Solo el propietario puede aplicar descuentos por pago adelantado.");
        BigDecimal gross = sum(pending);
        BigDecimal net = gross.multiply(BigDecimal.valueOf(100).subtract(discount)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        UUID saleId = invoice(s, "PREPAY-" + s.getId(), net, actor);
        pending.forEach(installment -> installment.markInvoiced(saleId));
        s.markPrepaid(discount, net);
        s.completeIfDone(today);
        audit.record(actor, "SUBSCRIPTION_PREPAID", "SUBSCRIPTION", s.getId(), Map.of("number", s.getSubscriptionNumber(),
            "gross", gross.toPlainString(), "discountPercent", discount.toPlainString(), "net", net.toPlainString()));
    }

    /** Charges of the current period (or overdue ones) are invoiced on cancellation; future periods are dropped. */
    private List<SubscriptionInstallment> chargedOnCancel(Subscription s, LocalDate today) {
        return s.scheduled().stream().filter(i -> !i.getBillingDate().isAfter(today) || i.covers(today)).toList();
    }

    /** The invoice is a regular sale created through the sales flow, keyed by the charge so it can never be duplicated. */
    private UUID invoice(Subscription s, String chargeKey, BigDecimal amount, String actor) {
        SaleDtos.CreateRequest request = new SaleDtos.CreateRequest(s.getWarehouse().getId(), null, null, SaleChannel.SUBSCRIPTION,
            List.of(new SaleDtos.LineRequest(s.getProduct().getId(), BigDecimal.ONE.setScale(3), amount)), s.getCustomer().getId());
        return saleService.create("SUB-" + chargeKey, request, actor).id();
    }

    private Map<UUID, Sale> invoicesOf(Collection<Subscription> list) {
        Set<UUID> ids = list.stream().flatMap(s -> s.getInstallments().stream()).map(SubscriptionInstallment::getSaleId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return sales.findAllById(ids).stream().collect(Collectors.toMap(Sale::getId, Function.identity()));
    }

    private SubscriptionDtos.View view(Subscription s, Map<UUID, Sale> invoices, boolean withInstallments) {
        List<SubscriptionInstallment> installments = s.getInstallments();
        Optional<SubscriptionInstallment> next = s.getStatus() == Subscription.Status.ACTIVE ? s.scheduled().stream().findFirst() : Optional.empty();
        BigDecimal invoiced = installments.stream().map(SubscriptionInstallment::getSaleId).filter(Objects::nonNull).distinct()
            .map(invoices::get).filter(Objects::nonNull).map(Sale::getTotal).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new SubscriptionDtos.View(s.getId(), s.getSubscriptionNumber(), s.getStatus(), s.getCustomer().getId(), s.getCustomer().getName(),
            s.getProduct().getId(), s.getProduct().getName(), s.getWarehouse().getId(), s.getWarehouse().getCode(),
            s.getBillingFrequency(), s.getBillingFrequency().label(), s.getTermMonths(), s.getMonthlyPrice(),
            installments.size(), s.getTotalAmount(), invoiced, s.getCurrency(), s.getStartDate(), s.getEndDate().minusDays(1),
            next.map(SubscriptionInstallment::getBillingDate).orElse(null), next.map(SubscriptionInstallment::getAmount).orElse(null),
            s.isPrepaid(), s.getPrepaidDiscountPercent(), s.getPrepaidAmount(), s.getCancellationReason(), s.getCancelledBy(), s.getCancelledAt(),
            s.getLastBillingError(), s.getCreatedBy(), s.getCreatedAt(),
            withInstallments ? installments.stream().map(i -> installmentView(i, invoices)).toList() : null);
    }

    private SubscriptionDtos.InstallmentView installmentView(SubscriptionInstallment i, Map<UUID, Sale> invoices) {
        Sale sale = i.getSaleId() == null ? null : invoices.get(i.getSaleId());
        return new SubscriptionDtos.InstallmentView(i.getId(), i.getSequence(), i.getPeriodStart(), i.getPeriodEnd().minusDays(1), i.getBillingDate(),
            i.getAmount(), i.getStatus(), i.getSaleId(), sale == null ? null : sale.getSaleNumber());
    }

    private String summary(BillingFrequency frequency, List<SubscriptionSchedule.Period> periods, BigDecimal total) {
        if (frequency == BillingFrequency.AT_END) return "1 cobro de " + money(total) + " al finalizar el plan";
        boolean same = periods.stream().map(SubscriptionSchedule.Period::amount).distinct().count() == 1;
        String each = same ? money(periods.get(0).amount()) : "aprox. " + money(total.divide(BigDecimal.valueOf(periods.size()), 2, RoundingMode.HALF_UP));
        return periods.size() + (periods.size() == 1 ? " cobro de " : " cobros de ") + each + " " + frequency.label().toLowerCase(Locale.ROOT) + " · total " + money(total);
    }

    private Subscription require(UUID id) { return subscriptions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Suscripción", id)); }

    private static BigDecimal sum(List<SubscriptionInstallment> list) {
        return list.stream().map(SubscriptionInstallment::getAmount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-EC"));
        format.setCurrency(Currency.getInstance("USD"));
        return format.format(value);
    }

    private static String plain(BigDecimal value) { return value == null ? "" : value.stripTrailingZeros().toPlainString(); }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 120)
            throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");
        return key.trim();
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("No se pudo calcular huella de idempotencia.", ex); }
    }
}
