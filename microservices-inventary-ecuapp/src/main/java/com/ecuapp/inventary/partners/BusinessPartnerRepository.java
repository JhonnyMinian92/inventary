package com.ecuapp.inventary.partners;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
public interface BusinessPartnerRepository extends JpaRepository<BusinessPartner,UUID>{
 boolean existsByCode(String code);
 boolean existsByTaxId(String taxId);
 Optional<BusinessPartner> findByIdAndTypeIn(UUID id,Collection<PartnerType> types);
 Page<BusinessPartner> findByTypeIn(Collection<PartnerType> types,Pageable pageable);
}
