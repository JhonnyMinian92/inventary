package com.ecuapp.inventary.returns;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.UnitOfMeasure;
import com.ecuapp.inventary.catalog.UnitOfMeasureRepository;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.sales.*;
import com.ecuapp.inventary.warehouse.Warehouse;
import com.ecuapp.inventary.warehouse.WarehouseLocation;
import com.ecuapp.inventary.warehouse.WarehouseLocationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SalesReturnService {
 private final SaleRepository sales;private final SalesReturnRepository returns;private final SalesReturnLineRepository returnLines;
 private final StockBalanceRepository balances;private final WarehouseLocationRepository locations;private final StockMovementRepository movements;private final OutboxEventRepository outbox;
 private final ObjectMapper mapper;private final AuditService audit;private final IdempotencyRecordRepository idempotencyRecords;private final UnitOfMeasureRepository units;
 private final InventoryCostingService costing;private final StockBalanceLocks balanceLocks;private final SaleService saleService;
 private static final Set<String> SORT=Set.of("createdAt","returnNumber");
 public SalesReturnService(SaleRepository sales,SalesReturnRepository returns,SalesReturnLineRepository returnLines,StockBalanceRepository balances,WarehouseLocationRepository locations,
   StockMovementRepository movements,OutboxEventRepository outbox,ObjectMapper mapper,AuditService audit,IdempotencyRecordRepository idempotencyRecords,UnitOfMeasureRepository units,
   InventoryCostingService costing,StockBalanceLocks balanceLocks,SaleService saleService){
  this.sales=sales;this.returns=returns;this.returnLines=returnLines;this.balances=balances;this.locations=locations;this.movements=movements;this.outbox=outbox;this.mapper=mapper;this.audit=audit;this.idempotencyRecords=idempotencyRecords;this.units=units;
  this.costing=costing;this.balanceLocks=balanceLocks;this.saleService=saleService;
 }
 @Transactional(readOnly=true) public SalesReturnDtos.View get(UUID id){return SalesReturnDtos.View.from(returns.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Devolución de venta",id)));}
 @Transactional(readOnly=true) public org.springframework.data.domain.Page<SalesReturnDtos.View> list(UUID sourceSaleId,UUID warehouseId,java.time.Instant from,java.time.Instant to,org.springframework.data.domain.Pageable p){
  if(from!=null&&to!=null&&!from.isBefore(to))throw new InvalidQueryException("'from' debe ser anterior a 'to'.");
  return returns.search(sourceSaleId,warehouseId,com.ecuapp.inventary.common.TimeRange.lower(from),com.ecuapp.inventary.common.TimeRange.upper(to),Pages.of(p,SORT,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"createdAt"))).map(SalesReturnDtos.View::from);
 }
 @Transactional public SalesReturnDtos.View create(String key,SalesReturnDtos.CreateRequest request,String actor){
  String normalizedKey=normalizeKey(key);String hash=canonicalHash(request);
  int claimed=idempotencyRecords.insertIfAbsent(UUID.randomUUID(),"SALES_RETURN_CREATE",normalizedKey,hash);
  IdempotencyRecord idempotency=idempotencyRecords.findForUpdate("SALES_RETURN_CREATE",normalizedKey).orElseThrow(()->new IllegalStateException("No se pudo reservar la clave de idempotencia."));
  if(!hash.equals(idempotency.getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra solicitud.");
  if(claimed==0){if(idempotency.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED&&idempotency.getResourceId()!=null)return SalesReturnDtos.View.from(returns.findDetailedById(idempotency.getResourceId()).orElseThrow(()->new ConflictException("La devolución idempotente ya no existe.")));throw new ConflictException("Ya existe una operación con esta clave de idempotencia y aún no puede reproducirse.");}
  Optional<SalesReturn> previous=returns.findByIdempotencyKey(normalizedKey);
  if(previous.isPresent()){
   if(!hash.equals(previous.get().getRequestHash())&&!legacyHash(request).equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra solicitud.");
   idempotency.markCompleted("SALES_RETURN",previous.get().getId(),(short)201);idempotencyRecords.saveAndFlush(idempotency);
   return SalesReturnDtos.View.from(returns.findDetailedById(previous.get().getId()).orElseThrow());
  }
  Sale sale=sales.findForUpdateById(request.sourceSaleId()).orElseThrow(()->new ResourceNotFoundException("Venta",request.sourceSaleId()));
  if(sale.getStatus()!=SaleStatus.COMPLETED)throw new BusinessRuleException("Solo se pueden devolver ventas completadas.");
  if(!sale.getWarehouse().isActive())throw new BusinessRuleException("Reactive el almacén de la venta antes de registrar una devolución para no ingresar mercancía a un almacén inactivo.");
  Map<UUID,SaleItem> saleItems=sale.getItems().stream().collect(Collectors.toMap(SaleItem::getId, i->i));
  Set<UUID> seen=new HashSet<>();List<Calc> calc=new ArrayList<>();
  for(SalesReturnDtos.LineRequest line:request.items()){
   if(!seen.add(line.saleItemId()))throw new BusinessRuleException("No repita una línea de venta en la misma devolución.");
   SaleItem item=saleItems.get(line.saleItemId());
   if(item==null)throw new BusinessRuleException("La línea indicada no pertenece a la venta original.");
   BigDecimal quantity=line.quantity().setScale(3,RoundingMode.UNNECESSARY);
   BigDecimal returned=returnLines.sumReturnedQuantity(item.getId(),ReturnStatus.POSTED);
   if(returned.add(quantity).compareTo(item.getQuantity())>0)throw new BusinessRuleException("La cantidad devuelta supera la cantidad vendida disponible.");
   calc.add(new Calc(item,quantity,line.disposition()));
  }
  calc.sort(Comparator.comparing(c->c.item().getProduct().getId().toString()));
  Warehouse warehouse=sale.getWarehouse();
  String number="DEV-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  SalesReturn salesReturn=returns.saveAndFlush(new SalesReturn(number,sale,warehouse,request.reason().trim(),normalizedKey,hash,actor));
  for(Calc c:calc){
   SalesReturnLine line=new SalesReturnLine(c.item(),c.quantity(),c.disposition());salesReturn.addLine(line);returns.saveAndFlush(salesReturn);
   UnitOfMeasure unit=units.findById(c.item().getProduct().getUnitOfMeasureId()).orElseThrow(()->new BusinessRuleException("No existe unidad de medida para "+c.item().getSkuSnapshot()+"."));
   String base="SRET-"+salesReturn.getId()+"-"+c.item().getId();
   String movementHash=hash(salesReturn.getId()+"|"+c.item().getId()+"|"+c.quantity().toPlainString()+"|"+c.disposition());
   // Returned goods are valued at the cost recorded by the original sale.
   StockBalance balance=balanceLocks.lockOrCreate(c.item().getProduct(),warehouse);
   java.math.BigDecimal soldCost=saleService.soldUnitCost(sale,c.item(),balance);
   if(c.disposition()==ReturnDisposition.RESTOCK){
    balance.increase(c.quantity());
    InventoryCostingService.Valuation valuation=costing.inbound(c.item().getProduct(),balance.getLocation(),c.quantity(),soldCost);
    StockMovement movement=new StockMovement(c.item().getProduct(),warehouse,MovementType.RETURN_IN,c.quantity(),valuation.movementUnitCost(),"SALES_RETURN",salesReturn.getId(),base+"-IN",movementHash,"Devolución "+number,actor);
    movement.assignLocations(null,balance.getLocation(),unit,number,c.item().getId(),valuation.method());costing.post(movements.save(movement),valuation);
   }else{
    // Paired entry and exit: a scrapped return never increases sellable stock; its cost is recorded as a write-off.
    WarehouseLocation traceLocation=balance.getLocation();
    InventoryCostingService.Valuation in=costing.passThrough(c.item().getProduct(),traceLocation,c.quantity(),soldCost);
    StockMovement inbound=new StockMovement(c.item().getProduct(),warehouse,MovementType.RETURN_IN,c.quantity(),in.movementUnitCost(),"SALES_RETURN",salesReturn.getId(),base+"-IN",movementHash,"Entrada no vendible por devolución "+number,actor);
    inbound.assignLocations(null,traceLocation,unit,number,c.item().getId(),in.method());costing.post(movements.save(inbound),in);
    InventoryCostingService.Valuation out=costing.passThrough(c.item().getProduct(),traceLocation,c.quantity(),soldCost);
    StockMovement outbound=new StockMovement(c.item().getProduct(),warehouse,MovementType.RETURN_OUT,c.quantity(),out.movementUnitCost(),"SALES_RETURN",salesReturn.getId(),base+"-OUT",movementHash,"Merma por devolución "+number,actor);
    outbound.assignLocations(traceLocation,null,unit,number,c.item().getId(),out.method());costing.post(movements.save(outbound),out);
   }
  }
  returns.save(salesReturn);
  outbox.save(new OutboxEvent("SALES_RETURN",salesReturn.getId(),"SALES_RETURN_POSTED",Map.of("returnId",salesReturn.getId().toString(),"returnNumber",number,"saleId",sale.getId().toString())));
  audit.record(actor,"SALES_RETURN_POSTED","SALES_RETURN",salesReturn.getId(),Map.of("returnNumber",number,"sourceSaleId",sale.getId().toString()));
  idempotency.markCompleted("SALES_RETURN",salesReturn.getId(),(short)201);idempotencyRecords.saveAndFlush(idempotency);
  return SalesReturnDtos.View.from(returns.findDetailedById(salesReturn.getId()).orElseThrow());
 }
 private record Calc(SaleItem item,BigDecimal quantity,ReturnDisposition disposition){}
 private String canonicalHash(SalesReturnDtos.CreateRequest r){List<String> lines=r.items().stream().map(x->x.saleItemId()+"|"+x.quantity().setScale(3,RoundingMode.UNNECESSARY).toPlainString()+"|"+x.disposition().name()).sorted().toList();return hash(r.sourceSaleId()+"|"+clean(r.reason())+"|"+String.join(";",lines));}
 private String legacyHash(SalesReturnDtos.CreateRequest r){return hash(r.sourceSaleId()+"|"+json(r));}
 private String normalizeKey(String key){if(key==null||key.isBlank()||key.trim().length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");return key.trim();}
 private String clean(String v){return v==null||v.isBlank()?"":v.trim();}
 private String json(SalesReturnDtos.CreateRequest r){try{return mapper.writeValueAsString(r);}catch(JsonProcessingException ex){throw new BusinessRuleException("No se pudo validar la devolución.");}}
 private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
