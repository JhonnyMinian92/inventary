package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.audit.AuditService;
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
 private final ProductRepository products;private final IdempotencyRecordRepository idempotencyRecords;private final WarehouseRepository warehouses;private final StockBalanceRepository balances;private final StockMovementRepository movements;private final WarehouseLocationRepository locations;private final UnitOfMeasureRepository units;private final AuditService audit;
 public InventoryService(ProductRepository products,IdempotencyRecordRepository idempotencyRecords,WarehouseRepository warehouses,StockBalanceRepository balances,StockMovementRepository movements,WarehouseLocationRepository locations,UnitOfMeasureRepository units,AuditService audit){this.products=products;this.idempotencyRecords=idempotencyRecords;this.warehouses=warehouses;this.balances=balances;this.movements=movements;this.locations=locations;this.units=units;this.audit=audit;}
 @Transactional(readOnly=true) public Page<InventoryDtos.BalanceView> balances(Pageable p){return balances.findAll(limited(p)).map(InventoryDtos.BalanceView::from);}
 @Transactional(readOnly=true) public Page<InventoryDtos.MovementView> movements(Pageable p){
  Pageable ordered=PageRequest.of(p.getPageNumber(),Math.min(p.getPageSize(),100),Sort.by(Sort.Direction.DESC,"occurredAt"));
  return movements.findAll(ordered).map(InventoryDtos.MovementView::from);
 }
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
  idempotencyRecords.insertIfAbsent(UUID.randomUUID(),"INVENTORY_ADJUSTMENT",key,hash);
  IdempotencyRecord idempotency=idempotencyRecords.findForUpdate("INVENTORY_ADJUSTMENT",key)
   .orElseThrow(()->new ConflictException("No se pudo reservar la clave de idempotencia."));
  if(!hash.equals(idempotency.getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
  if(idempotency.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED && idempotency.getResourceId()!=null)
   return InventoryDtos.MovementView.from(movements.findById(idempotency.getResourceId())
    .orElseThrow(()->new ResourceNotFoundException("Movimiento de inventario",idempotency.getResourceId())));
  if(idempotency.getStatus()!=IdempotencyRecord.RecordStatus.IN_PROGRESS)
   throw new ConflictException("La clave de idempotencia no está disponible para una nueva operación.");
  Product product=products.findById(r.productId()).orElseThrow(()->new ResourceNotFoundException("Producto",r.productId()));
  Warehouse warehouse=warehouses.findById(r.warehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén",r.warehouseId()));
  if(!product.isActive())throw new BusinessRuleException("No se puede ajustar un producto inactivo.");
  if(product.getKind()!=ProductKind.STOCKED_GOOD)throw new BusinessRuleException("Solo los productos con control de stock admiten movimientos físicos.");
  if(!warehouse.isActive())throw new BusinessRuleException("No se puede ajustar un almacén inactivo.");
  StockBalance balance=balances.findForUpdate(product.getId(),warehouse.getId()).orElse(null);
  if(balance==null){
   if(r.direction()==InventoryDtos.Direction.DECREASE)throw new BusinessRuleException("No existe saldo disponible para disminuir.");
   WarehouseLocation mainLocation=locations.findByWarehouseIdAndCode(warehouse.getId(),"MAIN")
    .orElseGet(()->locations.save(new WarehouseLocation(warehouse,"MAIN","Ubicación principal",WarehouseLocation.LocationType.STORAGE)));
   balance=balances.save(new StockBalance(product,warehouse,mainLocation));
  }
  MovementType type;
  if(r.direction()==InventoryDtos.Direction.INCREASE){balance.increase(quantity);type=MovementType.ADJUSTMENT_IN;}
  else {balance.decrease(quantity);type=MovementType.ADJUSTMENT_OUT;}
  StockMovement movement=new StockMovement(product,warehouse,type,quantity,product.getCostPrice(),"ADJUSTMENT",null,key,hash,r.reason().trim(),actor);
  movement.assignLocations(type==MovementType.ADJUSTMENT_OUT?balance.getLocation():null,type==MovementType.ADJUSTMENT_IN?balance.getLocation():null,
   units.findById(product.getUnitOfMeasureId()).orElseThrow(()->new ResourceNotFoundException("Unidad de medida",product.getUnitOfMeasureId())),key,null,null);
  StockMovement persisted=movements.saveAndFlush(movement);
  idempotency.markCompleted("STOCK_MOVEMENT",persisted.getId(),(short) 200);
  idempotencyRecords.save(idempotency);
  audit.record(actor,"STOCK_ADJUSTED","STOCK_MOVEMENT",persisted.getId(),java.util.Map.of("productId",product.getId().toString(),"warehouseId",warehouse.getId().toString(),"movementType",type.name(),"quantity",quantity.toPlainString()));
  return InventoryDtos.MovementView.from(persisted);
 }
 private Pageable limited(Pageable p){return PageRequest.of(p.getPageNumber(),Math.min(p.getPageSize(),100),p.getSort());}
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("No se pudo calcular huella de idempotencia.",ex);}}
}
