package com.ecuapp.inventary.whatsapp;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.UnitOfMeasure;
import com.ecuapp.inventary.catalog.UnitOfMeasureRepository;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.sales.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Staff-side operations over WhatsApp orders. A payment confirmed here was verified by an authorized user
 * (bank transfer, cash on delivery, card terminal); no payment provider is called or simulated.
 */
@Service
public class WhatsAppOrderBackofficeService {
    private static final String SCOPE = "WHATSAPP_ORDER_CONFIRM";
    private static final Set<String> SORT = Set.of("createdAt", "orderNumber", "status", "total");
    private final WhatsAppOrderRepository orders;
    private final SaleRepository sales;
    private final StockBalanceRepository balances;
    private final StockMovementRepository movements;
    private final UnitOfMeasureRepository units;
    private final InventoryCostingService costing;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final OutboxEventRepository outbox;
    private final AuditService audit;

    public WhatsAppOrderBackofficeService(WhatsAppOrderRepository orders, SaleRepository sales, StockBalanceRepository balances,
                                          StockMovementRepository movements, UnitOfMeasureRepository units,
                                          InventoryCostingService costing, IdempotencyRecordRepository idempotencyRecords,
                                          OutboxEventRepository outbox, AuditService audit) {
        this.orders = orders;
        this.sales = sales;
        this.balances = balances;
        this.movements = movements;
        this.units = units;
        this.costing = costing;
        this.idempotencyRecords = idempotencyRecords;
        this.outbox = outbox;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<WhatsAppDtos.OrderView> list(WhatsAppOrderStatus status, String customerRef, Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null && !from.isBefore(to)) throw new InvalidQueryException("'from' debe ser anterior a 'to'.");
        String ref = customerRef == null || customerRef.isBlank() ? null : customerRef.trim().replaceAll("\\s+", "");
        return orders.search(status, ref, com.ecuapp.inventary.common.TimeRange.lower(from), com.ecuapp.inventary.common.TimeRange.upper(to), Pages.of(pageable, SORT, Sort.by(Sort.Direction.DESC, "createdAt")))
            .map(WhatsAppDtos.OrderView::from);
    }

