package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.UnitOfMeasure;
import com.ecuapp.inventary.catalog.UnitOfMeasureRepository;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.sales.OutboxEvent;
import com.ecuapp.inventary.sales.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PurchaseReturnService {
 private static final Set<String> SORT=Set.of("createdAt","returnNumber");
 private final PurchaseReturnRepository returns;private final PurchaseOrderRepository orders;private final GoodsReceiptRepository receipts;
 private final GoodsReceiptLineRepository receiptLines;private final StockBalanceRepository balances;private final StockMovementRepository movements;
 private final OutboxEventRepository outbox;private final ObjectMapper mapper;private final AuditService audit;private final IdempotencyRecordRepository idempotencyRecords;private final UnitOfMeasureRepository units;
 private final InventoryCostingService costing;
 public PurchaseReturnService(PurchaseReturnRepository returns,PurchaseOrderRepository orders,GoodsReceiptRepository receipts,GoodsReceiptLineRepository receiptLines,
  StockBalanceRepository balances,StockMovementRepository movements,OutboxEventRepository outbox,ObjectMapper mapper,AuditService audit,IdempotencyRecordRepository idempotencyRecords,UnitOfMeasureRepository units,
  InventoryCostingService costing){
  this.returns=returns;this.orders=orders;this.receipts=receipts;this.receiptLines=receiptLines;this.balances=balances;this.movements=movements;this.outbox=outbox;this.mapper=mapper;this.audit=audit;this.idempotencyRecords=idempotencyRecords;this.units=units;this.costing=costing;
 }
 @Transactional(readOnly=true) public PurchaseReturnDtos.View get(UUID id){return PurchaseReturnDtos.View.from(returns.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Devolución a proveedor",id)));}
 @Transactional(readOnly=true) public Page<PurchaseReturnDtos.View> list(UUID goodsReceiptId,UUID warehouseId,Instant from,Instant to,Pageable p){
  if(from!=null&&to!=null&&!from.isBefore(to))throw new InvalidQueryException("'from' debe ser anterior a 'to'.");
  return returns.search(goodsReceiptId,warehouseId,com.ecuapp.inventary.common.TimeRange.lower(from),com.ecuapp.inventary.common.TimeRange.upper(to),Pages.of(p,SORT,Sort.by(Sort.Direction.DESC,"createdAt"))).map(PurchaseReturnDtos.View::from);
 }
 @Transactional public PurchaseReturnDtos.View create(String key,PurchaseReturnDtos.CreateRequest request,String actor){
  String normalizedKey=normalizeKey(key);String hash=canonicalHash(request);
  int claimed=idempotencyRecords.insertIfAbsent(UUID.randomUUID(),"PURCHASE_RETURN_CREATE",normalizedKey,hash);
  IdempotencyRecord idempotency=idempotencyRecords.findForUpdate("PURCHASE_RETURN_CREATE",normalizedKey).orElseThrow(()->new IllegalStateException("No se pudo reservar la clave de idempotencia."));
  if(!hash.equals(idempotency.getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra devolución.");
  if(claimed==0){if(idempotency.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED&&idempotency.getResourceId()!=null)return PurchaseReturnDtos.View.from(returns.findDetailedById(idempotency.getResourceId()).orElseThrow(()->new ConflictException("La devolución idempotente ya no existe.")));throw new ConflictException("Ya existe una operación con esta clave de idempotencia y aún no puede reproducirse.");}
  Optional<PurchaseReturn> previous=returns.findByIdempotencyKey(normalizedKey);
  if(previous.isPresent()){
   if(!hash.equals(previous.get().getRequestHash())&&!legacyHash(request).equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra devolución.");
   idempotency.markCompleted("PURCHASE_RETURN",previous.get().getId(),(short)201);idempotencyRecords.saveAndFlush(idempotency);
   return PurchaseReturnDtos.View.from(returns.findDetailedById(previous.get().getId()).orElseThrow());
  }
  GoodsReceipt receipt=receipts.findDetailedById(request.goodsReceiptId()).orElseThrow(()->new ResourceNotFoundException("Recepción",request.goodsReceiptId()));
  if(!"POSTED".equals(receipt.getStatus()))throw new BusinessRuleException("Solo se puede devolver mercadería de una recepción contabilizada.");
  // The purchase order row serializes returns and receipts of the same purchase.
  PurchaseOrder order=orders.findForUpdate(receipt.getPurchaseOrder().getId()).orElseThrow();
  Set<UUID> seen=new HashSet<>();List<ReturnCalc> calculated=new ArrayList<>();
  for(PurchaseReturnDtos.LineRequest item:request.items()){
   if(!seen.add(item.receiptLineId()))throw new BusinessRuleException("No repita la misma línea de recepción.");
   GoodsReceiptLine source=receiptLines.findByIdAndReceiptId(item.receiptLineId(),receipt.getId()).orElseThrow(()->new BusinessRuleException("La línea no pertenece a la recepción indicada."));
   BigDecimal quantity=item.quantity().setScale(3,RoundingMode.UNNECESSARY);
   BigDecimal alreadyReturned=receiptLines.sumReturned(source.getId());
   if(alreadyReturned.add(quantity).compareTo(source.getQuantity())>0)throw new BusinessRuleException("La devolución supera la cantidad aceptada en la recepción.");
   calculated.add(new ReturnCalc(source,quantity));
  }
  calculated.sort(Comparator.comparing(c->c.source().getProduct().getId().toString()));
  Map<UUID,StockBalance> locked=new HashMap<>();
  for(ReturnCalc c:calculated){
   StockBalance balance=balances.findForUpdate(c.source().getProduct().getId(),receipt.getWarehouse().getId()).orElseThrow(()->new BusinessRuleException("No existe saldo para devolver "+c.source().getProduct().getSku()));
   balance.decrease(c.quantity());locked.put(c.source().getId(),balance);
  }
  String number="DEV-C-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  PurchaseReturn purchaseReturn=returns.saveAndFlush(new PurchaseReturn(number,order,receipt,receipt.getWarehouse(),request.reason().trim(),normalizedKey,hash,actor));
  for(ReturnCalc c:calculated){
   PurchaseReturnLine returnLine=new PurchaseReturnLine(c.source(),c.quantity());purchaseReturn.addLine(returnLine);returns.saveAndFlush(purchaseReturn);
   UnitOfMeasure unit=units.findById(c.source().getProduct().getUnitOfMeasureId()).orElseThrow(()->new BusinessRuleException("No existe unidad de medida para "+c.source().getProduct().getSku()+"."));
   StockBalance balance=locked.get(c.source().getId());
   // The goods leave at the cost they entered with: the receipt layer is consumed first.
   UUID receiptMovementId=movements.findFirstByReferenceTypeAndReferenceIdAndSourceLineIdAndMovementType("GOODS_RECEIPT",receipt.getId(),c.source().getId(),MovementType.RECEIPT)
    .map(StockMovement::getId).orElse(null);
   InventoryCostingService.Valuation valuation=costing.outbound(c.source().getProduct(),balance.getLocation(),c.quantity(),receiptMovementId,c.source().getUnitCost());
   String movementKey="PRTN-"+purchaseReturn.getId()+"-"+c.source().getId();
   StockMovement movement=new StockMovement(c.source().getProduct(),receipt.getWarehouse(),MovementType.RETURN_OUT,c.quantity(),valuation.movementUnitCost(),"PURCHASE_RETURN",purchaseReturn.getId(),movementKey,hash(purchaseReturn.getId()+"|"+returnLine.getId()+"|"+c.quantity().toPlainString()),"Devolución al proveedor "+number,actor);
   movement.assignLocations(balance.getLocation(),null,unit,number,c.source().getId(),valuation.method());
   costing.post(movements.save(movement),valuation);
  }
  returns.save(purchaseReturn);
  outbox.save(new OutboxEvent("PURCHASE_RETURN",purchaseReturn.getId(),"PURCHASE_RETURN_POSTED",Map.of("returnId",purchaseReturn.getId().toString(),"returnNumber",number,"receiptId",receipt.getId().toString())));
  audit.record(actor,"PURCHASE_RETURN_POSTED","PURCHASE_RETURN",purchaseReturn.getId(),Map.of("returnNumber",number,"receiptId",receipt.getId().toString(),"lines",calculated.size()));
  idempotency.markCompleted("PURCHASE_RETURN",purchaseReturn.getId(),(short)201);idempotencyRecords.saveAndFlush(idempotency);
  return PurchaseReturnDtos.View.from(returns.findDetailedById(purchaseReturn.getId()).orElseThrow());
 }
 private record ReturnCalc(GoodsReceiptLine source,BigDecimal quantity){}
 private String canonicalHash(PurchaseReturnDtos.CreateRequest r){List<String> lines=r.items().stream().map(x->x.receiptLineId()+"|"+x.quantity().setScale(3,RoundingMode.UNNECESSARY).toPlainString()).sorted().toList();return hash(r.goodsReceiptId()+"|"+clean(r.reason())+"|"+String.join(";",lines));}
 private String legacyHash(PurchaseReturnDtos.CreateRequest r){return hash(r.goodsReceiptId()+"|"+json(r));}
 private String normalizeKey(String key){if(key==null||key.isBlank()||key.trim().length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");return key.trim();}
 private String clean(String v){return v==null||v.isBlank()?"":v.trim();}
 private String json(PurchaseReturnDtos.CreateRequest r){try{return mapper.writeValueAsString(r);}catch(JsonProcessingException ex){throw new BusinessRuleException("No se pudo validar la devolución.");}}
 private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
