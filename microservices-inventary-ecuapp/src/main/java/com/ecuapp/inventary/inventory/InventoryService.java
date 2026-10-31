package com.ecuapp.inventary.inventory;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.warehouse.*;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.auth.WarehouseScopeService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {
 private static final Map<String,String> BALANCE_SORT=Map.of("onHand","onHand","reserved","reserved","updatedAt","updatedAt","sku","product.sku","productName","product.name","warehouseCode","warehouse.code");
 private static final Map<String,String> MOVEMENT_SORT=Map.of("occurredAt","occurredAt","quantity","quantity","movementType","movementType");
 private final ProductRepository products;private final IdempotencyRecordRepository idempotencyRecords;private final WarehouseRepository warehouses;private final StockBalanceRepository balances;private final StockMovementRepository movements;private final WarehouseLocationRepository locations;private final UnitOfMeasureRepository units;private final AuditService audit;
 private final InventoryCostingService costing;private final InventoryValuationEntryRepository valuationEntries;private final WarehouseScopeService scopes;
 public InventoryService(ProductRepository products,IdempotencyRecordRepository idempotencyRecords,WarehouseRepository warehouses,StockBalanceRepository balances,StockMovementRepository movements,WarehouseLocationRepository locations,UnitOfMeasureRepository units,AuditService audit,
  InventoryCostingService costing,InventoryValuationEntryRepository valuationEntries,WarehouseScopeService scopes){this.products=products;this.idempotencyRecords=idempotencyRecords;this.warehouses=warehouses;this.balances=balances;this.movements=movements;this.locations=locations;this.units=units;this.audit=audit;this.costing=costing;this.valuationEntries=valuationEntries;this.scopes=scopes;}
 @Transactional(readOnly=true) public Page<InventoryDtos.BalanceView> balances(UUID productId,UUID warehouseId,boolean onlyAvailable,Pageable p,String actor){
  if(warehouseId!=null)scopes.requireWarehouseAccess(actor,warehouseId);
  var allowed=scopes.accessibleWarehouseIds(actor);var pageable=Pages.of(p,BALANCE_SORT,Sort.by("product.sku"));
  if(allowed!=null&&allowed.isEmpty())return Page.empty(pageable);
  return (allowed==null?balances.search(productId,warehouseId,onlyAvailable,pageable):balances.searchScoped(productId,warehouseId,onlyAvailable,allowed,pageable)).map(InventoryDtos.BalanceView::from);
 }
 @Transactional(readOnly=true) public Page<InventoryDtos.MovementView> movements(UUID productId,UUID warehouseId,MovementType type,String referenceType,UUID referenceId,Instant from,Instant to,Pageable p,String actor){
  if(from!=null&&to!=null&&!from.isBefore(to))throw new InvalidQueryException("'from' debe ser anterior a 'to'.");
  if(warehouseId!=null)scopes.requireWarehouseAccess(actor,warehouseId);
  var allowed=scopes.accessibleWarehouseIds(actor);var pageable=Pages.of(p,MOVEMENT_SORT,Sort.by(Sort.Direction.DESC,"occurredAt"));
  if(allowed!=null&&allowed.isEmpty())return Page.empty(pageable);
  String reference=referenceType==null||referenceType.isBlank()?null:referenceType.trim().toUpperCase(java.util.Locale.ROOT);
  var lower=com.ecuapp.inventary.common.TimeRange.lower(from);var upper=com.ecuapp.inventary.common.TimeRange.upper(to);
  return (allowed==null?movements.search(productId,warehouseId,type,reference,referenceId,lower,upper,pageable):
   movements.searchScoped(productId,warehouseId,type,reference,referenceId,lower,upper,allowed,pageable))
   .map(m->InventoryDtos.MovementView.from(m,scopes.canReadFinancialData(actor)));
 }
 @Transactional(readOnly=true) public InventoryDtos.MovementDetailView movement(UUID id,String actor){
  StockMovement movement=movements.findById(id).orElseThrow(()->new ResourceNotFoundException("Movimiento de inventario",id));
  scopes.requireWarehouseAccess(actor,movement.getWarehouse().getId());
  boolean includeCost=scopes.canReadFinancialData(actor);
  var entries=includeCost?valuationEntries.findByStockMovementIdOrderByCreatedAtAsc(id).stream()
      .map(InventoryDtos.ValuationEntryView::from).toList():List.<InventoryDtos.ValuationEntryView>of();
  BigDecimal total=includeCost?entries.stream().map(InventoryDtos.ValuationEntryView::totalCost)
      .reduce(BigDecimal.ZERO.setScale(4),BigDecimal::add):null;
  return new InventoryDtos.MovementDetailView(InventoryDtos.MovementView.from(movement,includeCost),total,entries);
 }
 @Transactional(readOnly=true) public InventoryDtos.BalanceView balance(UUID productId,UUID warehouseId,String actor){
  scopes.requireWarehouseAccess(actor,warehouseId);
  StockBalance b=balances.findByProductIdAndWarehouseId(productId,warehouseId).orElseThrow(()->new ResourceNotFoundException("Saldo de inventario",productId+"/"+warehouseId));
  return InventoryDtos.BalanceView.from(b);
 }
 @Transactional(readOnly=true) public InventoryDtos.AvailabilityView availability(UUID productId,String sku,UUID warehouseId,BigDecimal requested,String actor){
  scopes.requireWarehouseAccess(actor,warehouseId);
  if(productId==null&&(sku==null||sku.isBlank()))throw new InvalidQueryException("Indique productId o sku.");
  if(requested!=null&&requested.signum()<=0)throw new InvalidQueryException("quantity debe ser mayor que cero.");
  Product product=productId!=null?products.findById(productId).orElseThrow(()->new ResourceNotFoundException("Producto",productId))
   :products.findBySkuIgnoreCase(sku.trim()).orElseThrow(()->new ResourceNotFoundException("Producto",sku.trim()));
  Warehouse warehouse=warehouses.findById(warehouseId).orElseThrow(()->new ResourceNotFoundException("Almacén",warehouseId));
  StockBalance balance=balances.findByProductIdAndWarehouseId(product.getId(),warehouse.getId()).orElse(null);
  BigDecimal zero=BigDecimal.ZERO.setScale(3);
  BigDecimal onHand=balance==null?zero:balance.getOnHand(),reserved=balance==null?zero:balance.getReserved(),available=balance==null?zero:balance.available();
  if(!warehouse.isActive()||!product.isActive())available=zero;
  BigDecimal wanted=requested==null?null:requested.setScale(3,RoundingMode.HALF_UP);
  boolean sufficient=wanted==null?available.signum()>0:available.compareTo(wanted)>=0;
  return new InventoryDtos.AvailabilityView(product.getId(),product.getSku(),warehouse.getId(),warehouse.getCode(),onHand,reserved,available,wanted,sufficient);
 }
 @Transactional public InventoryDtos.MovementView adjust(String key,InventoryDtos.AdjustmentRequest r,String actor){
  scopes.requireWarehouseAccess(actor,r.warehouseId());
  if(key==null||key.isBlank()||key.trim().length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");
  key=key.trim();
  BigDecimal quantity=r.quantity().setScale(3,RoundingMode.UNNECESSARY);
  BigDecimal requestedCost=r.unitCost()==null?null:r.unitCost().setScale(4,RoundingMode.UNNECESSARY);
  if(requestedCost!=null&&r.direction()==InventoryDtos.Direction.DECREASE)throw new BusinessRuleException("unitCost solo aplica a ajustes de entrada; las salidas se valoran con la política vigente.");
  String hash=hash(r.productId()+"|"+r.warehouseId()+"|"+r.direction()+"|"+quantity.toPlainString()+"|"+r.reason().trim()+(requestedCost==null?"":"|"+requestedCost.toPlainString()));
  var previous=movements.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!hash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
   return InventoryDtos.MovementView.from(previous.get(),scopes.canReadFinancialData(actor));
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
  Product product=products.findForUpdate(r.productId()).orElseThrow(()->new ResourceNotFoundException("Producto",r.productId()));
  Warehouse warehouse=warehouses.findForUpdate(r.warehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén",r.warehouseId()));
  if(r.direction()==InventoryDtos.Direction.INCREASE&&(!product.isActive()||!warehouse.isActive()))
   throw new BusinessRuleException("No se puede aumentar existencias de un producto o almacén inactivo. Para retirar saldo residual, registre un ajuste de salida autorizado.");
  if(product.getKind()!=ProductKind.STOCKED_GOOD)throw new BusinessRuleException("Solo los productos con control de stock admiten movimientos físicos.");
  
  StockBalance balance=balances.findForUpdate(product.getId(),warehouse.getId()).orElse(null);
  if(balance==null){
   if(r.direction()==InventoryDtos.Direction.DECREASE)throw new BusinessRuleException("No existe saldo disponible para disminuir.");
   WarehouseLocation mainLocation=mainLocation(warehouse);
   balances.insertIfAbsent(UUID.randomUUID(),product.getId(),warehouse.getId(),mainLocation.getId());
   balance=balances.findForUpdate(product.getId(),warehouse.getId()).orElseThrow(()->new ConflictException("No se pudo crear el saldo de inventario."));
  }
  MovementType type;InventoryCostingService.Valuation valuation;
  if(r.direction()==InventoryDtos.Direction.INCREASE){
   balance.increase(quantity);type=MovementType.ADJUSTMENT_IN;
   BigDecimal cost=requestedCost!=null?requestedCost:costing.currentUnitCost(product,balance.getLocation());
   // The first entry with an explicit cost (initial stock load) sets the price base of a product never purchased.
   if(requestedCost!=null&&product.getCostPrice().signum()==0)product.applyPurchaseCost(requestedCost);
   valuation=costing.inbound(product,balance.getLocation(),quantity,cost);
  }else{
   balance.decrease(quantity);type=MovementType.ADJUSTMENT_OUT;
   valuation=costing.outbound(product,balance.getLocation(),quantity);
  }
  StockMovement movement=new StockMovement(product,warehouse,type,quantity,valuation.movementUnitCost(),"ADJUSTMENT",null,key,hash,r.reason().trim(),actor);
  movement.assignLocations(type==MovementType.ADJUSTMENT_OUT?balance.getLocation():null,type==MovementType.ADJUSTMENT_IN?balance.getLocation():null,
   units.findById(product.getUnitOfMeasureId()).orElseThrow(()->new ResourceNotFoundException("Unidad de medida",product.getUnitOfMeasureId())),key,null,valuation.method());
  StockMovement persisted=movements.saveAndFlush(movement);
  costing.post(persisted,valuation);
  idempotency.markCompleted("STOCK_MOVEMENT",persisted.getId(),(short) 200);
  idempotencyRecords.save(idempotency);
  audit.record(actor,"STOCK_ADJUSTED","STOCK_MOVEMENT",persisted.getId(),Map.of("productId",product.getId().toString(),"warehouseId",warehouse.getId().toString(),"movementType",type.name(),"quantity",quantity.toPlainString(),"valuedCost",valuation.totalCost().toPlainString()));
  return InventoryDtos.MovementView.from(persisted,scopes.canReadFinancialData(actor));
 }
 private WarehouseLocation mainLocation(Warehouse warehouse){
  locations.insertIfAbsent(UUID.randomUUID(),warehouse.getId(),"MAIN","Ubicación principal","STORAGE");
  return locations.findByWarehouseIdAndCode(warehouse.getId(),"MAIN").orElseThrow(()->new ConflictException("No se pudo asegurar la ubicación principal del almacén."));
 }
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("No se pudo calcular huella de idempotencia.",ex);}}
}
