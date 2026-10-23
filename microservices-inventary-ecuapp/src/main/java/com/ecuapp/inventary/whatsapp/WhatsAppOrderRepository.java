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
 @EntityGraph(attributePaths={"items","items.product","cart","quote","warehouse"})
 @Query("select distinct o from WhatsAppOrder o where o.id=:id")
 Optional<WhatsAppOrder> findDetailedById(@Param("id") UUID id);
 List<WhatsAppOrder> findByStatusAndExpiresAtBefore(WhatsAppOrderStatus status,Instant instant);
}
