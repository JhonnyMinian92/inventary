package com.ecuapp.inventary.auth;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserTaxpayerScopeRepository extends JpaRepository<UserTaxpayerScope,UUID> {
    @Query("""
        select distinct s.taxpayer.id from UserTaxpayerScope s
        where s.user.username=:username and s.user.active=true and s.active=true and s.validFrom<=:now
          and (s.validUntil is null or s.validUntil>:now)
        """)
    Set<UUID> findEffectiveTaxpayerIds(@Param("username") String username,@Param("now") Instant now);

    @Query("""
        select case when count(s)>0 then true else false end from UserTaxpayerScope s
        where s.user.username=:username and s.user.active=true and s.taxpayer.id=:taxpayerId
          and s.active=true and s.validFrom<=:now and (s.validUntil is null or s.validUntil>:now)
        """)
    boolean existsEffective(@Param("username") String username,@Param("taxpayerId") UUID taxpayerId,@Param("now") Instant now);

    boolean existsByUserIdAndTaxpayerIdAndActiveTrue(UUID userId,UUID taxpayerId);

    @EntityGraph(attributePaths={"taxpayer"})
    List<UserTaxpayerScope> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserTaxpayerScope s where s.id=:id and s.user.id=:userId")
    Optional<UserTaxpayerScope> findForUpdateByIdAndUserId(@Param("id") UUID id,@Param("userId") UUID userId);

    @EntityGraph(attributePaths={"taxpayer"})
    @Query("""
        select s from UserTaxpayerScope s where s.user.id=:userId and s.taxpayer.id=:taxpayerId
          and s.active=true and s.validUntil is not null and s.validUntil<=:now
        """)
    List<UserTaxpayerScope> findExpiredActive(@Param("userId") UUID userId,@Param("taxpayerId") UUID taxpayerId,@Param("now") Instant now);
}
