package com.ecuapp.inventary.taxpayer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SriEstablishmentRepository extends JpaRepository<SriEstablishment,UUID> {
    List<SriEstablishment> findByTaxpayerIdOrderByCodeAsc(UUID taxpayerId);
    Optional<SriEstablishment> findByIdAndTaxpayerId(UUID id,UUID taxpayerId);
    Optional<SriEstablishment> findByTaxpayerIdAndCode(UUID taxpayerId,String code);
}
