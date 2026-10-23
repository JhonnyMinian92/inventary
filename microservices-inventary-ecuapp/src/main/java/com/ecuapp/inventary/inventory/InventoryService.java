package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.warehouse.*;
import com.ecuapp.inventary.common.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class InventoryService {
 private final ProductRepository products;private final WarehouseRepository warehouses;private final StockBalanceRepository balances;private final StockMovementRepository movements;
 public InventoryService(ProductRepository products,WarehouseRepository warehouses,StockBalanceRepository balances,StockMovementRepository movements){this.products=products;this.warehouses=warehouses;this.balances=balances;this.movements=movements;}
 @Transactional(readOnly=true) public Page<InventoryDtos.BalanceView> balances(Pageable p){return balances.findAll(limited(p)).map(InventoryDtos.BalanceView::from);}
 @Transactional(readOnly=true) public Page<InventoryDtos.MovementView> movements(Pageable p){return movements.findAll(limited(p).withSort(Sort.by(Sort.Direction.DESC,"occurredAt"))).map(InventoryDtos.MovementView::from);}
 @Transactional(readOnly=true) public InventoryDtos.BalanceView balance(UUID productId,UUID warehouseId){
  StockBalance b=balances.findByProductIdAndWarehouseId(productId,warehouseId).orElseThrow(()->new ResourceNotFoundException("Saldo de inventario",productId+"/"+warehouseId));
  return InventoryDtos.BalanceView.from(b);
 }
 @Transactional public InventoryDtos.MovementView adjust(String key,InventoryDtos.AdjustmentRequest r,String actor){
  if(key==null||key.isBlank()||key.length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");
  BigDecimal quantity=r.quantity().setScale(3,java.math.RoundingMode.UNNECESSARY);
  String hash=hash(r.productId()+"|"+r.warehouseId()+"|"+r.direction()+"|"+quantity.toPlainString()+"|"+r.reason().trim());
  var previous=movements.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!hash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
   return InventoryDtos.MovementView.from(previous.get());
  }
  Product product=products.findById(r.productId()).orElseThrow(()->new ResourceNotFoundException("Producto",r.productId()));
  Warehouse warehouse=warehouses.findById(r.warehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén",r.warehouseId()));
  if(!product.isActive())throw new BusinessRuleException("No se puede ajustar un producto inactivo.");
  if(!warehouse.isActive())throw new BusinessRuleException("No se puede ajustar un almacén inactivo.");
  StockBalance balance=balances.findForUpdate(product.getId(),warehouse.getId()).orElse(null);
  if(balance==null){
   if(r.direction()==InventoryDtos.Direction.DECREASE)throw new BusinessRuleException("No existe saldo disponible para disminuir.");
   balance=balances.save(new StockBalance(product,warehouse));
  }
  MovementType type;
  if(r.direction()==InventoryDtos.Direction.INCREASE){balance.increase(quantity);type=MovementType.ADJUSTMENT_IN;}
  else {balance.decrease(quantity);type=MovementType.ADJUSTMENT_OUT;}
  StockMovement movement=new StockMovement(product,warehouse,type,quantity,product.getCostPrice(),"ADJUSTMENT",null,key,hash,r.reason().trim(),actor);
  return InventoryDtos.MovementView.from(movements.save(movement));
 }
 private Pageable limited(Pageable p){return PageRequest.of(p.getPageNumber(),Math.min(p.getPageSize(),100),p.getSort());}
 private String hash(String value){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception ex){throw new IllegalStateException("No se pudo calcular huella de idempotencia.",ex);}
 }
}
