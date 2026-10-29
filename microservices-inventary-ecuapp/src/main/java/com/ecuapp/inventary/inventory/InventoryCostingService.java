package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory valuation engine (FIFO or moving average).
 * <p>
 * Every physical movement is valued inside the same transaction that changes the stock balance. Callers already hold
 * the PESSIMISTIC_WRITE lock of the product/warehouse balance, which serializes valuation per product and location;
 * the cost state row is locked as well to stay correct even if a future flow forgets that rule.
 * <ul>
 * <li>{@link #inbound} values an entry at an explicit unit cost and opens a FIFO layer.</li>
 * <li>{@link #outbound} consumes layers in FIFO order and values the issue with the active method.</li>
 * <li>{@link #passThrough} records a cost for paired entry/exit movements that do not change stock (scrapped returns).</li>
 * </ul>
 * Movements are immutable once inserted, so callers first obtain a {@link Valuation}, persist the movement with
 * {@link Valuation#movementUnitCost()} and finally call {@link #post} to append the valuation ledger.
 */
@Service
public class InventoryCostingService {
    private static final String CONFIG_KEY = "DEFAULT";

    public record Portion(InventoryCostLayer layer, BigDecimal quantity, BigDecimal unitCost, BigDecimal totalCost) {}

    public record Valuation(CostingMethod method, boolean opensLayer, Product product, WarehouseLocation location,
                            BigDecimal quantity, BigDecimal unitCost, BigDecimal totalCost, String currency,
                            List<Portion> portions) {
        /** Unit cost stored on the stock movement (scale 2); the valuation ledger keeps scale 4. */
        public BigDecimal movementUnitCost() { return unitCost.setScale(2, RoundingMode.HALF_UP); }
    }

    private final InventoryCostingConfigRepository configs;
    private final InventoryCostStateRepository states;
    private final InventoryCostLayerRepository layers;
    private final InventoryValuationEntryRepository entries;
    private final AuditService audit;

    public InventoryCostingService(InventoryCostingConfigRepository configs, InventoryCostStateRepository states,
                                   InventoryCostLayerRepository layers, InventoryValuationEntryRepository entries,
                                   AuditService audit) {
        this.configs = configs;
        this.states = states;
        this.layers = layers;
        this.entries = entries;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public InventoryCostingConfig policy() {
        return configs.findBySingletonKey(CONFIG_KEY)
            .orElseThrow(() -> new IllegalStateException("No existe la política de valoración de inventario."));
    }

    @Transactional
    public InventoryCostingConfig changePolicy(CostingMethod method, String actor) {
        InventoryCostingConfig config = configs.findBySingletonKey(CONFIG_KEY)
            .orElseThrow(() -> new IllegalStateException("No existe la política de valoración de inventario."));
        if (config.getMethod() == method) return config;
        // Consistency principle: the valuation method cannot change after valued movements exist.
        if (entries.count() > 0) {
            throw new ConflictException("El método de valoración no puede cambiarse después de registrar movimientos valorizados.");
        }
        CostingMethod previous = config.getMethod();
        config.changeMethod(method, actor);
        audit.record(actor, "COSTING_POLICY_CHANGED", "INVENTORY_COSTING_CONFIG", config.getId(),
            Map.of("previous", previous.name(), "method", method.name()));
        return config;
    }

    /** Values an inbound movement at an explicit unit cost; a FIFO layer is opened when the movement is posted. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Valuation inbound(Product product, WarehouseLocation location, BigDecimal quantity, BigDecimal unitCost) {
        requirePositive(quantity);
        CostingMethod method = policy().getMethod();
        BigDecimal cost = normalizeCost(unitCost);
        BigDecimal total = total(quantity, cost);
        InventoryCostState state = lockState(product, location);
        state.add(quantity, total);
        return new Valuation(method, true, product, location, quantity, cost, total, product.getCurrency(),
            List.of(new Portion(null, quantity, cost, total)));
    }

    /** Values an outbound movement with the active method, consuming FIFO layers for quantity traceability. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Valuation outbound(Product product, WarehouseLocation location, BigDecimal quantity) {
        return outbound(product, location, quantity, null, null);
    }

    /**
     * Outbound valuation that consumes the layer opened by {@code preferredSourceMovementId} first (purchase returns).
     * Under moving average the issue is valued at {@code preferredUnitCost} when provided.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Valuation outbound(Product product, WarehouseLocation location, BigDecimal quantity,
                              UUID preferredSourceMovementId, BigDecimal preferredUnitCost) {
        requirePositive(quantity);
        CostingMethod method = policy().getMethod();
        InventoryCostState state = lockState(product, location);
        BigDecimal average = state.averageCost();
        BigDecimal fallback = average != null ? average : normalizeCost(product.getCostPrice());

        List<InventoryCostLayer> open = new ArrayList<>(layers.findOpenForUpdate(product.getId(), location.getId()));
        if (preferredSourceMovementId != null) {
            open.sort((a, b) -> Boolean.compare(!a.getSourceMovement().getId().equals(preferredSourceMovementId),
                !b.getSourceMovement().getId().equals(preferredSourceMovementId)));
        }
        List<Portion> consumed = new ArrayList<>();
        BigDecimal pending = quantity;
        for (InventoryCostLayer layer : open) {
            if (pending.signum() == 0) break;
            BigDecimal taken = layer.consume(pending);
            if (taken.signum() > 0) {
                consumed.add(new Portion(layer, taken, layer.getUnitCost(), total(taken, layer.getUnitCost())));
                pending = pending.subtract(taken);
            }
        }
        if (pending.signum() > 0) {
            // Stock that predates valuation (or legacy data) is valued at the current average or catalog cost.
            consumed.add(new Portion(null, pending.setScale(3), fallback, total(pending, fallback)));
        }

        List<Portion> portions;
        BigDecimal totalCost;
        if (method == CostingMethod.FIFO) {
            portions = consumed;
            totalCost = consumed.stream().map(Portion::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
        } else {
            BigDecimal unit = preferredUnitCost != null ? normalizeCost(preferredUnitCost) : fallback;
            boolean depletes = quantity.compareTo(state.getQuantity()) >= 0;
            // Full depletion transfers the exact remaining value so rounding residues never stay in stock.
            totalCost = depletes && preferredUnitCost == null ? state.getTotalValue() : total(quantity, unit);
            portions = List.of(new Portion(null, quantity, unit, totalCost));
        }
        state.remove(quantity, totalCost);
        if (method == CostingMethod.FIFO && state.getQuantity().signum() > 0) {
            // Under FIFO the remaining value is exactly the value of the open layers when every unit is layered.
            List<InventoryCostLayer> remaining = layers.findOpen(product.getId(), location.getId());
            BigDecimal layeredQuantity = remaining.stream().map(InventoryCostLayer::getRemainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (layeredQuantity.compareTo(state.getQuantity()) == 0) {
                state.replaceValue(remaining.stream().map(l -> total(l.getRemainingQuantity(), l.getUnitCost()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }
        BigDecimal unitCost = totalCost.divide(quantity, 4, RoundingMode.HALF_UP);
        return new Valuation(method, false, product, location, quantity, unitCost, totalCost, product.getCurrency(), portions);
    }

    /** Values a movement that is immediately compensated (no change to layers or running state). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Valuation passThrough(Product product, WarehouseLocation location, BigDecimal quantity, BigDecimal unitCost) {
        requirePositive(quantity);
        BigDecimal cost = normalizeCost(unitCost);
        BigDecimal total = total(quantity, cost);
        return new Valuation(policy().getMethod(), false, product, location, quantity, cost, total, product.getCurrency(),
            List.of(new Portion(null, quantity, cost, total)));
    }

    /** Appends the valuation ledger for an already persisted movement and opens its FIFO layer when inbound. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(StockMovement movement, Valuation valuation) {
        if (valuation.opensLayer()) {
            InventoryCostLayer layer = layers.save(new InventoryCostLayer(valuation.product(), valuation.location(), movement,
                valuation.quantity(), valuation.unitCost(), valuation.currency()));
            entries.save(new InventoryValuationEntry(movement, layer, valuation.method(), valuation.quantity(),
                valuation.unitCost(), valuation.totalCost(), valuation.currency()));
            return;
        }
        if (valuation.method() == CostingMethod.MOVING_AVERAGE || valuation.portions().size() == 1) {
            Portion portion = valuation.portions().get(0);
            InventoryCostLayer layer = valuation.method() == CostingMethod.FIFO ? portion.layer() : null;
            entries.save(new InventoryValuationEntry(movement, layer, valuation.method(), valuation.quantity(),
                valuation.unitCost(), valuation.totalCost(), valuation.currency()));
            return;
        }
        for (Portion portion : valuation.portions()) {
            entries.save(new InventoryValuationEntry(movement, portion.layer(), valuation.method(), portion.quantity(),
                portion.unitCost(), portion.totalCost(), valuation.currency()));
        }
    }

    /** Weighted unit cost recorded for a movement, or null when the movement has no valuation entries. */
    @Transactional(readOnly = true)
    public BigDecimal valuedUnitCost(UUID movementId) {
        List<Object[]> rows = entries.totals(movementId);
        if (rows.isEmpty()) return null;
        BigDecimal quantity = (BigDecimal) rows.get(0)[0];
        BigDecimal total = (BigDecimal) rows.get(0)[1];
        if (quantity == null || quantity.signum() == 0) return null;
        return total.divide(quantity, 4, RoundingMode.HALF_UP);
    }

    /**
     * Read-only estimate of the unit cost an outbound movement of {@code quantity} would get right now
     * (same rules as {@link #outbound} but without consuming layers). Used to price sales without losses.
     */
    @Transactional(readOnly = true)
    public BigDecimal estimateOutboundUnitCost(Product product, WarehouseLocation location, BigDecimal quantity) {
        requirePositive(quantity);
        BigDecimal average = location == null ? null : states.find(product.getId(), location.getId()).map(InventoryCostState::averageCost).orElse(null);
        BigDecimal fallback = average != null ? average : normalizeCost(product.getCostPrice());
        if (location == null || policy().getMethod() != CostingMethod.FIFO) return fallback;
        BigDecimal pending = quantity;
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryCostLayer layer : layers.findOpen(product.getId(), location.getId())) {
            if (pending.signum() == 0) break;
            BigDecimal taken = layer.getRemainingQuantity().min(pending);
            total = total.add(total(taken, layer.getUnitCost()));
            pending = pending.subtract(taken);
        }
        if (pending.signum() > 0) total = total.add(total(pending, fallback));
        return total.divide(quantity, 4, RoundingMode.HALF_UP);
    }

    /** Best estimate of the current replacement cost: running average, last layer cost or catalog cost. */
    @Transactional(readOnly = true)
    public BigDecimal currentUnitCost(Product product, WarehouseLocation location) {
        BigDecimal average = states.find(product.getId(), location.getId()).map(InventoryCostState::averageCost).orElse(null);
        if (average != null) return average;
        return layers.findLatest(product.getId(), location.getId()).stream().findFirst()
            .map(InventoryCostLayer::getUnitCost).orElse(normalizeCost(product.getCostPrice()));
    }

    private InventoryCostState lockState(Product product, WarehouseLocation location) {
        states.insertIfAbsent(UUID.randomUUID(), product.getId(), location.getId(), product.getCurrency());
        return states.findForUpdate(product.getId(), location.getId())
            .orElseThrow(() -> new IllegalStateException("No se pudo asegurar el estado de valoración."));
    }

    private static BigDecimal total(BigDecimal quantity, BigDecimal unitCost) {
        return quantity.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeCost(BigDecimal value) {
        if (value == null || value.signum() < 0) return BigDecimal.ZERO.setScale(4);
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("La cantidad valorizada debe ser positiva.");
    }
}
