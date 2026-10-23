package com.ecuapp.inventary.returns;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.UUID;
public interface SalesReturnLineRepository extends JpaRepository<SalesReturnLine,UUID>{
 @Query("select coalesce(sum(l.quantity), 0) from SalesReturnLine l where l.sourceSaleItem.id=:itemId and l.salesReturn.status=:status")
 BigDecimal sumReturnedQuantity(@Param("itemId") UUID itemId,@Param("status") ReturnStatus status);
 @Query("select case when count(l)>0 then true else false end from SalesReturnLine l where l.sourceSaleItem.sale.id=:saleId")
 boolean existsForSale(@Param("saleId") UUID saleId);
}
