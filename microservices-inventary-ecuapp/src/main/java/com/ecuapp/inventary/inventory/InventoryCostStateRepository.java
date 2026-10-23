package com.ecuapp.inventary.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCostStateRepository extends JpaRepository<InventoryCostState,UUID> {
 @Modifying(flushAutomatically=true)
 @Query(value="INSERT INTO inventory_cost_state (id,product_id,location_id,quantity,total_value,currency,version,updated_at) "+
  "VALUES (:id,:productId,:locationId,0,0,:currency,0,CURRENT_TIMESTAMP) ON CONFLICT (product_id,location_id) DO NOTHING",nativeQuery=true)
 int insertIfAbsent(@Param("id") UUID id,@Param("productId") UUID productId,@Param("locationId") UUID locationId,@Param("currency") String currency);

 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select s from InventoryCostState s where s.product.id=:productId and s.location.id=:locationId")
 Optional<InventoryCostState> findForUpdate(@Param("productId") UUID productId,@Param("locationId") UUID locationId);

 @Query("select s from InventoryCostState s where s.product.id=:productId and s.location.id=:locationId")
 Optional<InventoryCostState> find(@Param("productId") UUID productId,@Param("locationId") UUID locationId);

 @EntityGraph(attributePaths={"product","location","location.warehouse"})
 @Query("""
  select s from InventoryCostState s
  where (:warehouseId is null or s.location.warehouse.id=:warehouseId)
  and (:productId is null or s.product.id=:productId)
  and (:includeEmpty=true or s.quantity>0)
  """)
 Page<InventoryCostState> search(@Param("warehouseId") UUID warehouseId,@Param("productId") UUID productId,@Param("includeEmpty") boolean includeEmpty,Pageable pageable);
}
