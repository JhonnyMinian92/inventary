package com.ecuapp.inventary.common;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord,UUID> {
 Optional<IdempotencyRecord> findByScopeAndIdempotencyKey(String scope,String idempotencyKey);

 @Modifying(flushAutomatically=true)
 @Query(value="INSERT INTO idempotency_record (id,scope,idempotency_key,request_hash,status,created_at) "+
  "VALUES (:id,:scope,:key,:hash,'IN_PROGRESS',CURRENT_TIMESTAMP) "+
  "ON CONFLICT (scope,idempotency_key) DO NOTHING",nativeQuery=true)
 int insertIfAbsent(@Param("id") UUID id,@Param("scope") String scope,@Param("key") String key,@Param("hash") String hash);

 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select r from IdempotencyRecord r where r.scope=:scope and r.idempotencyKey=:key")
 Optional<IdempotencyRecord> findForUpdate(@Param("scope") String scope,@Param("key") String key);
}
