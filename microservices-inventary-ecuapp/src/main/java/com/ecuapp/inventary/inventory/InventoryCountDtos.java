package com.ecuapp.inventary.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryCountDtos {
    private InventoryCountDtos() {}

    public record LineRequest(@NotNull UUID productId,
                              @NotNull @DecimalMin(value = "0.000", inclusive = true) @Digits(integer = 11, fraction = 3)
                              BigDecimal countedQuantity) {}

    public record CreateRequest(@NotNull UUID warehouseId,
                                @NotEmpty @Size(max = 500) List<@Valid LineRequest> items) {}

    public record LineView(UUID id, UUID productId, String sku, String productName,
                           BigDecimal expectedQuantity, BigDecimal countedQuantity, BigDecimal difference) {
        static LineView from(InventoryCountLine line) {
            return new LineView(line.getId(), line.getProduct().getId(), line.getProduct().getSku(),
                line.getProduct().getName(), line.getExpectedQuantity(), line.getCountedQuantity(), line.getDifference());
        }
    }

    public record View(UUID id, String countNumber, UUID warehouseId, String warehouseCode,
                       InventoryCountStatus status, String createdBy, String postedBy, Instant createdAt, Instant postedAt,
                       List<LineView> items) {
        static View from(InventoryCount count) {
            return new View(count.getId(), count.getCountNumber(), count.getWarehouse().getId(),
                count.getWarehouse().getCode(), count.getStatus(), count.getCreatedBy(), count.getPostedBy(), count.getCreatedAt(),
                count.getPostedAt(), count.getLines().stream().map(LineView::from).toList());
        }
    }
}
