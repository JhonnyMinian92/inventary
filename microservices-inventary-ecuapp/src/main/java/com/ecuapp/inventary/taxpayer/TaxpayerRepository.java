package com.ecuapp.inventary.taxpayer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface TaxpayerRepository extends JpaRepository<TaxpayerProfile,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaxpayerProfile t where t.taxId=:taxId")
    Optional<TaxpayerProfile> findForUpdateByTaxId(@Param("taxId") String taxId);
    @Query("select t from TaxpayerProfile t where (:pattern is null or lower(t.taxId) like :pattern escape '\\\\' or lower(t.legalName) like :pattern escape '\\\\')")
    Page<TaxpayerProfile> search(@Param("pattern") String pattern, Pageable pageable);
}
