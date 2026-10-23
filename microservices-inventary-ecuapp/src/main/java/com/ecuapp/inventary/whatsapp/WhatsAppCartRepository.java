package com.ecuapp.inventary.whatsapp;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppCartRepository extends JpaRepository<WhatsAppCart,UUID> {
 Optional<WhatsAppCart> findFirstByCustomerRefAndStatusInOrderByCreatedAtDesc(String customerRef,Collection<CartStatus> statuses);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select distinct c from WhatsAppCart c left join fetch c.items i left join fetch i.product where c.id=:id")
 Optional<WhatsAppCart> findForUpdate(@Param("id") UUID id);
 @EntityGraph(attributePaths={"items","items.product"})
 @Query("select distinct c from WhatsAppCart c where c.id=:id")
 Optional<WhatsAppCart> findDetailedById(@Param("id") UUID id);
}
