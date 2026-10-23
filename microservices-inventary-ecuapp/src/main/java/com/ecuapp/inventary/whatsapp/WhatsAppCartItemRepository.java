package com.ecuapp.inventary.whatsapp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface WhatsAppCartItemRepository extends JpaRepository<WhatsAppCartItem,UUID>{
 Optional<WhatsAppCartItem> findByCartIdAndProductId(UUID cartId,UUID productId);
 Optional<WhatsAppCartItem> findByIdAndCartId(UUID id,UUID cartId);
}
