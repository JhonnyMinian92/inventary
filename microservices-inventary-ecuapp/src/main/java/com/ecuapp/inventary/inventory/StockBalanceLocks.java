package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.warehouse.Warehouse;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import com.ecuapp.inventary.warehouse.WarehouseLocationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Acquires stock balance row locks. Missing balances are created with INSERT ... ON CONFLICT DO NOTHING so two
 * concurrent first receipts of the same product converge on one row instead of failing on the unique key.
 */
@Component
public class StockBalanceLocks {
    private final StockBalanceRepository balances;
    private final WarehouseLocationRepository locations;

    public StockBalanceLocks(StockBalanceRepository balances, WarehouseLocationRepository locations) {
        this.balances = balances;
        this.locations = locations;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<StockBalance> lock(Product product, Warehouse warehouse) {
        return balances.findForUpdate(product.getId(), warehouse.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockBalance lockOrCreate(Product product, Warehouse warehouse) {
        Optional<StockBalance> existing = balances.findForUpdate(product.getId(), warehouse.getId());
        if (existing.isPresent()) return existing.get();
        WarehouseLocation main = mainLocation(warehouse);
        balances.insertIfAbsent(UUID.randomUUID(), product.getId(), warehouse.getId(), main.getId());
        return balances.findForUpdate(product.getId(), warehouse.getId())
            .orElseThrow(() -> new ConflictException("No se pudo crear el saldo de inventario de forma concurrente."));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WarehouseLocation mainLocation(Warehouse warehouse) {
        locations.insertIfAbsent(UUID.randomUUID(), warehouse.getId(), "MAIN", "Ubicación principal", "STORAGE");
        return locations.findByWarehouseIdAndCode(warehouse.getId(), "MAIN")
            .orElseThrow(() -> new ConflictException("No se pudo asegurar la ubicación principal del almacén."));
    }
}
