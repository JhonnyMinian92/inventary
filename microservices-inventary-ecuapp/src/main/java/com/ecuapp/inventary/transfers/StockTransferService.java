package com.ecuapp.inventary.transfers;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
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
 private final StockTransferRepository transfers;private final ProductRepository products;private final WarehouseRepository warehouses;
 private final StockBalanceRepository balances;private final WarehouseLocationRepository locations;private final StockMovementRepository movements;private final ObjectMapper mapper;private final AuditService audit;
 public StockTransferService(StockTransferRepository transfers,ProductRepository products,WarehouseRepository warehouses,
  StockBalanceRepository balances,WarehouseLocationRepository locations,StockMovementRepository movements,ObjectMapper mapper,AuditService audit){
  this.transfers=transfers;this.products=products;this.warehouses=warehouses;this.balances=balances;this.locations=locations;this.movements=movements;this.mapper=mapper;this.audit=audit;
 }
 @Transactional(readOnly=true) public Page<TransferDtos.View> list(Pageable p){return transfers.findAll(PageRequest.of(p.getPageNumber(),Math.min(p.getPageSize(),100),p.getSort())).map(TransferDtos.View::from);}
 @Transactional(readOnly=true) public TransferDtos.View get(UUID id){return TransferDtos.View.from(transfers.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Transferencia",id)));}
 @Transactional public TransferDtos.View create(String key,TransferDtos.CreateRequest request,String actor){
  if(key==null||key.isBlank()||key.length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio.");
  String hash=hash(mapperValue(request));Optional<StockTransfer> previous=transfers.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!hash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
   return TransferDtos.View.from(transfers.findDetailedById(previous.get().getId()).orElseThrow());
  }
  if(request.sourceWarehouseId().equals(request.destinationWarehouseId()))throw new BusinessRuleException("El almacén de origen y destino deben ser diferentes.");
  Warehouse source=warehouses.findById(request.sourceWarehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén origen",request.sourceWarehouseId()));
  Warehouse dest=warehouses.findById(request.destinationWarehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén destino",request.destinationWarehouseId()));
  if(!source.isActive()||!dest.isActive())throw new BusinessRuleException("Ambos almacenes deben estar activos.");
  Set<UUID> seen=new HashSet<>();List<StockTransferLine> lines=new ArrayList<>();
  for(TransferDtos.LineRequest line:request.items()){
   if(!seen.add(line.productId()))throw new BusinessRuleException("No repita un producto en más de una línea de transferencia.");
   Product product=products.findById(line.productId()).orElseThrow(()->new ResourceNotFoundException("Producto",line.productId()));
   if(!product.isActive())throw new BusinessRuleException("No se puede transferir un producto inactivo.");
   BigDecimal qty=line.quantity().setScale(3,RoundingMode.UNNECESSARY);
   lines.add(new StockTransferLine(product,qty,product.getCostPrice()));
  }
  String number="TRF-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  StockTransfer transfer=transfers.saveAndFlush(new StockTransfer(number,source,dest,key,hash,actor));
  lines.forEach(transfer::addLine);transfers.save(transfer);
  audit.record(actor,"STOCK_TRANSFER_CREATED","STOCK_TRANSFER",transfer.getId(),Map.of("transferNumber",number,"lineCount",lines.size()));
  return TransferDtos.View.from(transfers.findDetailedById(transfer.getId()).orElseThrow());
 }
 @Transactional public TransferDtos.View dispatch(UUID id,String actor){
  StockTransfer transfer=requireForUpdate(id);
  if(transfer.getStatus()==TransferStatus.IN_TRANSIT||transfer.getStatus()==TransferStatus.COMPLETED)return TransferDtos.View.from(transfers.findDetailedById(id).orElseThrow());
  if(transfer.getStatus()!=TransferStatus.DRAFT)throw new ConflictException("La transferencia no puede despacharse en su estado actual.");
  List<StockTransferLine> lines=new ArrayList<>(transfer.getLines());lines.sort(Comparator.comparing(l->l.getProduct().getId().toString()));
  for(StockTransferLine line:lines){
   StockBalance source=balances.findForUpdate(line.getProduct().getId(),transfer.getSourceWarehouse().getId()).orElseThrow(()->new BusinessRuleException("No existe saldo de origen para "+line.getSkuSnapshot()));
   source.decrease(line.getRequestedQuantity());
   String movementKey="TRFO-"+line.getId();
   movements.save(new StockMovement(line.getProduct(),transfer.getSourceWarehouse(),MovementType.TRANSFER_OUT,line.getRequestedQuantity(),line.getUnitCost(),
    "STOCK_TRANSFER",transfer.getId(),movementKey,hash(transfer.getId()+"|OUT|"+line.getId()),"Despacho de transferencia "+transfer.getTransferNumber(),actor));
  }
  transfer.dispatch();
  audit.record(actor,"STOCK_TRANSFER_DISPATCHED","STOCK_TRANSFER",id,Map.of("transferNumber",transfer.getTransferNumber(),"lines",lines.size()));
  return TransferDtos.View.from(transfers.findDetailedById(id).orElseThrow());
 }
 @Transactional public TransferDtos.View receive(UUID id,String actor){
  StockTransfer transfer=requireForUpdate(id);
  if(transfer.getStatus()==TransferStatus.COMPLETED)return TransferDtos.View.from(transfers.findDetailedById(id).orElseThrow());
  if(transfer.getStatus()!=TransferStatus.IN_TRANSIT)throw new ConflictException("Solo se puede recibir una transferencia que esté en tránsito.");
  List<StockTransferLine> lines=new ArrayList<>(transfer.getLines());lines.sort(Comparator.comparing(l->l.getProduct().getId().toString()));
  for(StockTransferLine line:lines){
   StockBalance dest=balances.findForUpdate(line.getProduct().getId(),transfer.getDestinationWarehouse().getId()).orElse(null);
   if(dest==null){
    WarehouseLocation mainLocation=locations.findByWarehouseIdAndCode(transfer.getDestinationWarehouse().getId(),"MAIN").orElseGet(()->locations.save(new WarehouseLocation(transfer.getDestinationWarehouse(),"MAIN","Ubicación principal",WarehouseLocation.LocationType.STORAGE)));
    dest=balances.save(new StockBalance(line.getProduct(),transfer.getDestinationWarehouse(),mainLocation));
   }
   dest.increase(line.getRequestedQuantity());
   line.receive();
   movements.save(new StockMovement(line.getProduct(),transfer.getDestinationWarehouse(),MovementType.TRANSFER_IN,line.getRequestedQuantity(),line.getUnitCost(),
    "STOCK_TRANSFER",transfer.getId(),"TRFI-"+line.getId(),hash(transfer.getId()+"|IN|"+line.getId()),"Recepción de transferencia "+transfer.getTransferNumber(),actor));
  }
  transfer.complete();
  audit.record(actor,"STOCK_TRANSFER_RECEIVED","STOCK_TRANSFER",id,Map.of("transferNumber",transfer.getTransferNumber(),"lines",lines.size()));
  return TransferDtos.View.from(transfers.findDetailedById(id).orElseThrow());
 }
 @Transactional public TransferDtos.View cancel(UUID id,String actor){
  StockTransfer transfer=requireForUpdate(id);
  if(transfer.getStatus()==TransferStatus.CANCELLED)return TransferDtos.View.from(transfers.findDetailedById(id).orElseThrow());
  transfer.cancel();
  audit.record(actor,"STOCK_TRANSFER_CANCELLED","STOCK_TRANSFER",id,Map.of("transferNumber",transfer.getTransferNumber()));
  return TransferDtos.View.from(transfers.findDetailedById(id).orElseThrow());
 }
 private StockTransfer requireForUpdate(UUID id){return transfers.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Transferencia",id));}
 private String mapperValue(TransferDtos.CreateRequest r){try{return mapper.writeValueAsString(r);}catch(JsonProcessingException ex){throw new BusinessRuleException("No se pudo validar la solicitud.");}}
 private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
