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
 @Override @EntityGraph(attributePaths={"items","items.product","warehouse"}) Page<Sale> findAll(Pageable pageable);
}
