package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryCostingServiceTest {
    @Mock private InventoryCostingConfigRepository configs;
    @Mock private InventoryCostLayerRepository layers;
    @Mock private InventoryValuationEntryRepository entries;
    @Mock private StockMovementRepository movements;
    @InjectMocks private InventoryCostingService service;

    @Test
    void fifoIssueCostUsesLayersInReceiptOrder() {
        Product product = mock(Product.class);
        WarehouseLocation location = mock(WarehouseLocation.class);
        InventoryCostingConfig policy = mock(InventoryCostingConfig.class);
        InventoryCostLayer first = mock(InventoryCostLayer.class);
        InventoryCostLayer second = mock(InventoryCostLayer.class);
        when(product.getId()).thenReturn(UUID.randomUUID());
        when(location.getId()).thenReturn(UUID.randomUUID());
        when(policy.getMethod()).thenReturn(CostingMethod.FIFO);
        when(configs.findBySingletonKey("DEFAULT")).thenReturn(Optional.of(policy));
        when(first.getRemainingQuantity()).thenReturn(new BigDecimal("2.000"));
        when(first.getUnitCost()).thenReturn(new BigDecimal("4.0000"));
        when(second.getRemainingQuantity()).thenReturn(new BigDecimal("3.000"));
        when(second.getUnitCost()).thenReturn(new BigDecimal("6.0000"));
        when(layers.findByProductIdAndLocationIdAndRemainingQuantityGreaterThanOrderByReceivedAtAscIdAsc(
                eq(product.getId()), eq(location.getId()), eq(new BigDecimal("0.000"))))
                .thenReturn(List.of(first, second));

        BigDecimal cost = service.currentIssueUnitCost(product, location, new BigDecimal("3.000"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("4.67"), cost);
    }

    @Test
    void movingAverageUsesValueOfAllRemainingLayers() {
        Product product = mock(Product.class);
        WarehouseLocation location = mock(WarehouseLocation.class);
        InventoryCostingConfig policy = mock(InventoryCostingConfig.class);
        InventoryCostLayer first = mock(InventoryCostLayer.class);
        InventoryCostLayer second = mock(InventoryCostLayer.class);
        when(product.getId()).thenReturn(UUID.randomUUID());
        when(location.getId()).thenReturn(UUID.randomUUID());
        when(policy.getMethod()).thenReturn(CostingMethod.MOVING_AVERAGE);
        when(configs.findBySingletonKey("DEFAULT")).thenReturn(Optional.of(policy));
        when(first.getRemainingQuantity()).thenReturn(new BigDecimal("2.000"));
        when(first.getUnitCost()).thenReturn(new BigDecimal("4.0000"));
        when(second.getRemainingQuantity()).thenReturn(new BigDecimal("3.000"));
        when(second.getUnitCost()).thenReturn(new BigDecimal("6.0000"));
        when(layers.findByProductIdAndLocationIdAndRemainingQuantityGreaterThanOrderByReceivedAtAscIdAsc(
                eq(product.getId()), eq(location.getId()), eq(new BigDecimal("0.000"))))
                .thenReturn(List.of(first, second));

        BigDecimal cost = service.currentIssueUnitCost(product, location, new BigDecimal("1.000"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("5.20"), cost);
    }

    @Test
    void rejectsIssueWhenCostLayersDoNotCoverPhysicalQuantity() {
        Product product = mock(Product.class);
        WarehouseLocation location = mock(WarehouseLocation.class);
        InventoryCostingConfig policy = mock(InventoryCostingConfig.class);
        when(product.getId()).thenReturn(UUID.randomUUID());
        when(location.getId()).thenReturn(UUID.randomUUID());
        when(product.getSku()).thenReturn("SKU-1");
        when(policy.getMethod()).thenReturn(CostingMethod.FIFO);
        when(configs.findBySingletonKey("DEFAULT")).thenReturn(Optional.of(policy));
        when(layers.findByProductIdAndLocationIdAndRemainingQuantityGreaterThanOrderByReceivedAtAscIdAsc(
                eq(product.getId()), eq(location.getId()), eq(new BigDecimal("0.000"))))
                .thenReturn(List.of());

        assertThrows(RuntimeException.class,
                () -> service.currentIssueUnitCost(product, location, new BigDecimal("1.000"), BigDecimal.ONE));
        verifyNoInteractions(entries);
    }
}
