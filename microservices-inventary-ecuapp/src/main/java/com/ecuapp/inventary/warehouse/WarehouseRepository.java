package com.ecuapp.inventary.warehouse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface WarehouseRepository extends JpaRepository<Warehouse,UUID>{
 boolean existsByCode(String code);
 boolean existsByCodeIgnoreCase(String code);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select w from Warehouse w where w.id=:id")
 Optional<Warehouse> findForUpdate(@Param("id") UUID id);
 @Query("select w from Warehouse w where (:active is null or w.active=:active)")
 Page<Warehouse> search(@Param("active") Boolean active,Pageable pageable);
}
