package com.ecuapp.inventary.whatsapp;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppOrderRepository extends JpaRepository<WhatsAppOrder,UUID> {
 Optional<WhatsAppOrder> findByIdempotencyKey(String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select o from WhatsAppOrder o where o.id=:id")
 Optional<WhatsAppOrder> findForUpdate(@Param("id") UUID id);
 @EntityGraph(attributePaths={"items","items.product","cart","quote","warehouse","sale"})
 @Query("select distinct o from WhatsAppOrder o where o.id=:id")
 Optional<WhatsAppOrder> findDetailedById(@Param("id") UUID id);
 List<WhatsAppOrder> findByStatusAndExpiresAtBefore(WhatsAppOrderStatus status,Instant instant);
 @EntityGraph(attributePaths={"cart","quote","warehouse","sale"})
 @Query("""
  select o from WhatsAppOrder o
  where (:status is null or o.status=:status)
  and (:customerRef is null or o.customerRef=:customerRef)
  and o.createdAt>=:fromTime
  and o.createdAt<:toTime
  """)
 org.springframework.data.domain.Page<WhatsAppOrder> search(@Param("status") WhatsAppOrderStatus status,@Param("customerRef") String customerRef,
  @Param("fromTime") Instant fromTime,@Param("toTime") Instant toTime,org.springframework.data.domain.Pageable pageable);
}
