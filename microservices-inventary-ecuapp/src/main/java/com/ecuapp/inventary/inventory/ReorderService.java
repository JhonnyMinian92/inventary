package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.partners.*;
import com.ecuapp.inventary.warehouse.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ReorderService {
 private final ReorderRuleRepository rules;private final ProductRepository products;private final WarehouseRepository warehouses;private final BusinessPartnerRepository partners;
 private final StockBalanceRepository balances;private final AuditService audit;
 public ReorderService(ReorderRuleRepository rules,ProductRepository products,WarehouseRepository warehouses,BusinessPartnerRepository partners,StockBalanceRepository balances,AuditService audit){
  this.rules=rules;this.products=products;this.warehouses=warehouses;this.partners=partners;this.balances=balances;this.audit=audit;
 }
 @Transactional(readOnly=true) public List<ReorderDtos.View> list(){return rules.findAll().stream().map(ReorderDtos.View::from).toList();}
 @Transactional(readOnly=true) public List<ReorderDtos.Suggestion> suggestions(){
  List<ReorderDtos.Suggestion> result=new ArrayList<>();
  for(ReorderRule rule:rules.findByActiveTrue()){
   BigDecimal available=balances.findByProductIdAndWarehouseId(rule.getProduct().getId(),rule.getWarehouse().getId()).map(StockBalance::available).orElse(BigDecimal.ZERO.setScale(3));
   if(available.compareTo(rule.getMinimumQuantity())<=0){
    BigDecimal suggestion=rule.getTargetQuantity().subtract(available).max(BigDecimal.ZERO).setScale(3,RoundingMode.HALF_UP);
    result.add(new ReorderDtos.Suggestion(rule.getProduct().getId(),rule.getProduct().getSku(),rule.getProduct().getName(),rule.getWarehouse().getId(),
     rule.getWarehouse().getCode(),available,rule.getMinimumQuantity(),rule.getTargetQuantity(),suggestion,rule.getLeadTimeDays(),
     rule.getPreferredSupplier()==null?null:rule.getPreferredSupplier().getId(),rule.getPreferredSupplier()==null?null:rule.getPreferredSupplier().getName()));
   }
  }
  return result;
 }
 @Transactional public ReorderDtos.View create(ReorderDtos.WriteRequest r,String actor){
  if(r.targetQuantity().compareTo(r.minimumQuantity())<0)throw new BusinessRuleException("La cantidad objetivo debe ser igual o superior al mínimo.");
  Product product=products.findById(r.productId()).orElseThrow(()->new ResourceNotFoundException("Producto",r.productId()));
  Warehouse warehouse=warehouses.findById(r.warehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén",r.warehouseId()));
  if(!product.isActive()||!warehouse.isActive())throw new BusinessRuleException("El producto y almacén deben estar activos.");
  if(rules.existsByProductIdAndWarehouseId(product.getId(),warehouse.getId()))throw new ConflictException("Ya existe una regla de reposición para ese producto y almacén.");
  BusinessPartner supplier=supplier(r.preferredSupplierId());
  ReorderRule rule=rules.save(new ReorderRule(product,warehouse,scale(r.minimumQuantity()),scale(r.targetQuantity()),r.leadTimeDays(),supplier));
  audit.record(actor,"REORDER_RULE_CREATED","REORDER_RULE",rule.getId(),Map.of("productId",product.getId().toString(),"warehouseId",warehouse.getId().toString()));
  return ReorderDtos.View.from(rule);
 }
 @Transactional public ReorderDtos.View update(UUID id,ReorderDtos.WriteRequest r,String actor){
  if(r.targetQuantity().compareTo(r.minimumQuantity())<0)throw new BusinessRuleException("La cantidad objetivo debe ser igual o superior al mínimo.");
  ReorderRule rule=rules.findById(id).orElseThrow(()->new ResourceNotFoundException("Regla de reposición",id));
  if(!rule.getProduct().getId().equals(r.productId())||!rule.getWarehouse().getId().equals(r.warehouseId()))throw new ConflictException("Producto y almacén de una regla existente son inmutables.");
  rule.update(scale(r.minimumQuantity()),scale(r.targetQuantity()),r.leadTimeDays(),supplier(r.preferredSupplierId()));
  audit.record(actor,"REORDER_RULE_UPDATED","REORDER_RULE",id,Map.of("productId",r.productId().toString(),"warehouseId",r.warehouseId().toString()));
  return ReorderDtos.View.from(rule);
 }
 @Transactional public void deactivate(UUID id,String actor){ReorderRule rule=rules.findById(id).orElseThrow(()->new ResourceNotFoundException("Regla de reposición",id));rule.deactivate();audit.record(actor,"REORDER_RULE_DEACTIVATED","REORDER_RULE",id,Map.of());}
 private BusinessPartner supplier(UUID id){
  if(id==null)return null;
  BusinessPartner p=partners.findByIdAndTypeIn(id,List.of(PartnerType.SUPPLIER,PartnerType.BOTH)).orElseThrow(()->new ResourceNotFoundException("Proveedor",id));
  if(!p.isActive())throw new BusinessRuleException("El proveedor preferido está inactivo.");
  return p;
 }
 private BigDecimal scale(BigDecimal v){return v.setScale(3,RoundingMode.UNNECESSARY);}
}
