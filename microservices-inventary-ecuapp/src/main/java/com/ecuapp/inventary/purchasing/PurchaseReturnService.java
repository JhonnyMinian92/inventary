package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.sales.OutboxEvent;
import com.ecuapp.inventary.sales.OutboxEventRepository;
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

@Service
public class PurchaseReturnService {
 private final PurchaseReturnRepository returns;private final PurchaseOrderRepository orders;private final GoodsReceiptRepository receipts;
 private final GoodsReceiptLineRepository receiptLines;private final StockBalanceRepository balances;private final StockMovementRepository movements;
 private final OutboxEventRepository outbox;private final ObjectMapper mapper;private final AuditService audit;
 public PurchaseReturnService(PurchaseReturnRepository returns,PurchaseOrderRepository orders,GoodsReceiptRepository receipts,GoodsReceiptLineRepository receiptLines,
  StockBalanceRepository balances,StockMovementRepository movements,OutboxEventRepository outbox,ObjectMapper mapper,AuditService audit){
  this.returns=returns;this.orders=orders;this.receipts=receipts;this.receiptLines=receiptLines;this.balances=balances;this.movements=movements;this.outbox=outbox;this.mapper=mapper;this.audit=audit;
 }
 @Transactional(readOnly=true) public PurchaseReturnDtos.View get(UUID id){return PurchaseReturnDtos.View.from(returns.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Devolución a proveedor",id)));}
 @Transactional public PurchaseReturnDtos.View create(String key,PurchaseReturnDtos.CreateRequest request,String actor){
  if(key==null||key.isBlank()||key.length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio.");
  String hash=hash(request.goodsReceiptId()+"|"+json(request));
  Optional<PurchaseReturn> previous=returns.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!hash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra devolución.");
   return PurchaseReturnDtos.View.from(returns.findDetailedById(previous.get().getId()).orElseThrow());
  }
  GoodsReceipt receipt=receipts.findDetailedById(request.goodsReceiptId()).orElseThrow(()->new ResourceNotFoundException("Recepción",request.goodsReceiptId()));
  if(!"POSTED".equals(receipt.getStatus()))throw new BusinessRuleException("Solo se puede devolver mercadería de una recepción contabilizada.");
  // La fila de la orden serializa las devoluciones y recepciones relativas a la misma compra.
  PurchaseOrder order=orders.findForUpdate(receipt.getPurchaseOrder().getId()).orElseThrow();
  Set<UUID> seen=new HashSet<>();List<ReturnCalc> calculated=new ArrayList<>();
  for(PurchaseReturnDtos.LineRequest item:request.items()){
   if(!seen.add(item.receiptLineId()))throw new BusinessRuleException("No repita la misma línea de recepción.");
   GoodsReceiptLine source=receiptLines.findByIdAndReceiptId(item.receiptLineId(),receipt.getId()).orElseThrow(()->new BusinessRuleException("La línea no pertenece a la recepción indicada."));
   BigDecimal quantity=item.quantity().setScale(3,RoundingMode.UNNECESSARY);
   BigDecimal alreadyReturned=receiptLines.sumReturned(source.getId());
   if(alreadyReturned.add(quantity).compareTo(source.getQuantity())>0)throw new BusinessRuleException("La devolución supera la cantidad aceptada en la recepción.");
   StockBalance balance=balances.findForUpdate(source.getProduct().getId(),receipt.getWarehouse().getId()).orElseThrow(()->new BusinessRuleException("No existe saldo para devolver "+source.getProduct().getSku()));
   balance.decrease(quantity);
   calculated.add(new ReturnCalc(source,quantity));
  }
  String number="DEV-C-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  PurchaseReturn purchaseReturn=returns.saveAndFlush(new PurchaseReturn(number,order,receipt,receipt.getWarehouse(),request.reason().trim(),key,hash,actor));
  for(ReturnCalc c:calculated){
   purchaseReturn.addLine(new PurchaseReturnLine(c.source(),c.quantity()));
   String movementKey="PRTN-"+purchaseReturn.getId()+"-"+c.source().getId();
   movements.save(new StockMovement(c.source().getProduct(),receipt.getWarehouse(),MovementType.RETURN_OUT,c.quantity(),c.source().getUnitCost(),"PURCHASE_RETURN",purchaseReturn.getId(),movementKey,
    hash(purchaseReturn.getId()+"|"+c.source().getId()+"|"+c.quantity().toPlainString()),"Devolución al proveedor "+number,actor));
  }
  returns.save(purchaseReturn);
  outbox.save(new OutboxEvent("PURCHASE_RETURN",purchaseReturn.getId(),"PURCHASE_RETURN_POSTED",Map.of("returnId",purchaseReturn.getId().toString(),"returnNumber",number,"receiptId",receipt.getId().toString())));
  audit.record(actor,"PURCHASE_RETURN_POSTED","PURCHASE_RETURN",purchaseReturn.getId(),Map.of("returnNumber",number,"receiptId",receipt.getId().toString(),"lines",calculated.size()));
  return PurchaseReturnDtos.View.from(returns.findDetailedById(purchaseReturn.getId()).orElseThrow());
 }
 private record ReturnCalc(GoodsReceiptLine source,BigDecimal quantity){}
 private String json(PurchaseReturnDtos.CreateRequest r){try{return mapper.writeValueAsString(r);}catch(JsonProcessingException ex){throw new BusinessRuleException("No se pudo validar la devolución.");}}
 private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
