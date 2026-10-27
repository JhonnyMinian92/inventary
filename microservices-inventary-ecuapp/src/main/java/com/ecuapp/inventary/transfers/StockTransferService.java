package com.ecuapp.inventary.transfers;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.sales.OutboxEvent;
import com.ecuapp.inventary.sales.OutboxEventRepository;
import com.ecuapp.inventary.warehouse.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
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

@Service
public class StockTransferService {
 private final StockTransferRepository transfers;
 private final IdempotencyRecordRepository idempotencyRecords;
 private final ProductRepository products;
 private final WarehouseRepository warehouses;
 private final StockBalanceRepository balances;
 private final WarehouseLocationRepository locations;
 private final UnitOfMeasureRepository units;
 private final StockMovementRepository movements;
 private final OutboxEventRepository outbox;
 private final ObjectMapper mapper;
 private final AuditService audit;
 private final InventoryCostingService costing;
 private static final Set<String> SORT=Set.of("createdAt","transferNumber","status","dispatchedAt","completedAt");

 public StockTransferService(StockTransferRepository transfers,IdempotencyRecordRepository idempotencyRecords,
  ProductRepository products,WarehouseRepository warehouses,StockBalanceRepository balances,
  WarehouseLocationRepository locations,UnitOfMeasureRepository units,StockMovementRepository movements,
  OutboxEventRepository outbox,ObjectMapper mapper,AuditService audit,InventoryCostingService costing){
  this.transfers=transfers;this.idempotencyRecords=idempotencyRecords;this.products=products;this.warehouses=warehouses;
  this.balances=balances;this.locations=locations;this.units=units;this.movements=movements;this.outbox=outbox;
  this.mapper=mapper;this.audit=audit;this.costing=costing;
 }

 @Transactional(readOnly=true)
 public Page<TransferDtos.View> list(TransferStatus status,UUID sourceWarehouseId,UUID destinationWarehouseId,Pageable p){
  return transfers.search(status,sourceWarehouseId,destinationWarehouseId,Pages.of(p,SORT,Sort.by(Sort.Direction.DESC,"createdAt"))).map(TransferDtos.View::from);
 }
 @Transactional(readOnly=true)
 public TransferDtos.View get(UUID id){return TransferDtos.View.from(requireDetailed(id));}

