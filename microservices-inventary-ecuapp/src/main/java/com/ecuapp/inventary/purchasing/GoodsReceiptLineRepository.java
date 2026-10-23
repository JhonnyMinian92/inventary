package com.ecuapp.inventary.purchasing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine,UUID>{
 Optional<GoodsReceiptLine> findByIdAndReceiptId(UUID id,UUID receiptId);
 @Query("select coalesce(sum(l.quantity), 0) from PurchaseReturnLine l where l.sourceReceiptLine.id=:receiptLineId")
 BigDecimal sumReturned(@Param("receiptLineId") UUID receiptLineId);
}
