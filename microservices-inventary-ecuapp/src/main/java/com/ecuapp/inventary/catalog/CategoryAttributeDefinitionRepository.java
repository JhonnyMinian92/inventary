package com.ecuapp.inventary.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CategoryAttributeDefinitionRepository extends JpaRepository<CategoryAttributeDefinition,UUID> {
 List<CategoryAttributeDefinition> findByCategoryIdAndActiveTrueOrderByAttributeKeyAsc(UUID categoryId);
}
