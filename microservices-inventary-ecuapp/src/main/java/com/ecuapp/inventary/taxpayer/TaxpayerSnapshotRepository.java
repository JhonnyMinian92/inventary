package com.ecuapp.inventary.taxpayer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TaxpayerSnapshotRepository extends JpaRepository<TaxpayerSnapshot,UUID> {
    Page<TaxpayerSnapshot> findByTaxpayerIdOrderByObservedAtDesc(UUID taxpayerId,Pageable pageable);
}
