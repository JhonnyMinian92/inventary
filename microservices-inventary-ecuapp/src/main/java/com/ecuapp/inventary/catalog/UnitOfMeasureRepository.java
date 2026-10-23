package com.ecuapp.inventary.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure,UUID> {
 Optional<UnitOfMeasure> findByCodeIgnoreCase(String code);
 boolean existsByCodeIgnoreCase(String code);
}
