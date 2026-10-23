package com.ecuapp.inventary.warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
public interface WarehouseRepository extends JpaRepository<Warehouse,UUID>{
 boolean existsByCode(String code);
 boolean existsByCodeIgnoreCase(String code);
 @Query("select w from Warehouse w where (:active is null or w.active=:active)")
 Page<Warehouse> search(@Param("active") Boolean active,Pageable pageable);
}
