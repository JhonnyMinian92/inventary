package com.ecuapp.inventary.catalog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CategoryRepository extends JpaRepository<Category,UUID> {
 boolean existsByNameIgnoreCase(String name);
 boolean existsBySlug(String slug);
}
