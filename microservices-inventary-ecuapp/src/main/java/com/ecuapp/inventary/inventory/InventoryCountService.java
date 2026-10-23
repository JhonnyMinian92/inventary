package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.catalog.ProductKind;
import com.ecuapp.inventary.catalog.ProductRepository;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import com.ecuapp.inventary.warehouse.Warehouse;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import com.ecuapp.inventary.warehouse.WarehouseLocationRepository;
import com.ecuapp.inventary.warehouse.WarehouseRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class InventoryCountService {
    private final InventoryCountRepository counts;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final StockBalanceRepository balances;
    private final StockMovementRepository movements;
    private final WarehouseLocationRepository locations;
    private final AuditService audit;

    public InventoryCountService(InventoryCountRepository counts, ProductRepository products,
                                 WarehouseRepository warehouses, StockBalanceRepository balances,
                                 StockMovementRepository movements, WarehouseLocationRepository locations,
                                 AuditService audit) {
        this.counts = counts;
        this.products = products;
        this.warehouses = warehouses;
        this.balances = balances;
        this.movements = movements;
        this.locations = locations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<InventoryCountDtos.View> list(Pageable pageable) {
        Pageable limited = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
            Sort.by(Sort.Direction.DESC, "createdAt"));
        return counts.findAll(limited).map(c -> InventoryCountDtos.View.from(
            counts.findDetailedById(c.getId()).orElseThrow()));
    }

    @Transactional(readOnly = true)
    public InventoryCountDtos.View get(UUID id) {
        return InventoryCountDtos.View.from(requireDetailed(id));
    }

    @Transactional
    public InventoryCountDtos.View create(String rawKey, InventoryCountDtos.CreateRequest request, String actor) {
        String key = normalizeKey(rawKey);
        String hash = requestHash(request);
        Optional<InventoryCount> previous = counts.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().getRequestHash())) {
                throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
            }
            return InventoryCountDtos.View.from(requireDetailed(previous.get().getId()));
        }

        Warehouse warehouse = warehouses.findById(request.warehouseId())
            .orElseThrow(() -> new ResourceNotFoundException("Almacén", request.warehouseId()));
        if (!warehouse.isActive()) throw new BusinessRuleException("No se puede contar un almacén inactivo.");

        Set<UUID> seen = new HashSet<>();
        List<InventoryCountDtos.LineRequest> ordered = new ArrayList<>(request.items());
        ordered.sort(Comparator.comparing(item -> item.productId().toString()));
        for (InventoryCountDtos.LineRequest item : ordered) {
            if (!seen.add(item.productId())) {
                throw new BusinessRuleException("No repita un producto en más de una línea de conteo.");
            }
            if (item.countedQuantity().signum() < 0) {
                throw new BusinessRuleException("La cantidad contada no puede ser negativa.");
            }
            item.countedQuantity().setScale(3, RoundingMode.UNNECESSARY);
        }

        // Stable lock order reduces deadlocks when counts overlap on the same products.
        Map<UUID, StockBalance> snapshots = new HashMap<>();
        for (InventoryCountDtos.LineRequest item : ordered) {
            StockBalance balance = balances.findForUpdate(item.productId(), warehouse.getId()).orElse(null);
            if (balance != null) snapshots.put(item.productId(), balance);
        }

        String number = "CNT-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        InventoryCount count = new InventoryCount(number, warehouse, key, hash, actor);
        for (InventoryCountDtos.LineRequest item : ordered) {
            Product product = products.findById(item.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto", item.productId()));
            if (!product.isActive()) throw new BusinessRuleException("No se puede contar un producto inactivo.");
            if (product.getKind() != ProductKind.STOCKED_GOOD) {
                throw new BusinessRuleException("Solo los productos con control físico admiten conteos de inventario.");
            }
            BigDecimal counted = item.countedQuantity().setScale(3, RoundingMode.UNNECESSARY);
            StockBalance balance = snapshots.get(item.productId());
            BigDecimal expected = balance == null ? BigDecimal.ZERO.setScale(3) : balance.getOnHand();
            count.addLine(new InventoryCountLine(product, expected, counted, product.getCostPrice()));
        }
        InventoryCount saved = counts.saveAndFlush(count);
        audit.record(actor, "INVENTORY_COUNT_CREATED", "INVENTORY_COUNT", saved.getId(),
            Map.of("countNumber", number, "warehouseId", warehouse.getId().toString(), "lineCount", ordered.size()));
        return InventoryCountDtos.View.from(requireDetailed(saved.getId()));
    }

    @Transactional
    public InventoryCountDtos.View post(UUID id, String actor) {
        InventoryCount count = counts.findForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conteo de inventario", id));
        if (count.getStatus() == InventoryCountStatus.POSTED) {
            return InventoryCountDtos.View.from(requireDetailed(id));
        }
        if (count.getStatus() != InventoryCountStatus.OPEN) {
            throw new ConflictException("Solo se puede contabilizar un conteo abierto.");
        }

        List<InventoryCountLine> lines = new ArrayList<>(count.getLines());
        lines.sort(Comparator.comparing(line -> line.getProduct().getId().toString()));
        for (InventoryCountLine line : lines) {
            UUID productId = line.getProduct().getId();
            StockBalance balance = balances.findForUpdate(productId, count.getWarehouse().getId()).orElse(null);
            BigDecimal current = balance == null ? BigDecimal.ZERO.setScale(3) : balance.getOnHand();
            if (current.compareTo(line.getExpectedQuantity()) != 0) {
                throw new ConflictException("El saldo de " + line.getProduct().getSku()
                    + " cambió desde la captura del conteo; vuelva a contar ese producto.");
            }

            BigDecimal difference = line.getDifference();
            if (difference.signum() == 0) continue;
            if (balance == null) {
                WarehouseLocation main = locations.findByWarehouseIdAndCode(count.getWarehouse().getId(), "MAIN")
                    .orElseGet(() -> locations.save(new WarehouseLocation(count.getWarehouse(), "MAIN",
                        "Ubicación principal", WarehouseLocation.LocationType.STORAGE)));
                balance = balances.save(new StockBalance(line.getProduct(), count.getWarehouse(), main));
            }

            MovementType type;
            BigDecimal quantity = difference.abs().setScale(3);
            if (difference.signum() > 0) {
                balance.increase(quantity);
                type = MovementType.ADJUSTMENT_IN;
            } else {
                // decrease() also protects stock reserved for active orders.
                balance.decrease(quantity);
                type = MovementType.ADJUSTMENT_OUT;
            }
            String movementKey = "CNT-" + count.getId() + "-" + line.getId();
            StockMovement movement = new StockMovement(line.getProduct(), count.getWarehouse(), type, quantity,
                line.getUnitCost(), "INVENTORY_COUNT", count.getId(), movementKey,
                hash(count.getId() + "|" + line.getId() + "|" + difference.toPlainString()),
                "Ajuste por conteo físico " + count.getCountNumber(), actor);
            movement.assignLocations(type == MovementType.ADJUSTMENT_OUT ? balance.getLocation() : null,
                type == MovementType.ADJUSTMENT_IN ? balance.getLocation() : null, null,
                count.getCountNumber(), line.getId(), null);
            movements.save(movement);
        }

        count.post();
        audit.record(actor, "INVENTORY_COUNT_POSTED", "INVENTORY_COUNT", id,
            Map.of("countNumber", count.getCountNumber(), "lineCount", lines.size()));
        return InventoryCountDtos.View.from(requireDetailed(id));
    }

    @Transactional
    public InventoryCountDtos.View cancel(UUID id, String actor) {
        InventoryCount count = counts.findForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conteo de inventario", id));
        if (count.getStatus() == InventoryCountStatus.CANCELLED) {
            return InventoryCountDtos.View.from(requireDetailed(id));
        }
        count.cancel();
        audit.record(actor, "INVENTORY_COUNT_CANCELLED", "INVENTORY_COUNT", id,
            Map.of("countNumber", count.getCountNumber()));
        return InventoryCountDtos.View.from(requireDetailed(id));
    }

    private InventoryCount requireDetailed(UUID id) {
        return counts.findDetailedById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conteo de inventario", id));
    }

    private String normalizeKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || rawKey.trim().length() > 120) {
            throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");
        }
        return rawKey.trim();
    }

    private String requestHash(InventoryCountDtos.CreateRequest request) {
        List<String> lines = request.items().stream()
            .map(item -> item.productId() + ":" + item.countedQuantity().setScale(3, RoundingMode.UNNECESSARY).toPlainString())
            .sorted().toList();
        return hash(request.warehouseId() + "|" + String.join("|", lines));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular la huella de la solicitud.", ex);
        }
    }
}
