package com.ecuapp.inventary.returns;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface SalesReturnRepository extends JpaRepository<SalesReturn,UUID>{
 Optional<SalesReturn> findByIdempotencyKey(String key);
 @EntityGraph(attributePaths={"lines","lines.product","lines.sourceSaleItem","sourceSale","warehouse"})
 @Query("select distinct r from SalesReturn r where r.id=:id")
 Optional<SalesReturn> findDetailedById(@Param("id") UUID id);
}