    @Transactional(readOnly = true)
    public WhatsAppDtos.OrderView get(UUID id) {
        return WhatsAppDtos.OrderView.from(orders.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Orden WhatsApp", id)));
    }

    @Transactional
    public WhatsAppDtos.OrderView confirmPayment(UUID orderId, String rawKey, WhatsAppDtos.ConfirmPaymentRequest request, String actor) {
        String key = normalizeKey(rawKey);
        String reference = request.paymentReference().trim();
        String method = request.paymentMethod().trim();
        String hash = sha256(orderId + "|" + reference + "|" + method);
        int claimed = idempotencyRecords.insertIfAbsent(UUID.randomUUID(), SCOPE, key, hash);
        IdempotencyRecord record = idempotencyRecords.findForUpdate(SCOPE, key)
            .orElseThrow(() -> new IllegalStateException("No se pudo reservar la clave de idempotencia."));
        if (!hash.equals(record.getRequestHash())) throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
        if (claimed == 0) {
            if (record.getStatus() == IdempotencyRecord.RecordStatus.COMPLETED) return get(record.getResourceId());
            throw new ConflictException("Ya existe una operación con esta clave de idempotencia y aún no puede reproducirse.");
        }

        WhatsAppOrder order = orders.findForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Orden WhatsApp", orderId));
        if (order.getStatus() == WhatsAppOrderStatus.CONFIRMED) {
            if (!reference.equals(order.getPaymentReference())) throw new ConflictException("La orden ya fue confirmada con otra referencia de pago.");
            complete(record, order);
            return get(orderId);
        }
        if (order.getStatus() != WhatsAppOrderStatus.AWAITING_PAYMENT) throw new ConflictException("La orden ya no puede confirmarse en su estado actual.");
        if (!order.getExpiresAt().isAfter(Instant.now())) throw new ConflictException("La orden venció y su reserva será liberada; genere un nuevo pedido.");

        List<WhatsAppOrderItem> lines = new ArrayList<>(order.getItems());
        lines.sort(Comparator.comparing(i -> i.getProduct().getId().toString()));
        Map<UUID, StockBalance> locked = new HashMap<>();
        for (WhatsAppOrderItem line : lines) {
            StockBalance balance = balances.findForUpdate(line.getProduct().getId(), order.getWarehouse().getId())
                .orElseThrow(() -> new BusinessRuleException("No existe saldo para despachar " + line.getSkuSnapshot() + "."));
            balance.consumeReservation(line.getQuantity());
            locked.put(line.getProduct().getId(), balance);
        }

        String saleNumber = "VTA-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
            + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Sale sale = sales.saveAndFlush(new Sale(saleNumber, "WhatsApp " + order.getCustomerRef(), null, SaleChannel.WHATSAPP, order.getWarehouse(),
            order.getSubtotal(), order.getTax(), order.getTotal(), order.getCurrency(), "WA-CONFIRM-" + order.getId(), hash, actor, null));
        Map<UUID, SaleItem> itemByProduct = new HashMap<>();
        for (WhatsAppOrderItem line : lines) {
            SaleItem item = new SaleItem(line.getProduct(), line.getQuantity(), line.getUnitPrice(), line.getLineTotal());
            sale.addItem(item);
            itemByProduct.put(line.getProduct().getId(), item);
        }
        sales.flush();
        for (WhatsAppOrderItem line : lines) {
            StockBalance balance = locked.get(line.getProduct().getId());
            SaleItem item = itemByProduct.get(line.getProduct().getId());
            UnitOfMeasure unit = units.findById(line.getProduct().getUnitOfMeasureId())
                .orElseThrow(() -> new BusinessRuleException("No existe unidad de medida para " + line.getSkuSnapshot() + "."));
            InventoryCostingService.Valuation valuation = costing.outbound(line.getProduct(), balance.getLocation(), line.getQuantity());
            StockMovement movement = new StockMovement(line.getProduct(), order.getWarehouse(), MovementType.SALE, line.getQuantity(),
                valuation.movementUnitCost(), "SALE", sale.getId(), "SALE-WA-" + order.getId() + "-" + line.getProduct().getId(),
                sha256(sale.getId() + "|" + item.getId() + "|" + line.getQuantity().toPlainString()),
                "Salida por pedido WhatsApp " + order.getOrderNumber() + " confirmado", actor);
            movement.assignLocations(balance.getLocation(), null, unit, sale.getId().toString(), item.getId(), valuation.method());
            costing.post(movements.save(movement), valuation);
        }
        order.confirm(sale, reference, method, actor);
        outbox.save(new OutboxEvent("SALE", sale.getId(), "SALE_COMPLETED", Map.of("saleId", sale.getId().toString(),
            "saleNumber", saleNumber, "total", sale.getTotal().setScale(2, RoundingMode.UNNECESSARY).toPlainString(), "currency", sale.getCurrency(),
            "channel", SaleChannel.WHATSAPP.name())));
        outbox.save(new OutboxEvent("WHATSAPP_ORDER", order.getId(), "WHATSAPP_ORDER_CONFIRMED", Map.of("orderId", order.getId().toString(),
            "orderNumber", order.getOrderNumber(), "saleId", sale.getId().toString())));
        audit.record(actor, "WHATSAPP_ORDER_CONFIRMED", "WHATSAPP_ORDER", order.getId(), Map.of("orderNumber", order.getOrderNumber(),
            "saleNumber", saleNumber, "paymentMethod", method, "total", order.getTotal().toPlainString()));
        complete(record, order);
        return get(orderId);
    }

    private void complete(IdempotencyRecord record, WhatsAppOrder order) {
        record.markCompleted("WHATSAPP_ORDER", order.getId(), (short) 200);
        idempotencyRecords.saveAndFlush(record);
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 120)
            throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");
        return key.trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular la huella de la solicitud.", ex);
        }
    }
}
