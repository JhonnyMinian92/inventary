package com.ecuapp.inventary.sales;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.auth.WarehouseScopeService;
import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.partners.BusinessPartner;
import com.ecuapp.inventary.partners.BusinessPartnerRepository;
import com.ecuapp.inventary.partners.PartnerType;
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
 private record CalculatedLine(Product product,StockBalance balance,UnitOfMeasure unit,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
 /** unitPrice is null when the client accepts the suggested price; null fields are omitted, so older hashes stay valid. */
 private record CanonicalLine(UUID productId,String quantity,String unitPrice){}
 private record CanonicalSaleRequest(UUID warehouseId,String customerName,String customerPhone,SaleChannel channel,UUID customerId,List<CanonicalLine> items){}
 private static final String IDEMPOTENCY_SCOPE="SALE_CREATE";
 private final SaleRepository sales;private final ProductRepository products;private final WarehouseRepository warehouses;
 private final StockBalanceRepository balances;private final StockMovementRepository movements;private final OutboxEventRepository outbox;
 private final ObjectMapper mapper;private final AuditService audit;private final SalesReturnLineRepository returnLines;
 private final BusinessPartnerRepository partners;private final IdempotencyRecordRepository idempotencyRecords;
 private final UnitOfMeasureRepository units;private final InventoryCostingService costing;private final SalePricingService pricing;private final WarehouseScopeService scopes;
 private static final Set<String> SORT=Set.of("soldAt","saleNumber","total","status");
 public SaleService(SaleRepository sales,ProductRepository products,WarehouseRepository warehouses,StockBalanceRepository balances,
  StockMovementRepository movements,OutboxEventRepository outbox,ObjectMapper mapper,AuditService audit,
  SalesReturnLineRepository returnLines,BusinessPartnerRepository partners,IdempotencyRecordRepository idempotencyRecords,
  UnitOfMeasureRepository units,InventoryCostingService costing,SalePricingService pricing,WarehouseScopeService scopes){
  this.sales=sales;this.products=products;this.warehouses=warehouses;this.balances=balances;this.movements=movements;
  this.outbox=outbox;this.mapper=mapper;this.audit=audit;this.returnLines=returnLines;this.partners=partners;
  this.idempotencyRecords=idempotencyRecords;this.units=units;this.costing=costing;this.pricing=pricing;this.scopes=scopes;
 }
 @Transactional(readOnly=true) public Page<SaleDtos.View> list(SaleStatus status,UUID warehouseId,UUID customerId,SaleChannel channel,java.time.Instant from,java.time.Instant to,Pageable p,String actor){
  if(from!=null&&to!=null&&!from.isBefore(to))throw new InvalidQueryException("'from' debe ser anterior a 'to'.");
  if(warehouseId!=null)scopes.requireWarehouseAccess(actor,warehouseId);
  var allowed=scopes.accessibleWarehouseIds(actor);var pageable=Pages.of(p,SORT,Sort.by(Sort.Direction.DESC,"soldAt"));
  if(allowed!=null&&allowed.isEmpty())return Page.empty(pageable);
  var lower=com.ecuapp.inventary.common.TimeRange.lower(from);var upper=com.ecuapp.inventary.common.TimeRange.upper(to);
  return (allowed==null?sales.search(status,warehouseId,customerId,channel,lower,upper,pageable):
   sales.searchScoped(status,warehouseId,customerId,channel,lower,upper,allowed,pageable)).map(SaleDtos.View::from);
 }
 @Transactional(readOnly=true) public SaleDtos.View get(UUID id,String actor){
  Sale sale=sales.findDetailedById(id).orElseThrow(()->new ResourceNotFoundException("Venta",id));
  scopes.requireWarehouseAccess(actor,sale.getWarehouse().getId());
  return SaleDtos.View.from(sale);
 }

 @Transactional
 public SaleDtos.View create(String suppliedKey,SaleDtos.CreateRequest request,String actor){
  scopes.requireWarehouseAccess(actor,request.warehouseId());
  String key=normalizeIdempotencyKey(suppliedKey);
  String hash=requestHash(request);

  // INSERT ... ON CONFLICT serializes concurrent requests for the same key in PostgreSQL.
  // The idempotency record and every business write commit or roll back together.
  int claimed=idempotencyRecords.insertIfAbsent(UUID.randomUUID(),IDEMPOTENCY_SCOPE,key,hash);
  IdempotencyRecord record=idempotencyRecords.findForUpdate(IDEMPOTENCY_SCOPE,key)
   .orElseThrow(()->new IllegalStateException("No se pudo reservar la clave de idempotencia."));
  if(!hash.equals(record.getRequestHash()))
   throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");

  if(claimed==0){
   if(record.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED&&record.getResourceId()!=null)
    return SaleDtos.View.from(sales.findDetailedById(record.getResourceId())
     .orElseThrow(()->new ConflictException("La clave de idempotencia apunta a una venta que ya no existe.")));
   throw new ConflictException("Ya existe una operación con esta clave de idempotencia y aún no puede reproducirse.");
  }

  // Preserve compatibility with sales created before idempotency_record was introduced.
  Optional<Sale> legacy=sales.findByIdempotencyKey(key);
  if(legacy.isPresent()){
   Sale previous=legacy.get();
   if(!hash.equals(previous.getRequestHash())&&!legacyRequestHash(request).equals(previous.getRequestHash()))
    throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
   record.markCompleted("SALE",previous.getId(),(short)201);
   idempotencyRecords.saveAndFlush(record);
   return SaleDtos.View.from(sales.findDetailedById(previous.getId()).orElseThrow());
  }

  Warehouse warehouse=warehouses.findById(request.warehouseId())
   .orElseThrow(()->new ResourceNotFoundException("Almacén",request.warehouseId()));
  if(!warehouse.isActive())throw new BusinessRuleException("El almacén está inactivo.");

  Set<UUID> uniqueProducts=new HashSet<>();
  List<CalculatedLine> lines=new ArrayList<>();
  BigDecimal subtotal=BigDecimal.ZERO.setScale(2);
  String saleCurrency=null;
  List<SaleDtos.LineRequest> sorted=new ArrayList<>(request.items());
  sorted.sort(Comparator.comparing(line->line.productId().toString()));

  // Lock stock balances in stable product-id order to reduce deadlocks between concurrent sales.
  for(SaleDtos.LineRequest line:sorted){
   if(!uniqueProducts.add(line.productId()))
    throw new BusinessRuleException("No repita el mismo producto en varias líneas de una venta; consolide las cantidades.");
   Product product=products.findForUpdate(line.productId())
    .orElseThrow(()->new ResourceNotFoundException("Producto",line.productId()));
   boolean subscriptionInvoice=request.channel()==SaleChannel.SUBSCRIPTION;
   // A contracted subscription keeps billing even if the service is later withdrawn from the catalog.
   if(!subscriptionInvoice&&(!product.isActive()||!product.getCategory().isActive()))
    throw new BusinessRuleException("El producto "+product.getSku()+" está inactivo o su categoría no está publicada.");
   if(product.isSubscription()!=subscriptionInvoice)
    throw new BusinessRuleException(subscriptionInvoice?"Solo los servicios por suscripción se facturan por este canal."
     :"El servicio "+product.getName()+" se cobra por suscripción; regístrelo en Suscripciones.");
   if(saleCurrency==null)saleCurrency=product.getCurrency();
   else if(!saleCurrency.equals(product.getCurrency()))
    throw new BusinessRuleException("No se pueden combinar productos con monedas diferentes en una venta.");

   BigDecimal quantity=line.quantity().setScale(3,RoundingMode.UNNECESSARY);
   if(product.getKind()!=ProductKind.STOCKED_GOOD){
    // Services and goods without stock control are invoiced without touching inventory or cost layers.
    BigDecimal unitPrice=line.unitPrice()!=null?line.unitPrice().setScale(2,RoundingMode.UNNECESSARY):product.getSalePrice().setScale(2,RoundingMode.HALF_UP);
    BigDecimal lineTotal=unitPrice.multiply(quantity).setScale(2,RoundingMode.HALF_UP);
    subtotal=subtotal.add(lineTotal).setScale(2,RoundingMode.HALF_UP);
    lines.add(new CalculatedLine(product,null,null,quantity,unitPrice,lineTotal));
    continue;
   }
   StockBalance balance=balances.findForUpdate(product.getId(),warehouse.getId())
    .orElseThrow(()->new BusinessRuleException("No existe saldo de inventario para "+product.getSku()+" en el almacén."));
   if(!balance.getLocation().isActive())
    throw new BusinessRuleException("La ubicación de inventario para "+product.getSku()+" está inactiva.");
   UnitOfMeasure unit=units.findById(product.getUnitOfMeasureId())
    .filter(UnitOfMeasure::isActive)
    .orElseThrow(()->new BusinessRuleException("La unidad de medida del producto "+product.getSku()+" no existe o está inactiva."));
   balance.decrease(quantity);

   // The seller may adjust the price; without one the server suggests cost plus the product markup.
   // The no-loss rule is enforced below against the exact valuation of the units that leave stock.
   BigDecimal unitPrice=line.unitPrice()!=null?line.unitPrice().setScale(2,RoundingMode.UNNECESSARY)
    :pricing.price(product,balance.getLocation(),quantity).suggestedPrice();
   BigDecimal lineTotal=unitPrice.multiply(quantity).setScale(2,RoundingMode.HALF_UP);
   subtotal=subtotal.add(lineTotal).setScale(2,RoundingMode.HALF_UP);
   lines.add(new CalculatedLine(product,balance,unit,quantity,unitPrice,lineTotal));
  }
  if(lines.isEmpty())throw new BusinessRuleException("La venta debe contener al menos una línea.");

  BusinessPartner customer=null;
  String customerName=clean(request.customerName()),customerPhone=clean(request.customerPhone());
  if(request.customerId()!=null){
   customer=partners.findForUpdateByIdAndTypeIn(request.customerId(),List.of(PartnerType.CUSTOMER,PartnerType.BOTH))
    .orElseThrow(()->new ResourceNotFoundException("Cliente",request.customerId()));
   if(!customer.isActive())throw new BusinessRuleException("El cliente está inactivo.");
   customerName=customer.getName();customerPhone=customer.getPhone();
  }

  String saleNumber="VTA-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+
   UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  Sale sale=sales.saveAndFlush(new Sale(saleNumber,customerName,customerPhone,request.channel(),warehouse,
   subtotal,BigDecimal.ZERO.setScale(2),subtotal,saleCurrency==null?"USD":saleCurrency,key,hash,actor,customer));

  Map<UUID,SaleItem> itemsByProduct=new HashMap<>();
  for(CalculatedLine line:lines){
   SaleItem item=new SaleItem(line.product(),line.quantity(),line.unitPrice(),line.lineTotal());
   sale.addItem(item);
   itemsByProduct.put(line.product().getId(),item);
  }
  // Flush the sale and its lines first so every immutable movement can reference the exact source line.
  sales.flush();

  for(CalculatedLine line:lines){
   if(line.balance()==null)continue;
   SaleItem item=itemsByProduct.get(line.product().getId());
   String movementKey="SALE-"+sha256(key).substring(0,32)+"-"+line.product().getId();
   String movementHash=sha256(sale.getId()+"|"+item.getId()+"|"+line.product().getId()+"|"+line.quantity().toPlainString());
   // The cost of goods sold comes from the valuation policy (FIFO layers or moving average), never from the catalog.
   InventoryCostingService.Valuation valuation=costing.outbound(line.product(),line.balance().getLocation(),line.quantity());
   SalePricingService.requireNoLoss(line.product(),line.unitPrice(),valuation.unitCost());
   StockMovement movement=new StockMovement(line.product(),warehouse,MovementType.SALE,line.quantity(),
    valuation.movementUnitCost(),"SALE",sale.getId(),movementKey,movementHash,
    "Salida por venta "+sale.getSaleNumber(),actor);
   movement.assignLocations(line.balance().getLocation(),null,line.unit(),sale.getId().toString(),item.getId(),valuation.method());
   costing.post(movements.save(movement),valuation);
  }
  movements.flush();

  outbox.save(new OutboxEvent("SALE",sale.getId(),"SALE_COMPLETED",
   Map.of("saleId",sale.getId().toString(),"saleNumber",sale.getSaleNumber(),"total",sale.getTotal().toPlainString(),"currency",sale.getCurrency())));
  audit.record(actor,"SALE_COMPLETED","SALE",sale.getId(),
   Map.of("saleNumber",sale.getSaleNumber(),"total",sale.getTotal().toPlainString(),"currency",sale.getCurrency(),"lines",lines.size()));

  record.markCompleted("SALE",sale.getId(),(short)201);
  idempotencyRecords.saveAndFlush(record);
  return SaleDtos.View.from(sales.findDetailedById(sale.getId()).orElseThrow());
 }

 @Transactional
 public SaleDtos.View requestCancellation(UUID id,String suppliedKey,String reason,String actor){
  requireSaleScope(id,actor);
  String key=normalizeIdempotencyKey(suppliedKey);
  if(reason==null||reason.isBlank()||reason.trim().length()>1000)throw new BusinessRuleException("Debe indicar un motivo de cancelación de hasta 1000 caracteres.");
  String cleanReason=reason.trim();
  IdempotencyRecord record=claimIdempotency("SALE_CANCEL_REQUEST",key,sha256("SALE_CANCEL_REQUEST|"+id+"|"+cleanReason));
  if(record.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED)return SaleDtos.View.from(sales.findDetailedById(record.getResourceId()).orElseThrow(()->new ConflictException("La clave de idempotencia apunta a una venta inexistente.")));
  Sale sale=sales.findForUpdateById(id).orElseThrow(()->new ResourceNotFoundException("Venta",id));
  if(sale.getStatus()!=SaleStatus.COMPLETED)throw new ConflictException("Solo una venta completada puede iniciar una solicitud de cancelación.");
  if(returnLines.existsForSale(id))throw new ConflictException("No se puede cancelar una venta que ya tiene devoluciones; registre una devolución o un ajuste separado.");
  sale.requestCancellation(cleanReason,actor);
  outbox.save(new OutboxEvent("SALE",sale.getId(),"SALE_CANCELLATION_REQUESTED",Map.of("saleId",sale.getId().toString(),"saleNumber",sale.getSaleNumber(),"reason",cleanReason,"requestedBy",actor)));
  audit.record(actor,"SALE_CANCELLATION_REQUESTED","SALE",sale.getId(),Map.of("saleNumber",sale.getSaleNumber(),"reason",cleanReason));
  record.markCompleted("SALE",sale.getId(),(short)200);idempotencyRecords.saveAndFlush(record);
  return SaleDtos.View.from(sales.findDetailedById(id).orElseThrow());
 }

 @Transactional
 public SaleDtos.View approveCancellation(UUID id,String suppliedKey,String actor){
  requireSaleScope(id,actor);
  String key=normalizeIdempotencyKey(suppliedKey);
  IdempotencyRecord record=claimIdempotency("SALE_CANCEL_APPROVE",key,sha256("SALE_CANCEL_APPROVE|"+id));
  if(record.getStatus()==IdempotencyRecord.RecordStatus.COMPLETED)return SaleDtos.View.from(sales.findDetailedById(record.getResourceId()).orElseThrow(()->new ConflictException("La clave de idempotencia apunta a una venta inexistente.")));
  Sale sale=sales.findForUpdateById(id).orElseThrow(()->new ResourceNotFoundException("Venta",id));
  if(sale.getStatus()!=SaleStatus.CANCELLATION_REQUESTED)throw new ConflictException("La venta no tiene una solicitud de cancelación pendiente.");
  if(actor!=null&&actor.equals(sale.getCancellationRequestedBy()))throw new ConflictException("Quien solicita la cancelación no puede aprobarla.");
  if(returnLines.existsForSale(id))throw new ConflictException("No se puede cancelar una venta que ya tiene devoluciones; registre una devolución o un ajuste separado.");
  List<SaleItem> items=new ArrayList<>(sale.getItems());items.sort(Comparator.comparing(i->i.getProduct().getId().toString()));
  for(SaleItem item:items){
   // Lines without a stock exit (services, goods without stock control) have nothing to put back.
   if(movements.findFirstByReferenceTypeAndReferenceIdAndSourceLineIdAndMovementType("SALE",sale.getId(),item.getId(),MovementType.SALE).isEmpty())continue;
   StockBalance balance=balances.findForUpdate(item.getProduct().getId(),sale.getWarehouse().getId()).orElseThrow(()->new BusinessRuleException("No existe saldo para reponer "+item.getSkuSnapshot()+"."));
   balance.increase(item.getQuantity());
   UnitOfMeasure unit=units.findById(item.getProduct().getUnitOfMeasureId()).orElseThrow(()->new BusinessRuleException("No existe unidad de medida para "+item.getSkuSnapshot()+"."));
   // Goods return to stock at the exact cost recorded when they were sold.
   InventoryCostingService.Valuation valuation=costing.inbound(item.getProduct(),balance.getLocation(),item.getQuantity(),soldUnitCost(sale,item,balance));
   String movementKey="SALE-CANCEL-"+sale.getId()+"-"+item.getProduct().getId();
   StockMovement movement=new StockMovement(item.getProduct(),sale.getWarehouse(),MovementType.SALE_CANCEL_IN,item.getQuantity(),valuation.movementUnitCost(),"SALE_CANCELLATION",sale.getId(),movementKey,sha256(sale.getId()+"|"+sale.getCancellationReason()+"|"+item.getId()),"Cancelación aprobada de venta "+sale.getSaleNumber()+": "+sale.getCancellationReason(),actor);
   movement.assignLocations(null,balance.getLocation(),unit,sale.getId().toString(),item.getId(),valuation.method());costing.post(movements.save(movement),valuation);
  }
  sale.approveCancellation(actor);
  outbox.save(new OutboxEvent("SALE",sale.getId(),"SALE_CANCELLED",Map.of("saleId",sale.getId().toString(),"saleNumber",sale.getSaleNumber(),"reason",sale.getCancellationReason(),"requestedBy",sale.getCancellationRequestedBy(),"approvedBy",actor)));
  audit.record(actor,"SALE_CANCELLED","SALE",sale.getId(),Map.of("saleNumber",sale.getSaleNumber(),"reason",sale.getCancellationReason(),"requestedBy",sale.getCancellationRequestedBy(),"approvedBy",actor,"lines",items.size()));
  record.markCompleted("SALE",sale.getId(),(short)200);idempotencyRecords.saveAndFlush(record);
  return SaleDtos.View.from(sales.findDetailedById(id).orElseThrow());
 }

 /** Unit cost assigned to the original SALE movement of a sale line; falls back to the current valued cost. */
 public java.math.BigDecimal soldUnitCost(Sale sale,SaleItem item,StockBalance balance){
  return movements.findFirstByReferenceTypeAndReferenceIdAndSourceLineIdAndMovementType("SALE",sale.getId(),item.getId(),MovementType.SALE)
   .map(m->costing.valuedUnitCost(m.getId())).filter(java.util.Objects::nonNull)
   .orElseGet(()->costing.currentUnitCost(item.getProduct(),balance.getLocation()));
 }
 private IdempotencyRecord claimIdempotency(String scope,String key,String hash){
  int claimed=idempotencyRecords.insertIfAbsent(UUID.randomUUID(),scope,key,hash);
  IdempotencyRecord record=idempotencyRecords.findForUpdate(scope,key).orElseThrow(()->new IllegalStateException("No se pudo reservar la clave de idempotencia."));
  if(!hash.equals(record.getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con una solicitud distinta.");
  if(claimed==0&&record.getStatus()!=IdempotencyRecord.RecordStatus.COMPLETED)throw new ConflictException("Ya existe una operación con esta clave de idempotencia y aún no puede reproducirse.");
  return record;
 }

 private String normalizeIdempotencyKey(String key){
  if(key==null||key.isBlank()||key.trim().length()>120)
   throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");
  return key.trim();
 }
 private String requestHash(SaleDtos.CreateRequest request){
  if(request==null||request.items()==null||request.items().isEmpty())
   throw new BusinessRuleException("La venta debe contener al menos una línea.");
  try{
   List<CanonicalLine> canonicalLines=request.items().stream()
    .map(line->new CanonicalLine(line.productId(),line.quantity().setScale(3,RoundingMode.UNNECESSARY).toPlainString(),
     line.unitPrice()==null?null:line.unitPrice().setScale(2,RoundingMode.UNNECESSARY).toPlainString()))
    .sorted(Comparator.comparing(line->line.productId().toString())).toList();
   CanonicalSaleRequest canonical=new CanonicalSaleRequest(request.warehouseId(),clean(request.customerName()),
    clean(request.customerPhone()),request.channel(),request.customerId(),canonicalLines);
   return sha256(mapper.writeValueAsString(canonical));
  }catch(JsonProcessingException ex){
   throw new BusinessRuleException("No se pudo validar el contenido de la solicitud.");
  }
 }
 private String legacyRequestHash(SaleDtos.CreateRequest request){
  try{return sha256(mapper.writeValueAsString(request));}
  catch(JsonProcessingException ex){throw new BusinessRuleException("No se pudo validar el contenido de la solicitud.");}
 }
 private String sha256(String value){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception ex){throw new IllegalStateException("No se pudo calcular huella de idempotencia.",ex);}
 }
 private String clean(String value){return value==null||value.isBlank()?null:value.trim();}
 private void requireSaleScope(UUID id,String actor){
  Sale sale=sales.findById(id).orElseThrow(()->new ResourceNotFoundException("Venta",id));
  scopes.requireWarehouseAccess(actor,sale.getWarehouse().getId());
 }
}
