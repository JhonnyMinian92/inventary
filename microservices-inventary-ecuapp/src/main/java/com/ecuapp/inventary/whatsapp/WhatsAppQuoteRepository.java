package com.ecuapp.inventary.whatsapp;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppQuoteRepository extends JpaRepository<WhatsAppQuote,UUID> {
 Optional<WhatsAppQuote> findByIdempotencyKey(String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @EntityGraph(attributePaths={"items","items.product","cart","warehouse"})
 @Query("select q from WhatsAppQuote q where q.id=:id")
 Optional<WhatsAppQuote> findForUpdate(@Param("id") UUID id);
 @EntityGraph(attributePaths={"items","items.product","cart","warehouse"})
 @Query("select distinct q from WhatsAppQuote q where q.id=:id")
 Optional<WhatsAppQuote> findDetailedById(@Param("id") UUID id);
 List<WhatsAppQuote> findByStatusAndExpiresAtBefore(QuoteStatus status,Instant instant);
 @Modifying(flushAutomatically=true,clearAutomatically=true)
 @Query("update WhatsAppQuote q set q.status=com.ecuapp.inventary.whatsapp.QuoteStatus.EXPIRED where q.cart.id=:cartId and q.status=com.ecuapp.inventary.whatsapp.QuoteStatus.ACTIVE")
 int expireActiveForCart(@Param("cartId") UUID cartId);
}
