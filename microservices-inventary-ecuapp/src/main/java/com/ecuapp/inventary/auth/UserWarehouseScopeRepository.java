package com.ecuapp.inventary.auth;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserWarehouseScopeRepository extends JpaRepository<UserWarehouseScope,UUID> {
    @Query("""
        select distinct s.warehouse.id from UserWarehouseScope s
        where s.user.username=:username and s.user.active=true and s.active=true and s.validFrom<=:now
          and (s.validUntil is null or s.validUntil>:now)
        """)
    Set<UUID> findEffectiveWarehouseIds(@Param("username") String username,@Param("now") Instant now);
    @Query("""
        select case when count(s)>0 then true else false end from UserWarehouseScope s
        where s.user.username=:username and s.user.active=true and s.warehouse.id=:warehouseId
          and s.active=true and s.validFrom<=:now and (s.validUntil is null or s.validUntil>:now)
        """)
    boolean existsEffective(@Param("username") String username,@Param("warehouseId") UUID warehouseId,@Param("now") Instant now);
    boolean existsByUserIdAndWarehouseIdAndActiveTrue(UUID userId,UUID warehouseId);
    @EntityGraph(attributePaths={"warehouse"}) List<UserWarehouseScope> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<UserWarehouseScope> findByIdAndUserId(UUID id,UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserWarehouseScope s where s.id=:id and s.user.id=:userId")
    Optional<UserWarehouseScope> findForUpdateByIdAndUserId(@Param("id") UUID id,@Param("userId") UUID userId);
    @EntityGraph(attributePaths={"warehouse"})
    @Query("""
        select s from UserWarehouseScope s where s.user.id=:userId and s.warehouse.id=:warehouseId
          and s.active=true and s.validUntil is not null and s.validUntil<=:now
        """)
    List<UserWarehouseScope> findExpiredActive(@Param("userId") UUID userId,@Param("warehouseId") UUID warehouseId,@Param("now") Instant now);

    @Query("select case when count(s)>0 then true else false end from UserWarehouseScope s where s.warehouse.id=:warehouseId")
    boolean existsAnyForWarehouse(@Param("warehouseId") UUID warehouseId);
}