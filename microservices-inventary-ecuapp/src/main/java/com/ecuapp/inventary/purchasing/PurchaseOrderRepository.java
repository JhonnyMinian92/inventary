package com.ecuapp.inventary.purchasing;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from PurchaseOrder p where p.id=:id")
 Optional<PurchaseOrder> findForUpdate(@Param("id") UUID id);
 @EntityGraph(attributePaths={"lines","lines.product","supplier","warehouse"})
 @Query("select distinct p from PurchaseOrder p where p.id=:id")
 Optional<PurchaseOrder> findDetailedById(@Param("id") UUID id);
 @Override @EntityGraph(attributePaths={"supplier","warehouse"})
 Page<PurchaseOrder> findAll(Pageable pageable);
}
