package com.ecuapp.inventary.sales;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.returns.ReturnStatus;
import com.ecuapp.inventary.returns.SalesReturnLineRepository;
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
public class SaleService {
 private record CalculatedLine(Product product,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
 private final SaleRepository sales;private final ProductRepository products;private final WarehouseRepository warehouses;
 private final StockBalanceRepository balances;private final StockMovementRepository movements;private final OutboxEventRepository outbox;
 private final ObjectMapper mapper;private final AuditService audit;private final SalesReturnLineRepository returnLines;
 public SaleService(SaleRepository sales,ProductRepository products,WarehouseRepository warehouses,StockBalanceRepository balances,
  StockMovementRepository movements,OutboxEventRepository outbox,ObjectMapper mapper,AuditService audit,SalesReturnLineRepository returnLines){
  this.sales=sales;this.products=products;this.warehouses=warehouses;this.balances=balances;this.movements=movements;
  this.outbox=outbox;this.mapper=mapper;this.audit=audit;this.returnLines=returnLines;
 }
 @Transactional(readOnly=true) public Page<SaleDtos.View> list(Pageable p){return sales.findAll(PageRequest.of(p.getPageNumber(),Math.min(p.getPageSize(),100),p.getSort())).map(SaleDtos.View::from);}
 @Transactional(readOnly=true) public SaleDtos.View get(UUID id){return SaleDtos.View.from(sales.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Venta",id)));}
 @Transactional public SaleDtos.View create(String key,SaleDtos.CreateRequest request,String actor){
  if(key==null||key.isBlank()||key.length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");
  String hash=requestHash(request);Optional<Sale> previous=sales.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!hash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
   return SaleDtos.View.from(sales.findDetailedById(previous.get().getId()).orElseThrow());
  }
  Warehouse warehouse=warehouses.findById(request.warehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén",request.warehouseId()));
  if(!warehouse.isActive())throw new BusinessRuleException("El almacén está inactivo.");
  Set<UUID> uniqueProducts=new HashSet<>();List<CalculatedLine> lines=new ArrayList<>();
  BigDecimal subtotal=BigDecimal.ZERO.setScale(2);String saleCurrency=null;
  List<SaleDtos.LineRequest> sorted=new ArrayList<>(request.items());sorted.sort(Comparator.comparing(x->x.productId().toString()));
  for(SaleDtos.LineRequest line:sorted){
   if(!uniqueProducts.add(line.productId()))throw new BusinessRuleException("No repita el mismo producto en varias líneas de una venta; consolide las cantidades.");
   Product product=products.findById(line.productId()).orElseThrow(()->new ResourceNotFoundException("Producto",line.productId()));
   if(!product.isActive()||!product.getCategory().isActive())throw new BusinessRuleException("El producto "+product.getSku()+" está inactivo o su categoría no está publicada.");
   if(saleCurrency==null)saleCurrency=product.getCurrency();else if(!saleCurrency.equals(product.getCurrency()))throw new BusinessRuleException("No se pueden combinar productos con monedas diferentes en una venta.");
   BigDecimal quantity=line.quantity().setScale(3,RoundingMode.UNNECESSARY);
   StockBalance balance=balances.findForUpdate(product.getId(),warehouse.getId()).orElseThrow(()->new BusinessRuleException("No existe saldo de inventario para "+product.getSku()+" en el almacén."));
   balance.decrease(quantity);
   BigDecimal unitPrice=product.getSalePrice().setScale(2,RoundingMode.UNNECESSARY);
   BigDecimal lineTotal=unitPrice.multiply(quantity).setScale(2,RoundingMode.HALF_UP);subtotal=subtotal.add(lineTotal).setScale(2,RoundingMode.HALF_UP);
   lines.add(new CalculatedLine(product,quantity,unitPrice,lineTotal));
  }
  if(lines.isEmpty())throw new BusinessRuleException("La venta debe contener al menos una línea.");
  String saleNumber="VTA-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  Sale sale=sales.saveAndFlush(new Sale(saleNumber,clean(request.customerName()),clean(request.customerPhone()),request.channel(),warehouse,
   subtotal,BigDecimal.ZERO.setScale(2),subtotal,saleCurrency==null?"USD":saleCurrency,key,hash,actor));
  for(CalculatedLine line:lines){
   sale.addItem(new SaleItem(line.product(),line.quantity(),line.unitPrice(),line.lineTotal()));
   String movementKey="SALE-"+sha256(key).substring(0,32)+"-"+line.product().getId();
   String movementHash=sha256(sale.getId()+"|"+line.product().getId()+"|"+line.quantity().toPlainString());
   movements.save(new StockMovement(line.product(),warehouse,MovementType.SALE,line.quantity(),line.product().getCostPrice(),"SALE",sale.getId(),movementKey,movementHash,"Salida por venta "+sale.getSaleNumber(),actor));
  }
  sales.save(sale);
  outbox.save(new OutboxEvent("SALE",sale.getId(),"SALE_COMPLETED",Map.of("saleId",sale.getId().toString(),"saleNumber",sale.getSaleNumber(),"total",sale.getTotal().toPlainString(),"currency",sale.getCurrency())));
  audit.record(actor,"SALE_COMPLETED","SALE",sale.getId(),Map.of("saleNumber",sale.getSaleNumber(),"total",sale.getTotal().toPlainString(),"currency",sale.getCurrency(),"lines",lines.size()));
  return SaleDtos.View.from(sales.findDetailedById(sale.getId()).orElseThrow());
 }
 @Transactional public SaleDtos.View cancel(UUID id,String key,String reason,String actor){
  if(key==null||key.isBlank()||key.length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");
  if(reason==null||reason.isBlank()||reason.trim().length()>1000)throw new BusinessRuleException("Debe indicar un motivo de cancelación de hasta 1000 caracteres.");
  Sale sale=sales.findForUpdateById(id).orElseThrow(()->new ResourceNotFoundException("Venta",id));
  if(sale.getStatus()==SaleStatus.CANCELLED)return SaleDtos.View.from(sales.findDetailedById(id).orElseThrow());
  if(sale.getStatus()!=SaleStatus.COMPLETED)throw new ConflictException("La venta no se puede cancelar en su estado actual.");
  if(returnLines.existsForSale(id))throw new ConflictException("No se puede cancelar una venta que ya tiene devoluciones; registre la devolución o un ajuste separado.");
  List<SaleItem> items=new ArrayList<>(sale.getItems());items.sort(Comparator.comparing(i->i.getProduct().getId().toString()));
  for(SaleItem item:items){
   StockBalance balance=balances.findForUpdate(item.getProduct().getId(),sale.getWarehouse().getId()).orElseThrow(()->new BusinessRuleException("No existe saldo para reponer "+item.getSkuSnapshot()+"."));
   balance.increase(item.getQuantity());
   String movementKey="SALE-CANCEL-"+sale.getId()+"-"+item.getProduct().getId();
   movements.save(new StockMovement(item.getProduct(),sale.getWarehouse(),MovementType.SALE_CANCEL_IN,item.getQuantity(),item.getProduct().getCostPrice(),
    "SALE_CANCELLATION",sale.getId(),movementKey,sha256(key+"|"+reason.trim()),"Cancelación de venta "+sale.getSaleNumber()+": "+reason.trim(),actor));
  }
  sale.cancel();
  outbox.save(new OutboxEvent("SALE",sale.getId(),"SALE_CANCELLED",Map.of("saleId",sale.getId().toString(),"saleNumber",sale.getSaleNumber(),"reason",reason.trim(),"cancelledBy",actor)));
  audit.record(actor,"SALE_CANCELLED","SALE",sale.getId(),Map.of("saleNumber",sale.getSaleNumber(),"reason",reason.trim(),"lines",items.size()));
  return SaleDtos.View.from(sales.findDetailedById(id).orElseThrow());
 }
 private String requestHash(SaleDtos.CreateRequest request){try{return sha256(mapper.writeValueAsString(request));}catch(JsonProcessingException ex){throw new BusinessRuleException("No se pudo validar el contenido de la solicitud.");}}
 private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("No se pudo calcular huella de idempotencia.",ex);}}
 private String clean(String value){return value==null||value.isBlank()?null:value.trim();}
}
