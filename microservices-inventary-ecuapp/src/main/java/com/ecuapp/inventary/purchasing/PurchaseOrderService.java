package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.partners.*;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService {
 private record ReceiptCalc(PurchaseOrderLine line,BigDecimal quantity){}
 private final PurchaseOrderRepository orders;private final GoodsReceiptRepository receipts;private final BusinessPartnerRepository partners;
 private final WarehouseRepository warehouses;private final ProductRepository products;private final StockBalanceRepository balances;
 private final StockMovementRepository movements;private final OutboxEventRepository outbox;private final ObjectMapper mapper;private final AuditService audit;
 public PurchaseOrderService(PurchaseOrderRepository orders,GoodsReceiptRepository receipts,BusinessPartnerRepository partners,WarehouseRepository warehouses,
   ProductRepository products,StockBalanceRepository balances,StockMovementRepository movements,OutboxEventRepository outbox,ObjectMapper mapper,AuditService audit){
  this.orders=orders;this.receipts=receipts;this.partners=partners;this.warehouses=warehouses;this.products=products;this.balances=balances;this.movements=movements;this.outbox=outbox;this.mapper=mapper;this.audit=audit;
 }
 @Transactional(readOnly=true) public Page<PurchaseDtos.OrderView> list(Pageable p){return orders.findAll(PageRequest.of(p.getPageNumber(),Math.min(p.getPageSize(),100),p.getSort())).map(PurchaseDtos.OrderView::from);}
 @Transactional(readOnly=true) public PurchaseDtos.OrderView get(UUID id){return PurchaseDtos.OrderView.from(orders.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Orden de compra",id)));}
 @Transactional public PurchaseDtos.OrderView create(PurchaseDtos.CreateOrderRequest r,String actor){
  BusinessPartner supplier=partners.findByIdAndTypeIn(r.supplierId(),List.of(PartnerType.SUPPLIER,PartnerType.BOTH)).orElseThrow(()->new ResourceNotFoundException("Proveedor",r.supplierId()));
  if(!supplier.isActive())throw new BusinessRuleException("El proveedor está inactivo.");
  Warehouse warehouse=warehouses.findById(r.warehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén",r.warehouseId()));
  if(!warehouse.isActive())throw new BusinessRuleException("El almacén está inactivo.");
  BigDecimal subtotal=BigDecimal.ZERO.setScale(2);String currency=null;Set<UUID> unique=new HashSet<>();
  List<PurchaseDtos.OrderLineRequest> sorted=new ArrayList<>(r.items());sorted.sort(Comparator.comparing(x->x.productId().toString()));
  List<PurchaseOrderLine> newLines=new ArrayList<>();
  for(PurchaseDtos.OrderLineRequest line:sorted){
   if(!unique.add(line.productId()))throw new BusinessRuleException("No repita el mismo producto en varias líneas de una orden de compra.");
   Product product=products.findById(line.productId()).orElseThrow(()->new ResourceNotFoundException("Producto",line.productId()));
   if(!product.isActive())throw new BusinessRuleException("No se puede comprar un producto inactivo.");
   if(currency==null)currency=product.getCurrency();else if(!currency.equals(product.getCurrency()))throw new BusinessRuleException("Todos los productos de una orden deben usar la misma moneda.");
   BigDecimal qty=line.quantity().setScale(3,RoundingMode.UNNECESSARY),unitCost=line.unitCost().setScale(2,RoundingMode.UNNECESSARY);
   BigDecimal total=unitCost.multiply(qty).setScale(2,RoundingMode.HALF_UP);subtotal=subtotal.add(total).setScale(2,RoundingMode.HALF_UP);
   newLines.add(new PurchaseOrderLine(product,qty,unitCost,total));
  }
  String number="OC-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  PurchaseOrder order=orders.saveAndFlush(new PurchaseOrder(number,supplier,warehouse,currency==null?"USD":currency,subtotal,subtotal,clean(r.notes()),actor));
  newLines.forEach(order::addLine);orders.save(order);
  audit.record(actor,"PURCHASE_ORDER_CREATED","PURCHASE_ORDER",order.getId(),Map.of("orderNumber",number,"lineCount",newLines.size()));
  return PurchaseDtos.OrderView.from(orders.findDetailedById(order.getId()).orElseThrow());
 }
 @Transactional public PurchaseDtos.OrderView submit(UUID id){PurchaseOrder order=requireForUpdate(id);order.submit();audit.record("system","PURCHASE_ORDER_SUBMITTED","PURCHASE_ORDER",id,Map.of("orderNumber",order.getOrderNumber()));return PurchaseDtos.OrderView.from(order);}
 @Transactional public PurchaseDtos.OrderView approve(UUID id){PurchaseOrder order=requireForUpdate(id);order.approve();audit.record("system","PURCHASE_ORDER_APPROVED","PURCHASE_ORDER",id,Map.of("orderNumber",order.getOrderNumber()));return PurchaseDtos.OrderView.from(order);}
 @Transactional public PurchaseDtos.OrderView cancel(UUID id){PurchaseOrder order=requireForUpdate(id);order.cancel();audit.record("system","PURCHASE_ORDER_CANCELLED","PURCHASE_ORDER",id,Map.of("orderNumber",order.getOrderNumber()));return PurchaseDtos.OrderView.from(order);}
 @Transactional(readOnly=true) public PurchaseDtos.ReceiptView getReceipt(UUID id){return PurchaseDtos.ReceiptView.from(receipts.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Recepción",id)));}
 @Transactional public PurchaseDtos.ReceiptView receive(UUID orderId,String key,PurchaseDtos.ReceiptRequest request,String actor){
  if(key==null||key.isBlank()||key.length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");
  String hash=hashRequest(orderId,request);Optional<GoodsReceipt> previous=receipts.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!hash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con una recepción distinta.");
   return PurchaseDtos.ReceiptView.from(receipts.findDetailedById(previous.get().getId()).orElseThrow());
  }
  PurchaseOrder order=orders.findForUpdate(orderId).orElseThrow(()->new ResourceNotFoundException("Orden de compra",orderId));
  if(order.getStatus()!=PurchaseOrderStatus.APPROVED&&order.getStatus()!=PurchaseOrderStatus.PARTIALLY_RECEIVED)throw new BusinessRuleException("Solo se reciben órdenes aprobadas o parcialmente recibidas.");
  Map<UUID,PurchaseOrderLine> lineById=order.getLines().stream().collect(Collectors.toMap(PurchaseOrderLine::getId,Function.identity()));
  List<PurchaseDtos.ReceiptLineRequest> sorted=new ArrayList<>(request.items());
  sorted.sort(Comparator.comparing(x->{PurchaseOrderLine l=lineById.get(x.purchaseOrderLineId());return l==null?"~":l.getProduct().getId().toString();}));
  Set<UUID> seen=new HashSet<>();List<ReceiptCalc> calculated=new ArrayList<>();
  for(PurchaseDtos.ReceiptLineRequest line:sorted){
   if(!seen.add(line.purchaseOrderLineId()))throw new BusinessRuleException("No repita la misma línea de orden en una recepción.");
   PurchaseOrderLine poLine=lineById.get(line.purchaseOrderLineId());
   if(poLine==null)throw new BusinessRuleException("La línea indicada no pertenece a la orden de compra.");
   BigDecimal quantity=line.quantity().setScale(3,RoundingMode.UNNECESSARY);
   if(poLine.remaining().compareTo(quantity)<0)throw new BusinessRuleException("La cantidad recibida supera la cantidad pendiente para "+poLine.getSkuSnapshot()+".");
   StockBalance balance=balances.findForUpdate(poLine.getProduct().getId(),order.getWarehouse().getId()).orElse(null);
   if(balance==null)balance=balances.save(new StockBalance(poLine.getProduct(),order.getWarehouse()));
   balance.increase(quantity);poLine.receive(quantity);calculated.add(new ReceiptCalc(poLine,quantity));
  }
  order.recalculateReceiptStatus();
  String number="REC-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  GoodsReceipt receipt=receipts.saveAndFlush(new GoodsReceipt(number,order,order.getWarehouse(),key,hash,actor));
  for(ReceiptCalc calc:calculated){
   receipt.addLine(new GoodsReceiptLine(calc.line(),calc.quantity()));
   String movementKey="RCV-"+sha256(key).substring(0,32)+"-"+calc.line().getProduct().getId();
   movements.save(new StockMovement(calc.line().getProduct(),order.getWarehouse(),MovementType.RECEIPT,calc.quantity(),calc.line().getUnitCost(),
    "GOODS_RECEIPT",receipt.getId(),movementKey,sha256(receipt.getId()+"|"+calc.line().getId()+"|"+calc.quantity().toPlainString()),"Recepción "+receipt.getReceiptNumber(),actor));
  }
  receipts.save(receipt);
  outbox.save(new OutboxEvent("GOODS_RECEIPT",receipt.getId(),"GOODS_RECEIPT_POSTED",Map.of("receiptId",receipt.getId().toString(),"receiptNumber",receipt.getReceiptNumber(),"purchaseOrderId",order.getId().toString())));
  audit.record(actor,"GOODS_RECEIPT_POSTED","GOODS_RECEIPT",receipt.getId(),Map.of("receiptNumber",receipt.getReceiptNumber(),"purchaseOrderId",order.getId().toString(),"lines",calculated.size()));
  return PurchaseDtos.ReceiptView.from(receipts.findDetailedById(receipt.getId()).orElseThrow());
 }
 private PurchaseOrder requireForUpdate(UUID id){return orders.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Orden de compra",id));}
 private String hashRequest(UUID orderId,PurchaseDtos.ReceiptRequest r){try{return sha256(orderId+"|"+mapper.writeValueAsString(r));}catch(JsonProcessingException ex){throw new BusinessRuleException("No se pudo validar la recepción.");}}
 private String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
}
