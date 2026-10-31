package com.ecuapp.inventary.purchasing;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.auth.WarehouseScopeService;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.partners.*;
import com.ecuapp.inventary.sales.OutboxEvent;
import com.ecuapp.inventary.sales.OutboxEventRepository;
import com.ecuapp.inventary.warehouse.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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
 private record ReceiptCalc(PurchaseOrderLine line,BigDecimal quantity,StockBalance balance){}
 private static final Set<String> ORDER_SORT=Set.of("orderedAt","orderNumber","status","total");
 private static final Set<String> RECEIPT_SORT=Set.of("receivedAt","receiptNumber");
 private final PurchaseOrderRepository orders;private final GoodsReceiptRepository receipts;private final GoodsReceiptLineRepository receiptLines;private final IdempotencyRecordRepository idempotencyRecords;private final BusinessPartnerRepository partners;
 private final WarehouseRepository warehouses;private final ProductRepository products;private final StockBalanceLocks balanceLocks;private final UnitOfMeasureRepository units;
 private final StockMovementRepository movements;private final OutboxEventRepository outbox;private final ObjectMapper mapper;private final AuditService audit;private final InventoryCostingService costing;
 private final boolean requireDistinctApprover;private final WarehouseScopeService scopes;
 public PurchaseOrderService(PurchaseOrderRepository orders,GoodsReceiptRepository receipts,GoodsReceiptLineRepository receiptLines,IdempotencyRecordRepository idempotencyRecords,BusinessPartnerRepository partners,WarehouseRepository warehouses,
   ProductRepository products,StockBalanceLocks balanceLocks,UnitOfMeasureRepository units,StockMovementRepository movements,OutboxEventRepository outbox,ObjectMapper mapper,AuditService audit,InventoryCostingService costing,
   @Value("${app.purchasing.require-distinct-approver:true}") boolean requireDistinctApprover,WarehouseScopeService scopes){
  this.orders=orders;this.receipts=receipts;this.receiptLines=receiptLines;this.idempotencyRecords=idempotencyRecords;this.partners=partners;this.warehouses=warehouses;this.products=products;this.balanceLocks=balanceLocks;this.units=units;this.movements=movements;this.outbox=outbox;this.mapper=mapper;this.audit=audit;this.costing=costing;
  this.requireDistinctApprover=requireDistinctApprover;this.scopes=scopes;
 }
 @Transactional(readOnly=true) public Page<PurchaseDtos.OrderView> list(PurchaseOrderStatus status,UUID supplierId,UUID warehouseId,Instant from,Instant to,Pageable p,String actor){
  validateRange(from,to);
  if(warehouseId!=null)scopes.requireWarehouseAccess(actor,warehouseId);
  var allowed=scopes.accessibleWarehouseIds(actor);var pageable=Pages.of(p,ORDER_SORT,Sort.by(Sort.Direction.DESC,"orderedAt"));
  if(allowed!=null&&allowed.isEmpty())return Page.empty(pageable);
  var lower=com.ecuapp.inventary.common.TimeRange.lower(from);var upper=com.ecuapp.inventary.common.TimeRange.upper(to);
  return (allowed==null?orders.search(status,supplierId,warehouseId,lower,upper,pageable):
   orders.searchScoped(status,supplierId,warehouseId,lower,upper,allowed,pageable)).map(PurchaseDtos.OrderView::from);
 }
 @Transactional(readOnly=true) public PurchaseDtos.OrderView get(UUID id,String actor){
  PurchaseOrder order=orders.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Orden de compra",id));
  scopes.requireWarehouseAccess(actor,order.getWarehouse().getId());return PurchaseDtos.OrderView.from(order);
 }
 @Transactional(readOnly=true) public Page<PurchaseDtos.ReceiptView> listReceipts(UUID purchaseOrderId,UUID warehouseId,Instant from,Instant to,Pageable p,String actor){
  validateRange(from,to);
  if(warehouseId!=null)scopes.requireWarehouseAccess(actor,warehouseId);
  var allowed=scopes.accessibleWarehouseIds(actor);var pageable=Pages.of(p,RECEIPT_SORT,Sort.by(Sort.Direction.DESC,"receivedAt"));
  if(allowed!=null&&allowed.isEmpty())return Page.empty(pageable);
  var lower=com.ecuapp.inventary.common.TimeRange.lower(from);var upper=com.ecuapp.inventary.common.TimeRange.upper(to);
  return (allowed==null?receipts.search(purchaseOrderId,warehouseId,lower,upper,pageable):
   receipts.searchScoped(purchaseOrderId,warehouseId,lower,upper,allowed,pageable)).map(PurchaseDtos.ReceiptView::from);
 }
 @Transactional public PurchaseDtos.OrderView create(String key,PurchaseDtos.CreateOrderRequest r,String actor){
  scopes.requireWarehouseAccess(actor,r.warehouseId());
  key=normalizeKey(key);
  String requestHash=hashOrderRequest(r);
  idempotencyRecords.insertIfAbsent(UUID.randomUUID(),"PURCHASE_ORDER_CREATE",key,requestHash);
  IdempotencyRecord idempotency=idempotencyRecords.findForUpdate("PURCHASE_ORDER_CREATE",key).orElseThrow(()->new ConflictException("No se pudo reservar la clave de idempotencia."));
  if(!requestHash.equals(idempotency.getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra orden de compra.");
  if(idempotency.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED&&idempotency.getResourceId()!=null)
   return PurchaseDtos.OrderView.from(orders.findDetailedById(idempotency.getResourceId()).orElseThrow(()->new ResourceNotFoundException("Orden de compra",idempotency.getResourceId())));
  if(idempotency.getStatus()!=IdempotencyRecord.RecordStatus.IN_PROGRESS)throw new ConflictException("La clave de idempotencia no está disponible para una nueva operación.");
  BusinessPartner supplier=partners.findForUpdateByIdAndTypeIn(r.supplierId(),List.of(PartnerType.SUPPLIER,PartnerType.BOTH)).orElseThrow(()->new ResourceNotFoundException("Proveedor",r.supplierId()));
  if(!supplier.isActive())throw new BusinessRuleException("El proveedor está inactivo.");
  Warehouse warehouse=warehouses.findForUpdate(r.warehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén",r.warehouseId()));
  if(!warehouse.isActive())throw new BusinessRuleException("El almacén está inactivo.");
  BigDecimal subtotal=BigDecimal.ZERO.setScale(2);String currency=null;Set<UUID> unique=new HashSet<>();
  List<PurchaseDtos.OrderLineRequest> sorted=new ArrayList<>(r.items());sorted.sort(Comparator.comparing(x->x.productId().toString()));
  List<PurchaseOrderLine> newLines=new ArrayList<>();
  for(PurchaseDtos.OrderLineRequest line:sorted){
   if(!unique.add(line.productId()))throw new BusinessRuleException("No repita el mismo producto en varias líneas de una orden de compra.");
   Product product=products.findForUpdate(line.productId()).orElseThrow(()->new ResourceNotFoundException("Producto",line.productId()));
   if(!product.isActive())throw new BusinessRuleException("No se puede comprar un producto inactivo.");
   if(product.getKind()!=ProductKind.STOCKED_GOOD)throw new BusinessRuleException("Solo se pueden recibir físicamente productos con control de stock.");
   if(currency==null)currency=product.getCurrency();else if(!currency.equals(product.getCurrency()))throw new BusinessRuleException("Todos los productos de una orden deben usar la misma moneda.");
   BigDecimal qty=line.quantity().setScale(3,RoundingMode.UNNECESSARY),unitCost=line.unitCost().setScale(2,RoundingMode.UNNECESSARY);
   BigDecimal total=unitCost.multiply(qty).setScale(2,RoundingMode.HALF_UP);subtotal=subtotal.add(total).setScale(2,RoundingMode.HALF_UP);
   newLines.add(new PurchaseOrderLine(product,qty,unitCost,total));
  }
  String number="OC-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  PurchaseOrder order=orders.saveAndFlush(new PurchaseOrder(number,supplier,warehouse,currency==null?"USD":currency,subtotal,subtotal,clean(r.notes()),actor));
  newLines.forEach(order::addLine);orders.save(order);
  idempotency.markCompleted("PURCHASE_ORDER",order.getId(),(short)201);idempotencyRecords.save(idempotency);
  audit.record(actor,"PURCHASE_ORDER_CREATED","PURCHASE_ORDER",order.getId(),Map.of("orderNumber",number,"lineCount",newLines.size()));
  outbox.save(new OutboxEvent("PURCHASE_ORDER",order.getId(),"PURCHASE_ORDER_CREATED",Map.of(
   "purchaseOrderId",order.getId().toString(),"orderNumber",number,"supplierId",supplier.getId().toString(),
   "warehouseId",warehouse.getId().toString(),"lineCount",newLines.size(),"currency",order.getCurrency())));
  return PurchaseDtos.OrderView.from(orders.findDetailedById(order.getId()).orElseThrow());
 }
 @Transactional public PurchaseDtos.OrderView submit(UUID id,String actor){
  PurchaseOrder order=requireForUpdate(id);scopes.requireWarehouseAccess(actor,order.getWarehouse().getId());
   if(order.submit(actor)){
    audit.record(actor,"PURCHASE_ORDER_SUBMITTED","PURCHASE_ORDER",id,Map.of("orderNumber",order.getOrderNumber()));
    outbox.save(new OutboxEvent("PURCHASE_ORDER",id,"PURCHASE_ORDER_SUBMITTED",Map.of(
     "purchaseOrderId",id.toString(),"orderNumber",order.getOrderNumber(),"submittedBy",actor,
     "warehouseId",order.getWarehouse().getId().toString())));
   }
   return detailed(id);
 }
 @Transactional public PurchaseDtos.OrderView approve(UUID id,String actor){
  PurchaseOrder order=requireForUpdate(id);scopes.requireWarehouseAccess(actor,order.getWarehouse().getId());
   if(order.approve(actor,requireDistinctApprover)){
    audit.record(actor,"PURCHASE_ORDER_APPROVED","PURCHASE_ORDER",id,Map.of("orderNumber",order.getOrderNumber(),"approvedBy",actor));
    outbox.save(new OutboxEvent("PURCHASE_ORDER",id,"PURCHASE_ORDER_APPROVED",Map.of(
     "purchaseOrderId",id.toString(),"orderNumber",order.getOrderNumber(),"approvedBy",actor,
     "warehouseId",order.getWarehouse().getId().toString())));
   }
   return detailed(id);
 }
 @Transactional public PurchaseDtos.OrderView cancel(UUID id,String actor){
  PurchaseOrder order=requireForUpdate(id);scopes.requireWarehouseAccess(actor,order.getWarehouse().getId());
   if(order.cancel(actor)){
    audit.record(actor,"PURCHASE_ORDER_CANCELLED","PURCHASE_ORDER",id,Map.of("orderNumber",order.getOrderNumber(),"cancelledBy",actor));
    outbox.save(new OutboxEvent("PURCHASE_ORDER",id,"PURCHASE_ORDER_CANCELLED",Map.of(
     "purchaseOrderId",id.toString(),"orderNumber",order.getOrderNumber(),"cancelledBy",actor,
     "warehouseId",order.getWarehouse().getId().toString())));
   }
   return detailed(id);
 }
 @Transactional(readOnly=true) public PurchaseDtos.ReceiptView getReceipt(UUID id,String actor){
  GoodsReceipt receipt=receipts.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Recepción",id));
  scopes.requireWarehouseAccess(actor,receipt.getWarehouse().getId());return PurchaseDtos.ReceiptView.from(receipt);
 }
 @Transactional public PurchaseDtos.ReceiptView receive(UUID orderId,String rawKey,PurchaseDtos.ReceiptRequest request,String actor){
  PurchaseOrder visibleOrder=orders.findById(orderId).orElseThrow(()->new ResourceNotFoundException("Orden de compra",orderId));
  scopes.requireWarehouseAccess(actor,visibleOrder.getWarehouse().getId());
  String key=normalizeKey(rawKey);
  String hash=hashRequest(orderId,request);
  Optional<GoodsReceipt> previous=receipts.findByIdempotencyKey(key);
  if(previous.isPresent()){
   String legacyHash=legacyHashRequest(orderId,request);
   if(!hash.equals(previous.get().getRequestHash())&&!legacyHash.equals(previous.get().getRequestHash()))
    throw new ConflictException("La clave Idempotency-Key ya se usó con una recepción distinta.");
   return PurchaseDtos.ReceiptView.from(receipts.findDetailedById(previous.get().getId()).orElseThrow());
  }
  idempotencyRecords.insertIfAbsent(UUID.randomUUID(),"GOODS_RECEIPT_CREATE",key,hash);
  IdempotencyRecord idempotency=idempotencyRecords.findForUpdate("GOODS_RECEIPT_CREATE",key)
   .orElseThrow(()->new ConflictException("No se pudo reservar la clave de idempotencia."));
  if(!hash.equals(idempotency.getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra recepción.");
  if(idempotency.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED&&idempotency.getResourceId()!=null)
   return PurchaseDtos.ReceiptView.from(receipts.findDetailedById(idempotency.getResourceId()).orElseThrow(()->new ResourceNotFoundException("Recepción",idempotency.getResourceId())));
  if(idempotency.getStatus()!=IdempotencyRecord.RecordStatus.IN_PROGRESS)throw new ConflictException("La clave de idempotencia no está disponible para una nueva operación.");
  PurchaseOrder order=orders.findForUpdate(orderId).orElseThrow(()->new ResourceNotFoundException("Orden de compra",orderId));
  scopes.requireWarehouseAccess(actor,order.getWarehouse().getId());
  if(order.getStatus()!=PurchaseOrderStatus.APPROVED&&order.getStatus()!=PurchaseOrderStatus.PARTIALLY_RECEIVED)throw new BusinessRuleException("Solo se reciben órdenes aprobadas o parcialmente recibidas.");
  if(!order.getWarehouse().isActive())throw new BusinessRuleException("El almacén de destino está inactivo.");
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
   // The last received purchase becomes the product cost and reprices it with its markup (lock product, then balance, like sales).
   products.findForUpdate(poLine.getProduct().getId()).orElseThrow(()->new ResourceNotFoundException("Producto",poLine.getProduct().getId()))
    .applyPurchaseCost(poLine.getUnitCost());
   StockBalance balance=balanceLocks.lockOrCreate(poLine.getProduct(),order.getWarehouse());
   balance.increase(quantity);poLine.receive(quantity);calculated.add(new ReceiptCalc(poLine,quantity,balance));
  }
  order.recalculateReceiptStatus();
  String number="REC-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  GoodsReceipt receipt=receipts.saveAndFlush(new GoodsReceipt(number,order,order.getWarehouse(),key,hash,actor));
  for(ReceiptCalc calc:calculated){
   GoodsReceiptLine receiptLine=new GoodsReceiptLine(calc.line(),calc.quantity());
   receipt.addLine(receiptLine);
   receiptLines.saveAndFlush(receiptLine);
   Product product=calc.line().getProduct();
   // Receipts open a FIFO layer at the purchase order cost and update the moving average.
   InventoryCostingService.Valuation valuation=costing.inbound(product,calc.balance().getLocation(),calc.quantity(),calc.line().getUnitCost());
   String movementKey="RCV-"+sha256(key).substring(0,32)+"-"+product.getId();
   StockMovement movement=new StockMovement(product,order.getWarehouse(),MovementType.RECEIPT,calc.quantity(),valuation.movementUnitCost(),
    "GOODS_RECEIPT",receipt.getId(),movementKey,sha256(receipt.getId()+"|"+receiptLine.getId()+"|"+calc.quantity().toPlainString()),"Recepción "+receipt.getReceiptNumber(),actor);
   movement.assignLocations(null,calc.balance().getLocation(),
    units.findById(product.getUnitOfMeasureId()).orElseThrow(()->new ResourceNotFoundException("Unidad de medida",product.getUnitOfMeasureId())),
    receipt.getReceiptNumber(),receiptLine.getId(),valuation.method());
   costing.post(movements.save(movement),valuation);
  }
  receipts.save(receipt);
  idempotency.markCompleted("GOODS_RECEIPT",receipt.getId(),(short)201);idempotencyRecords.save(idempotency);
  outbox.save(new OutboxEvent("GOODS_RECEIPT",receipt.getId(),"GOODS_RECEIPT_POSTED",Map.of("receiptId",receipt.getId().toString(),"receiptNumber",receipt.getReceiptNumber(),"purchaseOrderId",order.getId().toString())));
  audit.record(actor,"GOODS_RECEIPT_POSTED","GOODS_RECEIPT",receipt.getId(),Map.of("receiptNumber",receipt.getReceiptNumber(),"purchaseOrderId",order.getId().toString(),"lines",calculated.size()));
  return PurchaseDtos.ReceiptView.from(receipts.findDetailedById(receipt.getId()).orElseThrow());
 }
 private PurchaseDtos.OrderView detailed(UUID id){orders.flush();return PurchaseDtos.OrderView.from(orders.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Orden de compra",id)));}
 private PurchaseOrder requireForUpdate(UUID id){return orders.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Orden de compra",id));}
 private void validateRange(Instant from,Instant to){if(from!=null&&to!=null&&!from.isBefore(to))throw new InvalidQueryException("'from' debe ser anterior a 'to'.");}
 private String hashOrderRequest(PurchaseDtos.CreateOrderRequest r){
  List<String> lines=r.items().stream().map(x->x.productId()+"|"+x.quantity().setScale(3,RoundingMode.UNNECESSARY).toPlainString()+"|"+x.unitCost().setScale(2,RoundingMode.UNNECESSARY).toPlainString()).sorted().toList();
  return sha256(r.supplierId()+"|"+r.warehouseId()+"|"+Objects.toString(clean(r.notes()),"")+"|"+String.join(";",lines));
 }
 private String hashRequest(UUID orderId,PurchaseDtos.ReceiptRequest r){
  List<String> lines=r.items().stream().map(x->x.purchaseOrderLineId()+"|"+x.quantity().setScale(3,RoundingMode.UNNECESSARY).toPlainString()).sorted().toList();
  return sha256(orderId+"|"+String.join(";",lines));
 }
 private String legacyHashRequest(UUID orderId,PurchaseDtos.ReceiptRequest r){try{return sha256(orderId+"|"+mapper.writeValueAsString(r));}catch(JsonProcessingException ex){throw new BusinessRuleException("No se pudo validar la recepción.");}}
 private String normalizeKey(String key){if(key==null||key.isBlank()||key.trim().length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");return key.trim();}
 private String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
}
