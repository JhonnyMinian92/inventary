package com.ecuapp.inventary.sales;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.catalog.ProductRepository;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import com.ecuapp.inventary.inventory.InventoryCostingService;
import com.ecuapp.inventary.inventory.StockBalance;
import com.ecuapp.inventary.inventory.StockBalanceRepository;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import com.ecuapp.inventary.warehouse.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Sale prices derived from purchase costs.
 * <ul>
 * <li>Minimum price: the cost of the exact units that would leave the warehouse (FIFO layers or moving average),
 * rounded up to the cent. Selling below it is a loss and is rejected.</li>
 * <li>Suggested price for stocked goods: that cost plus the product markup (25% by default), never below the
 * catalog price that the last received purchase set.</li>
 * <li>Non-stocked goods and services use the catalog sale price and do not receive an inventory cost floor.</li>
 * </ul>
 */
@Service
public class SalePricingService {
    public record LinePrice(BigDecimal minimumPrice, BigDecimal suggestedPrice, BigDecimal marginPercent) {}

    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final StockBalanceRepository balances;
    private final InventoryCostingService costing;

    public SalePricingService(ProductRepository products, WarehouseRepository warehouses, StockBalanceRepository balances, InventoryCostingService costing) {
        this.products = products;
        this.warehouses = warehouses;
        this.balances = balances;
        this.costing = costing;
    }

    @Transactional(readOnly = true)
    public List<SaleDtos.PriceView> quote(SaleDtos.PricingRequest request) {
        if (!warehouses.existsById(request.warehouseId())) throw new ResourceNotFoundException("Almacén", request.warehouseId());
        return request.items().stream().map(item -> {
            Product product = products.findById(item.productId()).orElseThrow(() -> new ResourceNotFoundException("Producto", item.productId()));
            WarehouseLocation location = balances.findByProductIdAndWarehouseId(product.getId(), request.warehouseId())
                .map(StockBalance::getLocation).orElse(null);
            LinePrice price = product.getKind() == com.ecuapp.inventary.catalog.ProductKind.STOCKED_GOOD
                ? price(product, location, item.quantity().setScale(3, RoundingMode.HALF_UP))
                : new LinePrice(BigDecimal.ZERO.setScale(2), product.getSalePrice().setScale(2, RoundingMode.HALF_UP), product.getMarginPercent());
            return new SaleDtos.PriceView(product.getId(), item.quantity(), price.minimumPrice(), price.suggestedPrice(), price.marginPercent(), product.getCurrency());
        }).toList();
    }

    /** Must run inside the transaction that holds the balance lock so the estimate matches the final valuation. */
    public LinePrice price(Product product, WarehouseLocation location, BigDecimal quantity) {
        BigDecimal unitCost = costing.estimateOutboundUnitCost(product, location, quantity);
        BigDecimal minimum = minimumFor(unitCost);
        BigDecimal suggested = Product.priceWithMargin(unitCost, product.getMarginPercent()).max(product.getSalePrice()).max(minimum);
        return new LinePrice(minimum, suggested, product.getMarginPercent());
    }

    public static BigDecimal minimumFor(BigDecimal unitCost) {
        return unitCost.setScale(2, RoundingMode.CEILING);
    }

    /** Rejects a price below the real cost of the units sold. */
    public static void requireNoLoss(Product product, BigDecimal unitPrice, BigDecimal unitCost) {
        BigDecimal minimum = minimumFor(unitCost);
        if (unitPrice.compareTo(minimum) < 0)
            throw new BusinessRuleException("Vender " + product.getName() + " a " + unitPrice.toPlainString() + " genera pérdida: el costo es "
                + minimum.toPlainString() + ". Use un precio igual o mayor.");
    }
}
