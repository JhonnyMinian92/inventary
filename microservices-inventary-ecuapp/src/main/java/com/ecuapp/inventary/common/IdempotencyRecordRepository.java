package com.ecuapp.inventary.common;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord,UUID> {
 Optional<IdempotencyRecord> findByScopeAndIdempotencyKey(String scope,String idempotencyKey);
}
