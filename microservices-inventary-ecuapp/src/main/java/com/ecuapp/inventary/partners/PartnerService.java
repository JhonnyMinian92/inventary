package com.ecuapp.inventary.partners;

import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PartnerService {
 private final BusinessPartnerRepository repo;
 public PartnerService(BusinessPartnerRepository repo){this.repo=repo;}
 @Transactional(readOnly=true) public Page<PartnerDtos.View> list(PartnerType type,Pageable p){
  return repo.findByTypeIn(List.of(type,PartnerType.BOTH),PageRequest.of(p.getPageNumber(),Math.min(p.getPageSize(),100),p.getSort())).map(PartnerDtos.View::from);
 }
 @Transactional(readOnly=true) public PartnerDtos.View get(PartnerType type,UUID id){return PartnerDtos.View.from(require(type,id));}
 @Transactional public PartnerDtos.View create(PartnerType type,PartnerDtos.WriteRequest r){
  String code=r.code().trim().toUpperCase(Locale.ROOT);String tax=clean(r.taxId());
  if(repo.existsByCode(code))throw new ConflictException("Ya existe un contacto comercial con ese código.");
  if(tax!=null&&repo.existsByTaxId(tax))throw new ConflictException("Ya existe un contacto comercial con ese identificador fiscal.");
  return PartnerDtos.View.from(repo.save(new BusinessPartner(code,type,r.name().trim(),tax,clean(r.email()),clean(r.phone()),clean(r.address()))));
 }
 @Transactional public PartnerDtos.View update(PartnerType type,UUID id,PartnerDtos.WriteRequest r){
  BusinessPartner partner=require(type,id);String code=r.code().trim().toUpperCase(Locale.ROOT);
  if(!partner.getCode().equals(code))throw new ConflictException("El código del contacto es inmutable.");
  String tax=clean(r.taxId());
  if(tax!=null&&!tax.equals(partner.getTaxId())&&repo.existsByTaxId(tax))throw new ConflictException("Ya existe un contacto comercial con ese identificador fiscal.");
  partner.update(r.name().trim(),tax,clean(r.email()),clean(r.phone()),clean(r.address()));return PartnerDtos.View.from(partner);
 }
 @Transactional public void deactivate(PartnerType type,UUID id){require(type,id).deactivate();}
 private BusinessPartner require(PartnerType type,UUID id){
  return repo.findByIdAndTypeIn(id,List.of(type,PartnerType.BOTH)).orElseThrow(()->new ResourceNotFoundException(type==PartnerType.SUPPLIER?"Proveedor":"Cliente",id));
 }
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
}
