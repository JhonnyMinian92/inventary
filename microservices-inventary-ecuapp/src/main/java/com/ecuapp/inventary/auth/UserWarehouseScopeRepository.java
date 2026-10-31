package com.ecuapp.inventary.auth;

import org.springframework.data.jpa.repository.*;
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
    @Modifying
    @Query("""
        update UserWarehouseScope s set s.active=false,s.revokedAt=:now,s.revokedBy='SYSTEM_EXPIRY'
        where s.user.id=:userId and s.warehouse.id=:warehouseId and s.active=true
          and s.validUntil is not null and s.validUntil<=:now
        """)
    int expirePrevious(@Param("userId") UUID userId,@Param("warehouseId") UUID warehouseId,@Param("now") Instant now);
}