package com.ecuapp.inventary.media;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ProductImageRepository extends JpaRepository<ProductImage,UUID>{
 List<ProductImage> findByProductIdAndPublishedTrueOrderBySortOrderAsc(UUID productId);
 Optional<ProductImage> findByIdAndProductId(UUID imageId,UUID productId);
 @Modifying(flushAutomatically=true,clearAutomatically=true)
 @Query("update ProductImage i set i.primaryImage=false where i.product.id=:productId and i.published=true")
 int clearPrimary(@Param("productId") UUID productId);
 @Modifying(flushAutomatically=true)
 @Query("update ProductImage i set i.primaryImage=false where i.product.id=:productId and i.id<>:imageId and i.published=true")
 int clearPrimaryExcept(@Param("productId") UUID productId,@Param("imageId") UUID imageId);
}
