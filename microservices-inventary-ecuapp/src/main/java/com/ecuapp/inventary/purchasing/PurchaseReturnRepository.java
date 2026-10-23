package com.ecuapp.inventary.purchasing;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn,UUID>{
 Optional<PurchaseReturn> findByIdempotencyKey(String key);
 @EntityGraph(attributePaths={"lines","lines.product","lines.sourceReceiptLine","purchaseOrder","goodsReceipt","warehouse"})
 @Query("select distinct r from PurchaseReturn r where r.id=:id")
 Optional<PurchaseReturn> findDetailedById(@Param("id") UUID id);
}
