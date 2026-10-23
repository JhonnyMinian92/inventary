package com.ecuapp.inventary.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UnitConversionRepository extends JpaRepository<UnitConversion,UUID> {
 Optional<UnitConversion> findByFromUnitIdAndToUnitId(UUID fromUnitId,UUID toUnitId);
}
