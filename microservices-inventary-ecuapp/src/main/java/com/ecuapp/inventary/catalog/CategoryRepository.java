package com.ecuapp.inventary.catalog;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface CategoryRepository extends JpaRepository<Category,UUID> {
 boolean existsByNameIgnoreCase(String name);
 boolean existsBySlug(String slug);
 Page<Category> findByActiveTrue(Pageable pageable);
 @Query("select c from Category c where (:active is null or c.active=:active)")
 Page<Category> search(@Param("active") Boolean active,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from Category c where c.id=:id")
 Optional<Category> findForUpdate(@Param("id") UUID id);
}
