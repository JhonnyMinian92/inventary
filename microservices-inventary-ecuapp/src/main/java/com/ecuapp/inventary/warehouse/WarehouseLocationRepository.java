package com.ecuapp.inventary.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseLocationRepository extends JpaRepository<WarehouseLocation,UUID> {
 @Modifying(flushAutomatically=true)
 @Query(value="INSERT INTO warehouse_location (id,warehouse_id,code,name,location_type,active) VALUES (:id,:warehouseId,:code,:name,:type,true) ON CONFLICT (warehouse_id,code) DO NOTHING",nativeQuery=true)
 int insertIfAbsent(@Param("id") UUID id,@Param("warehouseId") UUID warehouseId,@Param("code") String code,@Param("name") String name,@Param("type") String type);
 Optional<WarehouseLocation> findByWarehouseIdAndCode(UUID warehouseId,String code);
 java.util.List<WarehouseLocation> findByWarehouseIdOrderByCodeAsc(UUID warehouseId);
}
