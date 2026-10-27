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
 @Transactional(readOnly=true) public List<ReorderDtos.View> list(UUID warehouseId,Boolean active){
  return rules.findAll().stream().filter(r->warehouseId==null||r.getWarehouse().getId().equals(warehouseId)).filter(r->active==null||r.isActive()==active)
   .sorted(Comparator.comparing((ReorderRule r)->r.getWarehouse().getCode()).thenComparing(r->r.getProduct().getSku())).map(ReorderDtos.View::from).toList();
 }
 @Transactional(readOnly=true) public List<ReorderDtos.Suggestion> suggestions(){
  List<ReorderDtos.Suggestion> result=new ArrayList<>();
  for(ReorderRule rule:rules.findByActiveTrue()){
   Product product=rule.getProduct();Warehouse warehouse=rule.getWarehouse();BusinessPartner preferred=rule.getPreferredSupplier();
   if(!product.isActive()||!product.getCategory().isActive()||product.getKind()!=ProductKind.STOCKED_GOOD||!warehouse.isActive()
      ||(preferred!=null&&!preferred.isActive()))continue;
   BigDecimal available=balances.findByProductIdAndWarehouseId(product.getId(),warehouse.getId()).map(StockBalance::available).orElse(BigDecimal.ZERO.setScale(3));
   if(available.compareTo(rule.getMinimumQuantity())<=0){
    BigDecimal suggestion=rule.getTargetQuantity().subtract(available).max(BigDecimal.ZERO).setScale(3,RoundingMode.HALF_UP);
    result.add(new ReorderDtos.Suggestion(product.getId(),product.getSku(),product.getName(),warehouse.getId(),
     warehouse.getCode(),available,rule.getMinimumQuantity(),rule.getTargetQuantity(),suggestion,rule.getLeadTimeDays(),
     preferred==null?null:preferred.getId(),preferred==null?null:preferred.getName()));
   }
  }
  return result;
 }
 @Transactional public ReorderDtos.View create(ReorderDtos.WriteRequest r,String actor){
  if(r.targetQuantity().compareTo(r.minimumQuantity())<0)throw new BusinessRuleException("La cantidad objetivo debe ser igual o superior al mínimo.");
  Product product=products.findForUpdate(r.productId()).orElseThrow(()->new ResourceNotFoundException("Producto",r.productId()));
  Warehouse warehouse=warehouses.findForUpdate(r.warehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén",r.warehouseId()));
  if(!product.isActive()||!product.getCategory().isActive()||!warehouse.isActive())throw new BusinessRuleException("El producto, su categoría y el almacén deben estar activos.");
  if(product.getKind()!=ProductKind.STOCKED_GOOD)throw new BusinessRuleException("Solo los productos físicos con control de stock admiten reglas de reposición.");
  if(rules.existsByProductIdAndWarehouseId(product.getId(),warehouse.getId()))throw new ConflictException("Ya existe una regla de reposición para ese producto y almacén.");
  BusinessPartner supplier=supplier(r.preferredSupplierId());
  ReorderRule rule=rules.saveAndFlush(new ReorderRule(product,warehouse,scale(r.minimumQuantity()),scale(r.targetQuantity()),r.leadTimeDays(),supplier));
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
 @Transactional public void deactivate(UUID id,String actor){ReorderRule rule=rules.findById(id).orElseThrow(()->new ResourceNotFoundException("Regla de reposición",id));if(!rule.isActive())return;rule.deactivate();audit.record(actor,"REORDER_RULE_DEACTIVATED","REORDER_RULE",id,Map.of());}
 private BusinessPartner supplier(UUID id){
  if(id==null)return null;
  BusinessPartner p=partners.findByIdAndTypeIn(id,List.of(PartnerType.SUPPLIER,PartnerType.BOTH)).orElseThrow(()->new ResourceNotFoundException("Proveedor",id));
  if(!p.isActive())throw new BusinessRuleException("El proveedor preferido está inactivo.");
  return p;
 }
 private BigDecimal scale(BigDecimal v){return v.setScale(3,RoundingMode.UNNECESSARY);}
}
