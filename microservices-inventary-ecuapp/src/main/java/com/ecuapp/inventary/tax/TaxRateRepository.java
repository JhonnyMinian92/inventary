package com.ecuapp.inventary.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxRateRepository extends JpaRepository<TaxRate,UUID> {
 @Query("""
 select r from TaxRate r where r.active=true and r.code=:code
 and r.validFrom<=:date and (r.validUntil is null or r.validUntil>=:date)
 order by r.validFrom desc
 """)
 Optional<TaxRate> findEffective(@Param("code") String code,@Param("date") LocalDate date);

 @Query("""
 select r from TaxRate r where r.active=true
 and r.validFrom<=:date and (r.validUntil is null or r.validUntil>=:date)
 order by r.code,r.validFrom
 """)
 List<TaxRate> findEffectiveAll(@Param("date") LocalDate date);
}
