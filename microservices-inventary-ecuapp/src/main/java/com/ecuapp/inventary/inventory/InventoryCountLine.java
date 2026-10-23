package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inventory_count_line",
       uniqueConstraints = @UniqueConstraint(name = "uk_inventory_count_line_product", columnNames = {"inventory_count_id", "product_id"}))
public class InventoryCountLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_count_id", nullable = false, updatable = false)
    private InventoryCount inventoryCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "expected_quantity", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal expectedQuantity;

    @Column(name = "counted_quantity", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal countedQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal unitCost;

    protected InventoryCountLine() {}

    public InventoryCountLine(InventoryCount inventoryCount, Product product, BigDecimal expectedQuantity,
                              BigDecimal countedQuantity, BigDecimal unitCost) {
        this.inventoryCount = inventoryCount;
        this.product = product;
        this.expectedQuantity = expectedQuantity;
        this.countedQuantity = countedQuantity;
        this.unitCost = unitCost;
    }

    public UUID getId() { return id; }
    public InventoryCount getInventoryCount() { return inventoryCount; }
    public Product getProduct() { return product; }
    public BigDecimal getExpectedQuantity() { return expectedQuantity; }
    public BigDecimal getCountedQuantity() { return countedQuantity; }
    public BigDecimal getDifference() { return countedQuantity.subtract(expectedQuantity).setScale(3); }
    public BigDecimal getUnitCost() { return unitCost; }
}
