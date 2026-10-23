package com.ecuapp.inventary.inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface StockMovementRepository extends JpaRepository<StockMovement,UUID>{
 Optional<StockMovement> findByIdempotencyKey(String idempotencyKey);
}
