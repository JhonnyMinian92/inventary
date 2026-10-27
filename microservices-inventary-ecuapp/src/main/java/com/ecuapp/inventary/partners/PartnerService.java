package com.ecuapp.inventary.partners;

import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.Pages;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PartnerService {
 private static final Set<String> SORT=Set.of("name","code","createdAt","updatedAt","active");
 private final BusinessPartnerRepository repo;private final AuditService audit;
 public PartnerService(BusinessPartnerRepository repo,AuditService audit){this.repo=repo;this.audit=audit;}
 @Transactional(readOnly=true) public Page<PartnerDtos.View> list(PartnerType type,String q,Boolean active,Pageable p){
  String pattern=q==null||q.isBlank()?null:"%"+q.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
  return repo.search(List.of(type,PartnerType.BOTH),pattern,active,Pages.of(p,SORT,Sort.by("name"))).map(PartnerDtos.View::from);
 }
 @Transactional(readOnly=true) public PartnerDtos.View get(PartnerType type,UUID id){return PartnerDtos.View.from(require(type,id));}
 @Transactional public PartnerDtos.View create(PartnerType type,PartnerDtos.WriteRequest r,String actor){
  String code=r.code().trim().toUpperCase(Locale.ROOT);String tax=clean(r.taxId());
  if(repo.existsByCodeIgnoreCase(code))throw new ConflictException("Ya existe un contacto comercial con ese código.");
  if(tax!=null&&repo.existsByTaxId(tax))throw new ConflictException("Ya existe un contacto comercial con ese identificador fiscal.");
  BusinessPartner saved=repo.saveAndFlush(new BusinessPartner(code,type,r.name().trim(),tax,lower(r.email()),clean(r.phone()),clean(r.address())));
  audit.record(actor,type.name()+"_CREATED","BUSINESS_PARTNER",saved.getId(),java.util.Map.of("code",code,"partnerType",type.name()));
  return PartnerDtos.View.from(saved);
 }
 @Transactional public PartnerDtos.View update(PartnerType type,UUID id,PartnerDtos.WriteRequest r,String actor){
  BusinessPartner partner=require(type,id);String code=r.code().trim().toUpperCase(Locale.ROOT);
  if(!partner.getCode().equals(code))throw new ConflictException("El código del contacto es inmutable.");
  String tax=clean(r.taxId());
  if(tax!=null&&!tax.equals(partner.getTaxId())&&repo.existsByTaxId(tax))throw new ConflictException("Ya existe un contacto comercial con ese identificador fiscal.");
  partner.update(r.name().trim(),tax,lower(r.email()),clean(r.phone()),clean(r.address()));repo.flush();
  audit.record(actor,type.name()+"_UPDATED","BUSINESS_PARTNER",id,java.util.Map.of("code",partner.getCode()));return PartnerDtos.View.from(partner);
 }
 @Transactional public void delete(PartnerType type,UUID id,String actor){
  BusinessPartner partner=require(type,id);
  if(partner.isActive())throw new ConflictException("Desactive el contacto antes de intentar su eliminación física.");
  String code=partner.getCode();PartnerType actualType=partner.getType();
  try{
   repo.deleteAndFlush(partner);
  }catch(DataIntegrityViolationException ex){
   throw new ConflictException("El contacto conserva referencias comerciales o financieras y no puede eliminarse físicamente.");
  }
  audit.record(actor,actualType.name()+"_DELETED","BUSINESS_PARTNER",id,java.util.Map.of("code",code,"partnerType",actualType.name()));
 }
 @Transactional public void deactivate(PartnerType type,UUID id,String actor){BusinessPartner partner=require(type,id);if(!partner.isActive())return;partner.deactivate();audit.record(actor,type.name()+"_DEACTIVATED","BUSINESS_PARTNER",id,java.util.Map.of("code",partner.getCode()));}
 @Transactional public void activate(PartnerType type,UUID id,String actor){BusinessPartner partner=require(type,id);if(partner.isActive())return;partner.activate();audit.record(actor,type.name()+"_ACTIVATED","BUSINESS_PARTNER",id,java.util.Map.of("code",partner.getCode()));}
 private BusinessPartner require(PartnerType type,UUID id){
  return repo.findByIdAndTypeIn(id,List.of(type,PartnerType.BOTH)).orElseThrow(()->new ResourceNotFoundException(type==PartnerType.SUPPLIER?"Proveedor":"Cliente",id));
 }
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
 private String lower(String v){String c=clean(v);return c==null?null:c.toLowerCase(Locale.ROOT);}
}
