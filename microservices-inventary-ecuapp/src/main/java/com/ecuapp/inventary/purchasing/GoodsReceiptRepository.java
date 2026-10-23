package com.ecuapp.inventary.purchasing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt,UUID>{
 Optional<GoodsReceipt> findByIdempotencyKey(String key);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"lines","lines.product","purchaseOrder","warehouse"})
 @org.springframework.data.jpa.repository.Query("select distinct r from GoodsReceipt r where r.id=:id")
 Optional<GoodsReceipt> findDetailedById(@org.springframework.data.repository.query.Param("id") UUID id);
}
