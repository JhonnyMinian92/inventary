package com.ecuapp.inventary.partners;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
public interface BusinessPartnerRepository extends JpaRepository<BusinessPartner,UUID>{
 boolean existsByCode(String code);
 boolean existsByCodeIgnoreCase(String code);
 boolean existsByTaxId(String taxId);
 Optional<BusinessPartner> findByIdAndTypeIn(UUID id,Collection<PartnerType> types);
 Page<BusinessPartner> findByTypeIn(Collection<PartnerType> types,Pageable pageable);
 @Query("""
  select p from BusinessPartner p
  where p.type in :types
  and (:pattern is null or lower(p.name) like :pattern escape '\\' or lower(p.code) like :pattern escape '\\' or lower(p.taxId) like :pattern escape '\\')
  and (:active is null or p.active=:active)
  """)
 Page<BusinessPartner> search(@Param("types") Collection<PartnerType> types,@Param("pattern") String pattern,@Param("active") Boolean active,Pageable pageable);
}
