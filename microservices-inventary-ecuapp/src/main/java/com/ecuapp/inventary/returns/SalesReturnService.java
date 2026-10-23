package com.ecuapp.inventary.returns;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.sales.*;
import com.ecuapp.inventary.warehouse.Warehouse;
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
 private final StockBalanceRepository balances;private final StockMovementRepository movements;private final OutboxEventRepository outbox;
 private final ObjectMapper mapper;private final AuditService audit;
 public SalesReturnService(SaleRepository sales,SalesReturnRepository returns,SalesReturnLineRepository returnLines,StockBalanceRepository balances,
   StockMovementRepository movements,OutboxEventRepository outbox,ObjectMapper mapper,AuditService audit){
  this.sales=sales;this.returns=returns;this.returnLines=returnLines;this.balances=balances;this.movements=movements;this.outbox=outbox;this.mapper=mapper;this.audit=audit;
 }
 @Transactional(readOnly=true) public SalesReturnDtos.View get(UUID id){return SalesReturnDtos.View.from(returns.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Devolución de venta",id)));}
 @Transactional public SalesReturnDtos.View create(String key,SalesReturnDtos.CreateRequest request,String actor){
  if(key==null||key.isBlank()||key.length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio.");
  String hash=hash(request.sourceSaleId()+"|"+json(request));
  Optional<SalesReturn> previous=returns.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!hash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra solicitud.");
   return SalesReturnDtos.View.from(returns.findDetailedById(previous.get().getId()).orElseThrow());
  }
  Sale sale=sales.findForUpdateById(request.sourceSaleId()).orElseThrow(()->new ResourceNotFoundException("Venta",request.sourceSaleId()));
  if(sale.getStatus()!=SaleStatus.COMPLETED)throw new BusinessRuleException("Solo se pueden devolver ventas completadas.");
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
  Warehouse warehouse=sale.getWarehouse();
  String number="DEV-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  SalesReturn salesReturn=returns.saveAndFlush(new SalesReturn(number,sale,warehouse,request.reason().trim(),key,hash,actor));
  for(Calc c:calc){
   SalesReturnLine line=new SalesReturnLine(c.item(),c.quantity(),c.disposition());salesReturn.addLine(line);
   String base="SRET-"+salesReturn.getId()+"-"+c.item().getId();
   String movementHash=hash(salesReturn.getId()+"|"+c.item().getId()+"|"+c.quantity().toPlainString()+"|"+c.disposition());
   if(c.disposition()==ReturnDisposition.RESTOCK){
    StockBalance balance=balances.findForUpdate(c.item().getProduct().getId(),warehouse.getId()).orElse(null);
    if(balance==null)balance=balances.save(new StockBalance(c.item().getProduct(),warehouse));
    balance.increase(c.quantity());
    movements.save(new StockMovement(c.item().getProduct(),warehouse,MovementType.RETURN_IN,c.quantity(),c.item().getProduct().getCostPrice(),"SALES_RETURN",salesReturn.getId(),base+"-IN",movementHash,"Devolución "+number,actor));
   }else{
    // Se registran entrada y salida compensadas: el artículo devuelto como merma no aumenta stock vendible.
    movements.save(new StockMovement(c.item().getProduct(),warehouse,MovementType.RETURN_IN,c.quantity(),c.item().getProduct().getCostPrice(),"SALES_RETURN",salesReturn.getId(),base+"-IN",movementHash,"Entrada no vendible por devolución "+number,actor));
    movements.save(new StockMovement(c.item().getProduct(),warehouse,MovementType.RETURN_OUT,c.quantity(),c.item().getProduct().getCostPrice(),"SALES_RETURN",salesReturn.getId(),base+"-OUT",movementHash,"Merma por devolución "+number,actor));
   }
  }
  returns.save(salesReturn);
  outbox.save(new OutboxEvent("SALES_RETURN",salesReturn.getId(),"SALES_RETURN_POSTED",Map.of("returnId",salesReturn.getId().toString(),"returnNumber",number,"saleId",sale.getId().toString())));
  audit.record(actor,"SALES_RETURN_POSTED","SALES_RETURN",salesReturn.getId(),Map.of("returnNumber",number,"sourceSaleId",sale.getId().toString()));
  return SalesReturnDtos.View.from(returns.findDetailedById(salesReturn.getId()).orElseThrow());
 }
 private record Calc(SaleItem item,BigDecimal quantity,ReturnDisposition disposition){}
 private String json(SalesReturnDtos.CreateRequest r){try{return mapper.writeValueAsString(r);}catch(JsonProcessingException ex){throw new BusinessRuleException("No se pudo validar la devolución.");}}
 private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
