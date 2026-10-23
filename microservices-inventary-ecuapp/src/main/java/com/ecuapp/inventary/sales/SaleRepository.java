package com.ecuapp.inventary.sales;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface SaleRepository extends JpaRepository<Sale,UUID>{
 Optional<Sale> findByIdempotencyKey(String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select s from Sale s where s.id=:id")
 Optional<Sale> findForUpdateById(@Param("id") UUID id);
 @EntityGraph(attributePaths={"items","items.product","warehouse"})
 @Query("select distinct s from Sale s where s.id=:id")
 Optional<Sale> findDetailedById(@Param("id") UUID id);
 @EntityGraph(attributePaths={"warehouse","customer"})
 @Query("""
  select s from Sale s
  where (:status is null or s.status=:status)
  and (:warehouseId is null or s.warehouse.id=:warehouseId)
  and (:customerId is null or s.customer.id=:customerId)
  and (:channel is null or s.channel=:channel)
  and s.soldAt>=:fromTime
  and s.soldAt<:toTime
  """)
 Page<Sale> search(@Param("status") SaleStatus status,@Param("warehouseId") UUID warehouseId,@Param("customerId") UUID customerId,
  @Param("channel") SaleChannel channel,@Param("fromTime") java.time.Instant fromTime,@Param("toTime") java.time.Instant toTime,Pageable pageable);
}
