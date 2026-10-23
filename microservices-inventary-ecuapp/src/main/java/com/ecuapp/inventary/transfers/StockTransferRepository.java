package com.ecuapp.inventary.transfers;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface StockTransferRepository extends JpaRepository<StockTransfer,UUID>{
 Optional<StockTransfer> findByIdempotencyKey(String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select distinct t from StockTransfer t left join fetch t.lines where t.id=:id")
 Optional<StockTransfer> findForUpdate(@Param("id") UUID id);
 @EntityGraph(attributePaths={"sourceWarehouse","destinationWarehouse","lines","lines.product"})
 @Query("select distinct t from StockTransfer t where t.id=:id")
 Optional<StockTransfer> findDetailedById(@Param("id") UUID id);
 @Override @EntityGraph(attributePaths={"sourceWarehouse","destinationWarehouse"})
 Page<StockTransfer> findAll(Pageable pageable);
}
