package com.ecuapp.inventary.whatsapp;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.common.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.inventory.MovementType;
import com.ecuapp.inventary.inventory.StockMovement;
import com.ecuapp.inventary.inventory.StockMovementRepository;
import com.ecuapp.inventary.sales.OutboxEvent;
import com.ecuapp.inventary.sales.OutboxEventRepository;
import com.ecuapp.inventary.warehouse.*;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class WhatsAppCommerceService {
 private final WhatsAppCartRepository carts;private final WhatsAppCartItemRepository cartItems;private final WhatsAppQuoteRepository quotes;private final WhatsAppOrderRepository orders;
 private final ProductRepository products;private final WarehouseRepository warehouses;private final StockBalanceRepository balances;private final StockMovementRepository movements;
 private final OutboxEventRepository outbox;private final AuditService audit;private final int quoteTtlMinutes;private final int orderTtlMinutes;
 public WhatsAppCommerceService(WhatsAppCartRepository carts,WhatsAppCartItemRepository cartItems,WhatsAppQuoteRepository quotes,WhatsAppOrderRepository orders,
  ProductRepository products,WarehouseRepository warehouses,StockBalanceRepository balances,StockMovementRepository movements,OutboxEventRepository outbox,AuditService audit,
  @Value("${app.integrations.whatsapp.quote-ttl-minutes:10}") int quoteTtlMinutes,
  @Value("${app.integrations.whatsapp.order-ttl-minutes:20}") int orderTtlMinutes){
  this.carts=carts;this.cartItems=cartItems;this.quotes=quotes;this.orders=orders;this.products=products;this.warehouses=warehouses;this.balances=balances;this.movements=movements;
  this.outbox=outbox;this.audit=audit;this.quoteTtlMinutes=Math.max(1,Math.min(quoteTtlMinutes,120));this.orderTtlMinutes=Math.max(1,Math.min(orderTtlMinutes,240));
 }
 @Transactional public WhatsAppDtos.CartView createCart(String customerRef,String actor){
  String reference=normalizeCustomerRef(customerRef);
  Optional<WhatsAppCart> existing=carts.findFirstByCustomerRefAndStatusInOrderByCreatedAtDesc(reference,List.of(CartStatus.OPEN,CartStatus.QUOTED));
  if(existing.isPresent()){
   WhatsAppCart current=carts.findForUpdate(existing.get().getId()).orElseThrow();
   if(current.getExpiresAt().isAfter(Instant.now()))return cartView(carts.findDetailedById(current.getId()).orElseThrow());
   current.expire();carts.saveAndFlush(current);
  }
  WhatsAppCart cart=carts.saveAndFlush(new WhatsAppCart(reference));
  audit.record(actor,"WHATSAPP_CART_CREATED","WHATSAPP_CART",cart.getId(),Map.of("customerRef",reference));
  return cartView(carts.findDetailedById(cart.getId()).orElseThrow());
 }
 @Transactional(readOnly=true) public WhatsAppDtos.CartView getCart(UUID cartId){
  return cartView(carts.findDetailedById(cartId).orElseThrow(()->new ResourceNotFoundException("Carrito",cartId)));
 }
 @Transactional public WhatsAppDtos.CartView setItem(UUID cartId,UUID productId,WhatsAppDtos.CartItemRequest request,String actor){
  WhatsAppCart cart=requireEditableCart(cartId);
  Product product=products.findById(productId).orElseThrow(()->new ResourceNotFoundException("Producto",productId));
  if(!product.isActive()||!product.getCategory().isActive())throw new BusinessRuleException("El producto no está disponible en el catálogo público.");
  BigDecimal quantity=request.quantity().setScale(3,RoundingMode.UNNECESSARY);
  if(cart.getStatus()==CartStatus.QUOTED){quotes.expireActiveForCart(cartId);cart.reopen();}
  WhatsAppCartItem item=cart.getItems().stream().filter(i->i.getProduct().getId().equals(productId)).findFirst().orElse(null);
  if(item==null)cart.addItem(new WhatsAppCartItem(product,quantity));else item.setQuantity(quantity);
  audit.record(actor,"WHATSAPP_CART_ITEM_SET","WHATSAPP_CART",cartId,Map.of("productId",productId.toString(),"quantity",quantity.toPlainString()));
  return cartView(carts.findDetailedById(cartId).orElseThrow());
 }
 @Transactional public WhatsAppDtos.CartView removeItem(UUID cartId,UUID productId,String actor){
  WhatsAppCart cart=requireEditableCart(cartId);
  if(cart.getStatus()==CartStatus.QUOTED){quotes.expireActiveForCart(cartId);cart.reopen();}
  WhatsAppCartItem item=cart.getItems().stream().filter(i->i.getProduct().getId().equals(productId)).findFirst().orElseThrow(()->new ResourceNotFoundException("Producto en carrito",productId));
  cart.removeItem(item);
  audit.record(actor,"WHATSAPP_CART_ITEM_REMOVED","WHATSAPP_CART",cartId,Map.of("productId",productId.toString()));
  return cartView(carts.findDetailedById(cartId).orElseThrow());
 }
 @Transactional public WhatsAppDtos.QuoteView createQuote(UUID cartId,String key,WhatsAppDtos.QuoteRequest request,String actor){
  requireIdempotencyKey(key);
  String requestHash=hash(cartId+"|"+request.warehouseId());
  Optional<WhatsAppQuote> previous=quotes.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!requestHash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra cotización.");
   return WhatsAppDtos.QuoteView.from(quotes.findDetailedById(previous.get().getId()).orElseThrow());
  }
  WhatsAppCart cart=carts.findForUpdate(cartId).orElseThrow(()->new ResourceNotFoundException("Carrito",cartId));
  previous=quotes.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!requestHash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra cotización.");
   return WhatsAppDtos.QuoteView.from(quotes.findDetailedById(previous.get().getId()).orElseThrow());
  }
  assertCartQuoteable(cart);
  Warehouse warehouse=warehouses.findById(request.warehouseId()).orElseThrow(()->new ResourceNotFoundException("Almacén",request.warehouseId()));
  if(!warehouse.isActive())throw new BusinessRuleException("El almacén seleccionado está inactivo.");
  if(cart.getItems().isEmpty())throw new BusinessRuleException("No se puede cotizar un carrito vacío.");
  List<WhatsAppCartItem> cartLines=new ArrayList<>(cart.getItems());cartLines.sort(Comparator.comparing(i->i.getProduct().getId().toString()));
  String currency=null;BigDecimal subtotal=BigDecimal.ZERO.setScale(2);List<QuoteCalc> calculated=new ArrayList<>();
  for(WhatsAppCartItem item:cartLines){
   Product p=item.getProduct();
   if(!p.isActive()||!p.getCategory().isActive())throw new ConflictException("El producto "+p.getSku()+" ya no está disponible; actualice el carrito.");
   if(currency==null)currency=p.getCurrency();else if(!currency.equals(p.getCurrency()))throw new BusinessRuleException("No se pueden cotizar productos con monedas diferentes en el mismo carrito.");
   BigDecimal quantity=item.getQuantity().setScale(3,RoundingMode.UNNECESSARY);
   StockBalance stock=balances.findByProductIdAndWarehouseId(p.getId(),warehouse.getId()).orElseThrow(()->new BusinessRuleException("El producto "+p.getSku()+" no tiene saldo en el almacén seleccionado."));
   if(stock.available().compareTo(quantity)<0)throw new BusinessRuleException("Existencias insuficientes para "+p.getSku()+". Disponible: "+stock.available().toPlainString());
   BigDecimal price=p.getSalePrice().setScale(2,RoundingMode.UNNECESSARY);
   BigDecimal lineTotal=price.multiply(quantity).setScale(2,RoundingMode.HALF_UP);subtotal=subtotal.add(lineTotal).setScale(2,RoundingMode.HALF_UP);
   calculated.add(new QuoteCalc(p,quantity,price,lineTotal));
  }
  quotes.expireActiveForCart(cartId);
  Instant expiry=Instant.now().plus(quoteTtlMinutes,ChronoUnit.MINUTES);
  String number="COT-WA-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  WhatsAppQuote quote=quotes.saveAndFlush(new WhatsAppQuote(number,cart,warehouse,currency==null?"USD":currency,subtotal,BigDecimal.ZERO.setScale(2),subtotal,key,requestHash,expiry));
  for(QuoteCalc item:calculated)quote.addItem(new WhatsAppQuoteItem(item.product(),item.quantity(),item.unitPrice(),item.lineTotal()));
  cart.markQuoted();
  carts.save(cart);quotes.save(quote);
  audit.record(actor,"WHATSAPP_QUOTE_CREATED","WHATSAPP_QUOTE",quote.getId(),Map.of("quoteNumber",number,"cartId",cartId.toString(),"total",subtotal.toPlainString(),"currency",quote.getCurrency()));
  return WhatsAppDtos.QuoteView.from(quotes.findDetailedById(quote.getId()).orElseThrow());
 }
 @Transactional(readOnly=true) public WhatsAppDtos.QuoteView getQuote(UUID quoteId){
  return WhatsAppDtos.QuoteView.from(quotes.findDetailedById(quoteId).orElseThrow(()->new ResourceNotFoundException("Cotización",quoteId)));
 }
 @Transactional public WhatsAppDtos.OrderView createOrder(String key,WhatsAppDtos.CreateOrderRequest request,String actor){
  requireIdempotencyKey(key);
  if(!request.consentConfirmed())throw new BusinessRuleException("Se requiere confirmación explícita de la orden por parte del cliente.");
  String requestHash=hash(request.quoteId()+"|CONSENT_CONFIRMED");
  Optional<WhatsAppOrder> previous=orders.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!requestHash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra orden.");
   return WhatsAppDtos.OrderView.from(orders.findDetailedById(previous.get().getId()).orElseThrow());
  }
  WhatsAppQuote quote=quotes.findForUpdate(request.quoteId()).orElseThrow(()->new ResourceNotFoundException("Cotización",request.quoteId()));
  previous=orders.findByIdempotencyKey(key);
  if(previous.isPresent()){
   if(!requestHash.equals(previous.get().getRequestHash()))throw new ConflictException("La clave Idempotency-Key ya se usó con otra orden.");
   return WhatsAppDtos.OrderView.from(orders.findDetailedById(previous.get().getId()).orElseThrow());
  }
  if(quote.getStatus()!=QuoteStatus.ACTIVE)throw new ConflictException("La cotización no está activa.");
  if(!quote.getExpiresAt().isAfter(Instant.now())){quote.expire();throw new ConflictException("La cotización venció; genere una nueva cotización.");}
  WhatsAppCart cart=carts.findForUpdate(quote.getCart().getId()).orElseThrow(()->new ResourceNotFoundException("Carrito",quote.getCart().getId()));
  if(cart.getStatus()!=CartStatus.QUOTED)throw new ConflictException("El carrito cambió después de la cotización; cotice nuevamente.");
  Map<UUID,WhatsAppCartItem> cartItemsByProduct=new HashMap<>();
  for(WhatsAppCartItem item:cart.getItems())cartItemsByProduct.put(item.getProduct().getId(),item);
  if(cartItemsByProduct.size()!=quote.getItems().size())throw new ConflictException("El carrito cambió después de la cotización; cotice nuevamente.");
  List<WhatsAppQuoteItem> quoteLines=new ArrayList<>(quote.getItems());quoteLines.sort(Comparator.comparing(i->i.getProduct().getId().toString()));
  for(WhatsAppQuoteItem line:quoteLines){
   Product product=line.getProduct();WhatsAppCartItem cartItem=cartItemsByProduct.get(product.getId());
   if(cartItem==null||cartItem.getQuantity().compareTo(line.getQuantity())!=0)throw new ConflictException("Las cantidades cambiaron; cotice nuevamente.");
   if(!product.isActive()||!product.getCategory().isActive())throw new ConflictException("El producto "+line.getSkuSnapshot()+" ya no está disponible.");
   if(product.getSalePrice().compareTo(line.getUnitPrice())!=0)throw new ConflictException("Cambió el precio de "+line.getSkuSnapshot()+"; genere una nueva cotización.");
  }
  // Reservar en orden determinista y con bloqueo SQL para evitar sobreventa en solicitudes concurrentes.
  for(WhatsAppQuoteItem line:quoteLines){
   StockBalance balance=balances.findForUpdate(line.getProduct().getId(),quote.getWarehouse().getId()).orElseThrow(()->new BusinessRuleException("No hay saldo para "+line.getSkuSnapshot()));
   balance.reserve(line.getQuantity());
  }
  String number="WA-"+LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  WhatsAppOrder order=orders.saveAndFlush(new WhatsAppOrder(number,cart,quote,quote.getWarehouse(),cart.getCustomerRef(),key,requestHash,Instant.now().plus(orderTtlMinutes,ChronoUnit.MINUTES)));
  for(WhatsAppQuoteItem line:quoteLines)order.addItem(new WhatsAppOrderItem(line));
  orders.save(order);
  for(WhatsAppOrderItem line:order.getItems()){
   String mkey="WARS-"+order.getId()+"-"+line.getProduct().getId();
   movements.save(new StockMovement(line.getProduct(),order.getWarehouse(),MovementType.RESERVE,line.getQuantity(),line.getProduct().getCostPrice(),
    "WHATSAPP_ORDER",order.getId(),mkey,requestHash,"Reserva de inventario por orden "+number,"whatsapp-integration"));
  }
  quote.markOrdered();cart.markOrdered();carts.save(cart);quotes.save(quote);
  outbox.save(new OutboxEvent("WHATSAPP_ORDER",order.getId(),"WHATSAPP_ORDER_CREATED",Map.of("orderId",order.getId().toString(),"orderNumber",number,"total",order.getTotal().toPlainString(),"currency",order.getCurrency())));
  audit.record(actor,"WHATSAPP_ORDER_CREATED","WHATSAPP_ORDER",order.getId(),Map.of("orderNumber",number,"quoteId",quote.getId().toString(),"total",order.getTotal().toPlainString()));
  return WhatsAppDtos.OrderView.from(orders.findDetailedById(order.getId()).orElseThrow());
 }
 @Transactional(readOnly=true) public WhatsAppDtos.OrderView getOrder(UUID orderId){
  return WhatsAppDtos.OrderView.from(orders.findDetailedById(orderId).orElseThrow(()->new ResourceNotFoundException("Orden WhatsApp",orderId)));
 }
 @Transactional public WhatsAppDtos.OrderView cancelOrder(UUID orderId,String actor){
  WhatsAppOrder order=orders.findForUpdate(orderId).orElseThrow(()->new ResourceNotFoundException("Orden WhatsApp",orderId));
  if(order.getStatus()==WhatsAppOrderStatus.CANCELLED||order.getStatus()==WhatsAppOrderStatus.EXPIRED)return WhatsAppDtos.OrderView.from(orders.findDetailedById(orderId).orElseThrow());
  if(order.getStatus()!=WhatsAppOrderStatus.AWAITING_PAYMENT)throw new ConflictException("La orden ya no puede cancelarse en este estado.");
  releaseReservations(order);
  order.cancel();order.getCart().reopenAfterOrderCancelled();
  outbox.save(new OutboxEvent("WHATSAPP_ORDER",order.getId(),"WHATSAPP_ORDER_CANCELLED",Map.of("orderId",order.getId().toString(),"orderNumber",order.getOrderNumber())));
  audit.record(actor,"WHATSAPP_ORDER_CANCELLED","WHATSAPP_ORDER",order.getId(),Map.of("orderNumber",order.getOrderNumber()));
  return WhatsAppDtos.OrderView.from(orders.findDetailedById(orderId).orElseThrow());
 }
 @Transactional public int expireOrders(){
  Instant now=Instant.now();List<WhatsAppOrder> due=orders.findByStatusAndExpiresAtBefore(WhatsAppOrderStatus.AWAITING_PAYMENT,now);int expired=0;
  for(WhatsAppOrder item:due){
   WhatsAppOrder order=orders.findForUpdate(item.getId()).orElse(null);
   if(order==null||order.getStatus()!=WhatsAppOrderStatus.AWAITING_PAYMENT||order.getExpiresAt().isAfter(now))continue;
   releaseReservations(order);order.expire();order.getCart().reopenAfterOrderCancelled();
   outbox.save(new OutboxEvent("WHATSAPP_ORDER",order.getId(),"WHATSAPP_ORDER_EXPIRED",Map.of("orderId",order.getId().toString(),"orderNumber",order.getOrderNumber())));
   audit.record("system","WHATSAPP_ORDER_EXPIRED","WHATSAPP_ORDER",order.getId(),Map.of("orderNumber",order.getOrderNumber()));
   expired++;
  }
  for(WhatsAppQuote quote:quotes.findByStatusAndExpiresAtBefore(QuoteStatus.ACTIVE,now))quote.expire();
  return expired;
 }
 private void releaseReservations(WhatsAppOrder order){
  List<WhatsAppOrderItem> lines=new ArrayList<>(order.getItems());lines.sort(Comparator.comparing(i->i.getProduct().getId().toString()));
  for(WhatsAppOrderItem line:lines){
   StockBalance balance=balances.findForUpdate(line.getProduct().getId(),order.getWarehouse().getId()).orElseThrow(()->new IllegalStateException("Falta saldo para liberar una reserva de inventario."));
   balance.releaseReservation(line.getQuantity());
   String mkey="WARR-"+order.getId()+"-"+line.getProduct().getId();
   movements.save(new StockMovement(line.getProduct(),order.getWarehouse(),MovementType.RELEASE,line.getQuantity(),line.getProduct().getCostPrice(),
    "WHATSAPP_ORDER",order.getId(),mkey,order.getRequestHash(),"Liberación de reserva de orden "+order.getOrderNumber(),"whatsapp-integration"));
  }
 }
 private WhatsAppCart requireEditableCart(UUID cartId){
  WhatsAppCart cart=carts.findForUpdate(cartId).orElseThrow(()->new ResourceNotFoundException("Carrito",cartId));
  if(cart.getExpiresAt().isBefore(Instant.now())){cart.expire();throw new ConflictException("El carrito venció; solicite uno nuevo.");}
  if(cart.getStatus()!=CartStatus.OPEN&&cart.getStatus()!=CartStatus.QUOTED)throw new ConflictException("El carrito no se puede editar en su estado actual.");
  return cart;
 }
 private void assertCartQuoteable(WhatsAppCart cart){
  if(cart.getExpiresAt().isBefore(Instant.now()))throw new ConflictException("El carrito venció; solicite uno nuevo.");
  if(cart.getStatus()!=CartStatus.OPEN&&cart.getStatus()!=CartStatus.QUOTED)throw new ConflictException("El carrito no se puede cotizar en su estado actual.");
 }
 private WhatsAppDtos.CartView cartView(WhatsAppCart cart){
  List<UUID> ids=cart.getItems().stream().map(i->i.getProduct().getId()).toList();
  Set<UUID> available=ids.isEmpty()?Set.of():balances.findProductIdsWithAvailableStock(ids);
  List<WhatsAppDtos.CartLineView> lines=cart.getItems().stream().map(i->{
   Product p=i.getProduct();return new WhatsAppDtos.CartLineView(p.getId(),p.getSku(),p.getName(),p.getDescription(),i.getQuantity(),p.getSalePrice(),p.getCurrency(),p.getImageUrl(),p.isActive()&&p.getCategory().isActive()&&available.contains(p.getId()));
  }).toList();
  return WhatsAppDtos.CartView.from(cart,lines);
 }
 private String normalizeCustomerRef(String value){return value.trim().replaceAll("\\s+","");}
 private void requireIdempotencyKey(String key){if(key==null||key.isBlank()||key.length()>120)throw new BusinessRuleException("El header Idempotency-Key es obligatorio y debe tener hasta 120 caracteres.");}
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("No se pudo calcular huella idempotente.",ex);}}
 private record QuoteCalc(Product product,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
}
