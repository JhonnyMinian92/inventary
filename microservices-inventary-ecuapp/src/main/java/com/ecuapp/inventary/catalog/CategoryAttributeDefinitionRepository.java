package com.ecuapp.inventary.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface CategoryAttributeDefinitionRepository extends JpaRepository<CategoryAttributeDefinition,UUID> {
 List<CategoryAttributeDefinition> findByCategoryIdAndActiveTrueOrderByAttributeKeyAsc(UUID categoryId);
 @Query("select coalesce(max(d.schemaVersion),0) from CategoryAttributeDefinition d where d.category.id=:categoryId")
 int maxSchemaVersion(@Param("categoryId") UUID categoryId);
 @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
 @Query("delete from CategoryAttributeDefinition d where d.category.id=:categoryId")
 int deleteByCategoryId(@Param("categoryId") UUID categoryId);
}
