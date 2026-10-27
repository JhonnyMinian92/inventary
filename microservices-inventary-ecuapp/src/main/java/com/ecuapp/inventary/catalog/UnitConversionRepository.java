package com.ecuapp.inventary.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UnitConversionRepository extends JpaRepository<UnitConversion,UUID> {
 Optional<UnitConversion> findByFromUnitIdAndToUnitId(UUID fromUnitId,UUID toUnitId);
 @org.springframework.data.jpa.repository.Query("select case when count(c)>0 then true else false end from UnitConversion c where c.fromUnit.id=:id or c.toUnit.id=:id")
 boolean existsForUnit(@org.springframework.data.repository.query.Param("id") UUID unitId);
}