 @Transactional
 public TransferDtos.View create(String rawKey,TransferDtos.CreateRequest request,String actor){
  String key=normalizeKey(rawKey);
  String hash=hash(canonicalRequest(request));
  Optional<StockTransfer> previous=transfers.findByIdempotencyKey(key);
  if(previous.isPresent()){
   String legacyHash=hash(mapperValue(request));
   if(!hash.equals(previous.get().getRequestHash())&&!legacyHash.equals(previous.get().getRequestHash()))
    throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
   IdempotencyRecord prior=idempotencyRecords.findForUpdate("STOCK_TRANSFER_CREATE",key).orElse(null);
   if(prior!=null){
    if(!hash.equals(prior.getRequestHash())&&!legacyHash.equals(prior.getRequestHash()))
     throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
    if(prior.getStatus()!=IdempotencyRecord.RecordStatus.COMPLETED){
     prior.markCompleted("STOCK_TRANSFER",previous.get().getId(),(short)201);idempotencyRecords.saveAndFlush(prior);
    }
   }else{
    IdempotencyRecord created=claim("STOCK_TRANSFER_CREATE",key,hash);
    if(created.getStatus()!=IdempotencyRecord.RecordStatus.COMPLETED){created.markCompleted("STOCK_TRANSFER",previous.get().getId(),(short)201);idempotencyRecords.saveAndFlush(created);}
   }
   return TransferDtos.View.from(requireDetailed(previous.get().getId()));
  }
  IdempotencyRecord idem=claim("STOCK_TRANSFER_CREATE",key,hash);
  if(idem.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED)return replay(idem);
  if(request.sourceWarehouseId().equals(request.destinationWarehouseId()))
   throw new BusinessRuleException("El almacén de origen y destino deben ser diferentes.");
  Warehouse source;
  Warehouse destination;
  // Every transfer locks both warehouses in the same UUID order so it serializes with deactivation.
  if(request.sourceWarehouseId().compareTo(request.destinationWarehouseId())<0){
   source=warehouses.findForUpdate(request.sourceWarehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén origen",request.sourceWarehouseId()));
   destination=warehouses.findForUpdate(request.destinationWarehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén destino",request.destinationWarehouseId()));
  }else{
   destination=warehouses.findForUpdate(request.destinationWarehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén destino",request.destinationWarehouseId()));
   source=warehouses.findForUpdate(request.sourceWarehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén origen",request.sourceWarehouseId()));
  }
  if(!source.isActive()||!destination.isActive())throw new BusinessRuleException("Ambos almacenes deben estar activos.");
  List<TransferDtos.LineRequest> ordered=new ArrayList<>(request.items());
  ordered.sort(Comparator.comparing(x->x.productId().toString()));
  Set<UUID> seen=new HashSet<>();List<StockTransferLine> lines=new ArrayList<>();
  for(TransferDtos.LineRequest item:ordered){
   if(!seen.add(item.productId()))throw new BusinessRuleException("No repita un producto en más de una línea de transferencia.");
   Product product=products.findForUpdate(item.productId()).orElseThrow(()->new ResourceNotFoundException("Producto",item.productId()));
   if(!product.isActive())throw new BusinessRuleException("No se puede transferir un producto inactivo.");
   if(product.getKind()!=ProductKind.STOCKED_GOOD)throw new BusinessRuleException("Solo los productos con control físico admiten transferencias.");
   BigDecimal quantity=item.quantity().setScale(3,RoundingMode.UNNECESSARY);
   lines.add(new StockTransferLine(product,quantity,product.getCostPrice()));
  }
  String number="TRF-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  StockTransfer transfer=transfers.saveAndFlush(new StockTransfer(number,source,destination,key,hash,actor));
  lines.forEach(transfer::addLine);transfers.saveAndFlush(transfer);
  idem.markCompleted("STOCK_TRANSFER",transfer.getId(),(short)201);idempotencyRecords.saveAndFlush(idem);
  audit.record(actor,"STOCK_TRANSFER_CREATED","STOCK_TRANSFER",transfer.getId(),Map.of("transferNumber",number,"lineCount",lines.size(),"sourceWarehouseId",source.getId().toString(),"destinationWarehouseId",destination.getId().toString()));
  event(transfer,"STOCK_TRANSFER_CREATED",actor,Map.of("transferNumber",number,"lineCount",lines.size(),"sourceWarehouseId",source.getId().toString(),"destinationWarehouseId",destination.getId().toString()));
  return TransferDtos.View.from(requireDetailed(transfer.getId()));
 }

 @Transactional
 public TransferDtos.View dispatch(UUID id,String rawKey,String actor){
  String key=normalizeKey(rawKey);IdempotencyRecord idem=claim("STOCK_TRANSFER_DISPATCH",key,hash("STOCK_TRANSFER_DISPATCH|"+id));
  if(idem.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED)return replay(idem);
  StockTransfer transfer=requireForUpdate(id);
  if(transfer.getStatus()==TransferStatus.IN_TRANSIT||transfer.getStatus()==TransferStatus.COMPLETED||transfer.getStatus()==TransferStatus.REVERSED){
   complete(idem,transfer,200);return TransferDtos.View.from(requireDetailed(id));
  }
  if(transfer.getStatus()!=TransferStatus.DRAFT)throw new ConflictException("La transferencia no puede despacharse en su estado actual.");
  List<StockTransferLine> lines=orderedLines(transfer);
  Map<UUID,UnitOfMeasure> measureByProduct=loadUnits(lines);
  for(StockTransferLine line:lines){
   StockBalance source=balances.findForUpdate(line.getProduct().getId(),transfer.getSourceWarehouse().getId())
    .orElseThrow(()->new BusinessRuleException("No existe saldo de origen para "+line.getSkuSnapshot()+"."));
   source.decrease(line.getRequestedQuantity());
   InventoryCostingService.Valuation valuation=costing.outbound(line.getProduct(),source.getLocation(),line.getRequestedQuantity());
   StockMovement movement=new StockMovement(line.getProduct(),transfer.getSourceWarehouse(),MovementType.TRANSFER_OUT,
    line.getRequestedQuantity(),valuation.movementUnitCost(),"STOCK_TRANSFER",transfer.getId(),"TRFO-"+line.getId(),
    hash(transfer.getId()+"|OUT|"+line.getId()),"Despacho de transferencia "+transfer.getTransferNumber(),actor);
   movement.assignLocations(source.getLocation(),null,measureByProduct.get(line.getProduct().getId()),transfer.getTransferNumber(),line.getId(),valuation.method());
   costing.post(movements.save(movement),valuation);
  }
  transfer.dispatch();
  audit.record(actor,"STOCK_TRANSFER_DISPATCHED","STOCK_TRANSFER",id,Map.of("transferNumber",transfer.getTransferNumber(),"lines",lines.size()));
  event(transfer,"STOCK_TRANSFER_DISPATCHED",actor,Map.of("transferNumber",transfer.getTransferNumber(),"lines",lines.size()));
  complete(idem,transfer,200);
  return TransferDtos.View.from(requireDetailed(id));
 }

 @Transactional
 public TransferDtos.View receive(UUID id,String rawKey,String actor){
  String key=normalizeKey(rawKey);IdempotencyRecord idem=claim("STOCK_TRANSFER_RECEIVE",key,hash("STOCK_TRANSFER_RECEIVE|"+id));
  if(idem.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED)return replay(idem);
  StockTransfer transfer=requireForUpdate(id);
  if(transfer.getStatus()==TransferStatus.COMPLETED||transfer.getStatus()==TransferStatus.REVERSED){
   complete(idem,transfer,200);return TransferDtos.View.from(requireDetailed(id));
  }
  if(transfer.getStatus()!=TransferStatus.IN_TRANSIT)throw new ConflictException("Solo se puede recibir una transferencia que esté en tránsito.");
  List<StockTransferLine> lines=orderedLines(transfer);Map<UUID,UnitOfMeasure> measureByProduct=loadUnits(lines);
  for(StockTransferLine line:lines){
   StockBalance destination=balances.findForUpdate(line.getProduct().getId(),transfer.getDestinationWarehouse().getId()).orElse(null);
   if(destination==null){
    WarehouseLocation main=mainLocation(transfer.getDestinationWarehouse());
    balances.insertIfAbsent(UUID.randomUUID(),line.getProduct().getId(),transfer.getDestinationWarehouse().getId(),main.getId());
    destination=balances.findForUpdate(line.getProduct().getId(),transfer.getDestinationWarehouse().getId())
     .orElseThrow(()->new ConflictException("No se pudo crear el saldo de destino de forma concurrente."));
   }
   destination.increase(line.getRequestedQuantity());line.receive();
   // The destination receives the goods at the exact cost they left the source warehouse with.
   InventoryCostingService.Valuation valuation=costing.inbound(line.getProduct(),destination.getLocation(),line.getRequestedQuantity(),dispatchedUnitCost(line));
   StockMovement movement=new StockMovement(line.getProduct(),transfer.getDestinationWarehouse(),MovementType.TRANSFER_IN,
    line.getRequestedQuantity(),valuation.movementUnitCost(),"STOCK_TRANSFER",transfer.getId(),"TRFI-"+line.getId(),
    hash(transfer.getId()+"|IN|"+line.getId()),"Recepción de transferencia "+transfer.getTransferNumber(),actor);
   movement.assignLocations(null,destination.getLocation(),measureByProduct.get(line.getProduct().getId()),transfer.getTransferNumber(),line.getId(),valuation.method());
   costing.post(movements.save(movement),valuation);
  }
  transfer.complete();
  audit.record(actor,"STOCK_TRANSFER_RECEIVED","STOCK_TRANSFER",id,Map.of("transferNumber",transfer.getTransferNumber(),"lines",lines.size()));
  event(transfer,"STOCK_TRANSFER_RECEIVED",actor,Map.of("transferNumber",transfer.getTransferNumber(),"lines",lines.size()));
  complete(idem,transfer,200);
  return TransferDtos.View.from(requireDetailed(id));
 }

 @Transactional
 public TransferDtos.View cancel(UUID id,String rawKey,String reason,String actor){
  String key=normalizeKey(rawKey);String clean=requiredReason(reason);
  IdempotencyRecord idem=claim("STOCK_TRANSFER_CANCEL",key,hash("STOCK_TRANSFER_CANCEL|"+id+"|"+clean));
  if(idem.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED)return replay(idem);
  StockTransfer transfer=requireForUpdate(id);
  if(transfer.getStatus()==TransferStatus.CANCELLED){complete(idem,transfer,200);return TransferDtos.View.from(requireDetailed(id));}
  if(transfer.getStatus()!=TransferStatus.DRAFT&&transfer.getStatus()!=TransferStatus.IN_TRANSIT)
   throw new ConflictException("Solo se puede cancelar una transferencia en borrador o en tránsito; las recibidas requieren reversión.");
  List<StockTransferLine> lines=orderedLines(transfer);
  if(transfer.getStatus()==TransferStatus.IN_TRANSIT){
   Map<UUID,UnitOfMeasure> measureByProduct=loadUnits(lines);
   for(StockTransferLine line:lines){
    StockBalance source=balances.findForUpdate(line.getProduct().getId(),transfer.getSourceWarehouse().getId())
     .orElseThrow(()->new BusinessRuleException("No existe saldo de origen para compensar "+line.getSkuSnapshot()+"."));
    source.increase(line.getRequestedQuantity());
    InventoryCostingService.Valuation valuation=costing.inbound(line.getProduct(),source.getLocation(),line.getRequestedQuantity(),dispatchedUnitCost(line));
    StockMovement movement=new StockMovement(line.getProduct(),transfer.getSourceWarehouse(),MovementType.TRANSFER_IN,
     line.getRequestedQuantity(),valuation.movementUnitCost(),"STOCK_TRANSFER",transfer.getId(),"TRFC-IN-"+line.getId(),
     hash(transfer.getId()+"|CANCEL_IN_TRANSIT|"+line.getId()),"Compensación de cancelación de transferencia "+transfer.getTransferNumber()+": "+clean,actor);
    movement.assignLocations(null,source.getLocation(),measureByProduct.get(line.getProduct().getId()),transfer.getTransferNumber(),line.getId(),valuation.method());
    costing.post(movements.save(movement),valuation);
   }
  }
  transfer.cancel(clean,actor);
  audit.record(actor,"STOCK_TRANSFER_CANCELLED","STOCK_TRANSFER",id,Map.of("transferNumber",transfer.getTransferNumber(),"reason",clean,"compensatedDispatch",transfer.getDispatchedAt()!=null));
  event(transfer,"STOCK_TRANSFER_CANCELLED",actor,Map.of("transferNumber",transfer.getTransferNumber(),"reason",clean,"compensatedDispatch",transfer.getDispatchedAt()!=null));
  complete(idem,transfer,200);
  return TransferDtos.View.from(requireDetailed(id));
 }

 @Transactional
 public TransferDtos.View reverse(UUID id,String rawKey,String reason,String actor){
  String key=normalizeKey(rawKey);String clean=requiredReason(reason);
  IdempotencyRecord idem=claim("STOCK_TRANSFER_REVERSE",key,hash("STOCK_TRANSFER_REVERSE|"+id+"|"+clean));
  if(idem.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED)return replay(idem);
  StockTransfer transfer=requireForUpdate(id);
  if(transfer.getStatus()==TransferStatus.REVERSED){complete(idem,transfer,200);return TransferDtos.View.from(requireDetailed(id));}
  if(transfer.getStatus()!=TransferStatus.COMPLETED)throw new ConflictException("Solo se puede revertir una transferencia completada.");
  List<StockTransferLine> lines=orderedLines(transfer);Map<UUID,UnitOfMeasure> measureByProduct=loadUnits(lines);
  for(StockTransferLine line:lines){
   // Lock both warehouse balances in the same UUID order for every reversal to prevent deadlocks.
   UUID first=transfer.getSourceWarehouse().getId().toString().compareTo(transfer.getDestinationWarehouse().getId().toString())<0
    ?transfer.getSourceWarehouse().getId():transfer.getDestinationWarehouse().getId();
   UUID second=first.equals(transfer.getSourceWarehouse().getId())?transfer.getDestinationWarehouse().getId():transfer.getSourceWarehouse().getId();
   StockBalance firstBalance=lockBalance(line,first,transfer);
   StockBalance secondBalance=lockBalance(line,second,transfer);
   StockBalance source=first.equals(transfer.getSourceWarehouse().getId())?firstBalance:secondBalance;
   StockBalance destination=first.equals(transfer.getDestinationWarehouse().getId())?firstBalance:secondBalance;
   destination.decrease(line.getReceivedQuantity());
   source.increase(line.getReceivedQuantity());
   InventoryCostingService.Valuation outValuation=costing.outbound(line.getProduct(),destination.getLocation(),line.getReceivedQuantity());
   StockMovement out=new StockMovement(line.getProduct(),transfer.getDestinationWarehouse(),MovementType.TRANSFER_OUT,
    line.getReceivedQuantity(),outValuation.movementUnitCost(),"STOCK_TRANSFER_REVERSAL",transfer.getId(),"TRFR-OUT-"+line.getId(),
    hash(transfer.getId()+"|REVERSE_OUT|"+line.getId()),"Salida compensatoria de reversión "+transfer.getTransferNumber()+": "+clean,actor);
   out.assignLocations(destination.getLocation(),null,measureByProduct.get(line.getProduct().getId()),transfer.getTransferNumber(),line.getId(),outValuation.method());
   costing.post(movements.save(out),outValuation);
   InventoryCostingService.Valuation inValuation=costing.inbound(line.getProduct(),source.getLocation(),line.getReceivedQuantity(),outValuation.unitCost());
   StockMovement in=new StockMovement(line.getProduct(),transfer.getSourceWarehouse(),MovementType.TRANSFER_IN,
    line.getReceivedQuantity(),inValuation.movementUnitCost(),"STOCK_TRANSFER_REVERSAL",transfer.getId(),"TRFR-IN-"+line.getId(),
    hash(transfer.getId()+"|REVERSE_IN|"+line.getId()),"Entrada compensatoria de reversión "+transfer.getTransferNumber()+": "+clean,actor);
   in.assignLocations(null,source.getLocation(),measureByProduct.get(line.getProduct().getId()),transfer.getTransferNumber(),line.getId(),inValuation.method());
   costing.post(movements.save(in),inValuation);
  }
  transfer.reverse(clean,actor);
  audit.record(actor,"STOCK_TRANSFER_REVERSED","STOCK_TRANSFER",id,Map.of("transferNumber",transfer.getTransferNumber(),"reason",clean,"lines",lines.size()));
  event(transfer,"STOCK_TRANSFER_REVERSED",actor,Map.of("transferNumber",transfer.getTransferNumber(),"reason",clean,"lines",lines.size()));
  complete(idem,transfer,200);
  return TransferDtos.View.from(requireDetailed(id));
 }

 private java.math.BigDecimal dispatchedUnitCost(StockTransferLine line){
  return movements.findByIdempotencyKey("TRFO-"+line.getId()).map(m->costing.valuedUnitCost(m.getId())).filter(Objects::nonNull)
   .orElse(line.getUnitCost());
 }
 private StockBalance lockBalance(StockTransferLine line,UUID warehouseId,StockTransfer transfer){
  StockBalance balance=balances.findForUpdate(line.getProduct().getId(),warehouseId).orElse(null);
  if(balance!=null)return balance;
  if(!warehouseId.equals(transfer.getSourceWarehouse().getId()))throw new BusinessRuleException("No existe saldo destino para revertir "+line.getSkuSnapshot()+".");
  throw new BusinessRuleException("No existe saldo de origen para revertir "+line.getSkuSnapshot()+".");
 }
 private WarehouseLocation mainLocation(Warehouse warehouse){
  locations.insertIfAbsent(UUID.randomUUID(),warehouse.getId(),"MAIN","Ubicación principal","STORAGE");
  return locations.findByWarehouseIdAndCode(warehouse.getId(),"MAIN")
   .orElseThrow(()->new ConflictException("No se pudo asegurar la ubicación principal del almacén."));
 }
 private Map<UUID,UnitOfMeasure> loadUnits(List<StockTransferLine> lines){
  Map<UUID,UnitOfMeasure> result=new HashMap<>();
  for(StockTransferLine line:lines)result.computeIfAbsent(line.getProduct().getId(),id->units.findById(line.getProduct().getUnitOfMeasureId())
   .orElseThrow(()->new ResourceNotFoundException("Unidad de medida",line.getProduct().getUnitOfMeasureId())));
  return result;
 }
 private List<StockTransferLine> orderedLines(StockTransfer transfer){
  List<StockTransferLine> lines=new ArrayList<>(transfer.getLines());lines.sort(Comparator.comparing(l->l.getProduct().getId().toString()));return lines;
 }
 private StockTransfer requireForUpdate(UUID id){return transfers.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Transferencia",id));}
 private StockTransfer requireDetailed(UUID id){return transfers.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Transferencia",id));}
 private String normalizeKey(String key){
  if(key==null||key.isBlank()||key.trim().length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");
  return key.trim();
 }
 private String requiredReason(String reason){
  if(reason==null||reason.isBlank()||reason.trim().length()>1000)throw new BusinessRuleException("Debe indicar un motivo de hasta 1000 caracteres.");
  return reason.trim();
 }
 private IdempotencyRecord claim(String scope,String key,String requestHash){
  idempotencyRecords.insertIfAbsent(UUID.randomUUID(),scope,key,requestHash);
  IdempotencyRecord record=idempotencyRecords.findForUpdate(scope,key).orElseThrow(()->new ConflictException("No se pudo reservar la clave de idempotencia."));
  if(!requestHash.equals(record.getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
  if(record.getStatus()==IdempotencyRecord.RecordStatus.FAILED)throw new ConflictException("La operación asociada a esta clave falló; use una nueva clave para reintentar.");
  if(record.getStatus()==IdempotencyRecord.RecordStatus.IN_PROGRESS&&record.getResourceId()!=null)throw new ConflictException("La operación aún no puede reproducirse.");
  return record;
 }
 private TransferDtos.View replay(IdempotencyRecord record){
  if(record.getStatus()!=IdempotencyRecord.RecordStatus.COMPLETED||record.getResourceId()==null)throw new ConflictException("La clave de idempotencia aún no tiene un resultado reproducible.");
  return TransferDtos.View.from(requireDetailed(record.getResourceId()));
 }
 private void complete(IdempotencyRecord record,StockTransfer transfer,int status){
  record.markCompleted("STOCK_TRANSFER",transfer.getId(),(short)status);idempotencyRecords.saveAndFlush(record);
 }
 private void event(StockTransfer transfer,String type,String actor,Map<String,Object> payload){
  Map<String,Object> data=new HashMap<>(payload);data.put("transferId",transfer.getId().toString());data.put("actor",actor);
  outbox.save(new OutboxEvent("STOCK_TRANSFER",transfer.getId(),type,data));
 }
 private String canonicalRequest(TransferDtos.CreateRequest r){
  List<String> lines=r.items().stream().map(x->x.productId()+"|"+x.quantity().setScale(3,RoundingMode.UNNECESSARY).toPlainString()).sorted().toList();
  return r.sourceWarehouseId()+"|"+r.destinationWarehouseId()+"|"+String.join(";",lines);
 }
 private String mapperValue(TransferDtos.CreateRequest r){try{return mapper.writeValueAsString(r);}catch(JsonProcessingException ex){throw new BusinessRuleException("No se pudo validar la solicitud.");}}
 private String hash(String value){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception ex){throw new IllegalStateException("No se pudo calcular el hash de la operación.",ex);}
 }
}
