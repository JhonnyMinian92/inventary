package com.ecuapp.inventary.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation,UUID> {
 Optional<StockReservation> findByIdempotencyKey(String idempotencyKey);
 List<StockReservation> findByStatusAndExpiresAtBefore(StockReservation.ReservationStatus status,java.time.Instant expiresAt);
}
