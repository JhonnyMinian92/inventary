package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Transactional inventory cost layer engine. Receipts create layers; issue movements consume layers;
 * each allocation is recorded append-only in inventory_valuation_entry.
 */
@Service
public class InventoryCostingService {
    private static final BigDecimal ZERO_QTY = new BigDecimal("0.000");
    private static final BigDecimal ZERO_COST = new BigDecimal("0.0000");

    private final InventoryCostingConfigRepository configs;
    private final InventoryCostLayerRepository layers;
    private final InventoryValuationEntryRepository entries;
    private final StockMovementRepository movements;

    public InventoryCostingService(InventoryCostingConfigRepository configs,
                                   InventoryCostLayerRepository layers,
                                   InventoryValuationEntryRepository entries,
                                   StockMovementRepository movements) {
        this.configs = configs;
        this.layers = layers;
        this.entries = entries;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public CostingMethod currentMethod() {
        return policy();
    }

    /**
     * Return the weighted unit cost which should be snapshotted on an outgoing movement.
     * The caller holds the product/warehouse balance lock, and this query also locks available layers.
     */
    @Transactional
    public BigDecimal currentIssueUnitCost(Product product, WarehouseLocation location,
                                           BigDecimal quantity, BigDecimal fallbackUnitCost) {
        if (location == null || quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("No se puede valorar una salida sin ubicación y cantidad válidas.");
        }
        CostingMethod method = policy();
        List<InventoryCostLayer> open = layers.findByProductIdAndLocationIdAndRemainingQuantityGreaterThanOrderByReceivedAtAscIdAsc(
                product.getId(), location.getId(), ZERO_QTY);
        BigDecimal remaining = quantity.setScale(3, RoundingMode.UNNECESSARY);
        BigDecimal allocatedValue = BigDecimal.ZERO;
        BigDecimal available = open.stream().map(InventoryCostLayer::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(remaining) < 0) {
            throw new BusinessRuleException("No existen capas de costo suficientes para valorar la salida de "
                    + product.getSku() + ". Revise la valoración inicial del inventario.");
        }
        if (method == CostingMethod.MOVING_AVERAGE) {
            BigDecimal value = open.stream()
                    .map(layer -> layer.getRemainingQuantity().multiply(layer.getUnitCost()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal average = value.divide(available, 4, RoundingMode.HALF_UP);
            return average.setScale(2, RoundingMode.HALF_UP);
        }
        for (InventoryCostLayer layer : open) {
            if (remaining.signum() == 0) break;
            BigDecimal used = remaining.min(layer.getRemainingQuantity());
            allocatedValue = allocatedValue.add(used.multiply(layer.getUnitCost()));
            remaining = remaining.subtract(used);
        }
        return allocatedValue.divide(quantity, 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
    }

    /** Resolve the inbound cost before an immutable stock_movement row is inserted. */
    @Transactional(readOnly = true)
    public BigDecimal inboundUnitCost(Product product, MovementType type, String referenceType,
                                     UUID referenceId, UUID sourceLineId, BigDecimal fallbackUnitCost) {
        if (type == MovementType.SALE_CANCEL_IN
                || (type == MovementType.RETURN_IN && "SALES_RETURN".equals(referenceType))) {
            return findOriginalSaleUnitCost(sourceLineId).orElse(scaleCost(fallbackUnitCost));
        }
        if (type == MovementType.TRANSFER_IN && referenceType != null && referenceId != null) {
            return movements.findFirstByMovementTypeAndReferenceTypeAndReferenceIdAndSourceLineIdOrderByOccurredAtAsc(
                            MovementType.TRANSFER_OUT, referenceType, referenceId, sourceLineId)
                    .flatMap(this::averageValuedUnitCost)
                    .orElse(scaleCost(fallbackUnitCost));
        }
        return scaleCost(fallbackUnitCost);
    }

    @Transactional
    public void recordInbound(StockMovement movement) {
        WarehouseLocation location = movement.getToLocation();
        if (location == null) return;
        CostingMethod method = policy();
        BigDecimal quantity = movement.getQuantity().setScale(3, RoundingMode.UNNECESSARY);
        String currency = movement.getProduct().getCurrency();

        if (movement.getMovementType() == MovementType.SALE_CANCEL_IN
                && restoreCancelledSaleLayers(movement, method, currency, quantity)) {
            if (method == CostingMethod.MOVING_AVERAGE) revalueOpenLayers(movement.getProduct().getId(), location.getId());
            return;
        }

        BigDecimal unitCost = scaleCost(movement.getUnitCost());
        InventoryCostLayer layer = layers.saveAndFlush(new InventoryCostLayer(
                movement.getProduct(), location, movement, quantity, quantity, unitCost, currency, movement.getOccurredAt()));
        entries.saveAndFlush(new InventoryValuationEntry(movement, layer, method, quantity, unitCost, currency));

        if (method == CostingMethod.MOVING_AVERAGE) revalueOpenLayers(movement.getProduct().getId(), location.getId());
    }

    @Transactional
    public void recordOutbound(StockMovement movement) {
        WarehouseLocation location = movement.getFromLocation();
        if (location == null) return;
        CostingMethod method = policy();
        BigDecimal quantity = movement.getQuantity().setScale(3, RoundingMode.UNNECESSARY);
        String currency = movement.getProduct().getCurrency();
        List<InventoryCostLayer> open = layers.findByProductIdAndLocationIdAndRemainingQuantityGreaterThanOrderByReceivedAtAscIdAsc(
                movement.getProduct().getId(), location.getId(), ZERO_QTY);
        BigDecimal available = open.stream().map(InventoryCostLayer::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(quantity) < 0) {
            throw new BusinessRuleException("Las capas de costo no concilian con el saldo para "
                    + movement.getProduct().getSku() + ". No se contabilizó la salida.");
        }

        BigDecimal average = BigDecimal.ZERO;
        if (method == CostingMethod.MOVING_AVERAGE) {
            BigDecimal inventoryValue = open.stream()
                    .map(layer -> layer.getRemainingQuantity().multiply(layer.getUnitCost()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            average = inventoryValue.divide(available, 4, RoundingMode.HALF_UP);
        }

        BigDecimal remaining = quantity;
        for (InventoryCostLayer layer : open) {
            if (remaining.signum() == 0) break;
            BigDecimal used = remaining.min(layer.getRemainingQuantity());
            BigDecimal unitCost = method == CostingMethod.FIFO ? scaleCost(layer.getUnitCost()) : average;
            layer.consume(used);
            entries.save(new InventoryValuationEntry(movement, layer, method, used, unitCost, currency));
            remaining = remaining.subtract(used);
        }
        entries.flush();
        if (method == CostingMethod.MOVING_AVERAGE) revalueOpenLayers(movement.getProduct().getId(), location.getId());
    }

    private boolean restoreCancelledSaleLayers(StockMovement cancellation, CostingMethod method,
                                               String currency, BigDecimal quantity) {
        if (cancellation.getSourceLineId() == null) return false;
        StockMovement original = movements.findFirstByMovementTypeAndSourceLineIdOrderByOccurredAtAsc(
                MovementType.SALE, cancellation.getSourceLineId()).orElse(null);
        if (original == null) return false;
        List<InventoryValuationEntry> originalEntries = entries.findByStockMovementIdOrderByCreatedAtAsc(original.getId());
        BigDecimal remaining = quantity;
        boolean restored = false;
        for (InventoryValuationEntry originalEntry : originalEntries) {
            if (remaining.signum() == 0) break;
            if (originalEntry.getCostLayer() == null) continue;
            BigDecimal restore = remaining.min(originalEntry.getQuantity());
            InventoryCostLayer sourceLayer = originalEntry.getCostLayer();
            sourceLayer.restore(restore);
            entries.save(new InventoryValuationEntry(cancellation, sourceLayer, method, restore,
                    sourceLayer.getUnitCost(), currency));
            remaining = remaining.subtract(restore);
            restored = true;
        }
        if (remaining.signum() > 0) {
            // Historical sales created before costing was activated have no allocation rows.
            InventoryCostLayer legacyLayer = layers.saveAndFlush(new InventoryCostLayer(
                    cancellation.getProduct(), cancellation.getToLocation(), cancellation, remaining, remaining,
                    scaleCost(cancellation.getUnitCost()), currency, cancellation.getOccurredAt()));
            entries.save(new InventoryValuationEntry(cancellation, legacyLayer, method, remaining,
                    legacyLayer.getUnitCost(), currency));
            restored = true;
        }
        entries.flush();
        return restored;
    }

    private java.util.Optional<BigDecimal> findOriginalSaleUnitCost(UUID sourceLineId) {
        if (sourceLineId == null) return java.util.Optional.empty();
        return movements.findFirstByMovementTypeAndSourceLineIdOrderByOccurredAtAsc(MovementType.SALE, sourceLineId)
                .flatMap(this::averageValuedUnitCost);
    }

    private java.util.Optional<BigDecimal> averageValuedUnitCost(StockMovement movement) {
        List<InventoryValuationEntry> movementEntries = entries.findByStockMovementIdOrderByCreatedAtAsc(movement.getId());
        BigDecimal quantity = movementEntries.stream().map(InventoryValuationEntry::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (quantity.signum() == 0) return java.util.Optional.empty();
        BigDecimal total = movementEntries.stream().map(InventoryValuationEntry::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return java.util.Optional.of(total.divide(quantity, 4, RoundingMode.HALF_UP));
    }

    private void revalueOpenLayers(UUID productId, UUID locationId) {
        List<InventoryCostLayer> open = layers.findByProductIdAndLocationIdAndRemainingQuantityGreaterThanOrderByReceivedAtAscIdAsc(
                productId, locationId, ZERO_QTY);
        BigDecimal quantity = open.stream().map(InventoryCostLayer::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (quantity.signum() == 0) return;
        BigDecimal value = open.stream().map(layer -> layer.getRemainingQuantity().multiply(layer.getUnitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = value.divide(quantity, 4, RoundingMode.HALF_UP);
        open.forEach(layer -> layer.revalue(average));
    }

    private CostingMethod policy() {
        return configs.findBySingletonKey("DEFAULT")
                .map(InventoryCostingConfig::getMethod)
                .orElseThrow(() -> new ResourceNotFoundException("Política de valoración", "DEFAULT"));
    }

    private BigDecimal scaleCost(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new BusinessRuleException("El costo unitario no puede ser negativo.");
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
