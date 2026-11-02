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
public interface WarehouseRepository extends com.ecuapp.inventary.common.FlushableJpaRepository<Warehouse,UUID>{
 boolean existsByCode(String code);
 boolean existsByCodeIgnoreCase(String code);
 java.util.List<Warehouse> findByActiveTrue();
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select w from Warehouse w where w.id=:id")
 Optional<Warehouse> findForUpdate(@Param("id") UUID id);
 @Query("""
     select w from Warehouse w
     where (:active is null or w.active=:active)
       and (:q is null or lower(w.code) like lower(concat('%', cast(:q as string), '%'))
                    or lower(w.name) like lower(concat('%', cast(:q as string), '%'))
                    or lower(coalesce(w.address,'')) like lower(concat('%', cast(:q as string), '%')))
     """)
 Page<Warehouse> search(@Param("q") String q,@Param("active") Boolean active,Pageable pageable);
    @Query("""
        select w from Warehouse w
        where (:active is null or w.active=:active)
          and w.id in :warehouseIds
          and (:q is null or lower(w.code) like lower(concat('%', cast(:q as string), '%'))
                         or lower(w.name) like lower(concat('%', cast(:q as string), '%'))
                         or lower(coalesce(w.address,'')) like lower(concat('%', cast(:q as string), '%')))
        """)
    Page<Warehouse> searchScoped(@Param("q") String q,@Param("active") Boolean active,@Param("warehouseIds") java.util.Collection<UUID> warehouseIds,Pageable pageable);
}
