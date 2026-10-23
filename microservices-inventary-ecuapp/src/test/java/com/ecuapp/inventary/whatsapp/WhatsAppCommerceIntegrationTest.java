package com.ecuapp.inventary.whatsapp;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.warehouse.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class WhatsAppCommerceIntegrationTest {
 @Autowired CategoryRepository categories;
 @Autowired ProductRepository products;
 @Autowired WarehouseRepository warehouses;
 @Autowired StockBalanceRepository balances;
 @Autowired WhatsAppCommerceService commerce;

 @Test void confirmedOrderReservesStockAndCancellationReleasesItIdempotently(){
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Category category=categories.save(new Category("WA category "+suffix,"wa-category-"+suffix,null));
  Product product=products.save(new Product("WA-"+suffix,"WA product",null,category,new BigDecimal("2.00"),new BigDecimal("5.00"),"USD",null));
  Warehouse warehouse=warehouses.save(new Warehouse("WA-"+suffix,"WA warehouse",null));
  StockBalance stock=balances.save(new StockBalance(product,warehouse));stock.increase(new BigDecimal("5.000"));balances.saveAndFlush(stock);

  WhatsAppDtos.CartView cart=commerce.createCart("1555"+suffix.replace("-",""),"test");
  commerce.setItem(cart.id(),product.getId(),new WhatsAppDtos.CartItemRequest(new BigDecimal("2.000")),"test");
  WhatsAppDtos.QuoteView quote=commerce.createQuote(cart.id(),"quote-"+suffix,new WhatsAppDtos.QuoteRequest(warehouse.getId()),"test");
  WhatsAppDtos.CreateOrderRequest request=new WhatsAppDtos.CreateOrderRequest(quote.id(),true);
  WhatsAppDtos.OrderView order=commerce.createOrder("order-"+suffix,request,"test");
  WhatsAppDtos.OrderView retry=commerce.createOrder("order-"+suffix,request,"test");

  assertEquals(order.id(),retry.id());
  assertEquals(WhatsAppOrderStatus.AWAITING_PAYMENT,order.status());
  StockBalance reserved=balances.findByProductIdAndWarehouseId(product.getId(),warehouse.getId()).orElseThrow();
  assertEquals(new BigDecimal("2.000"),reserved.getReserved());
  assertEquals(new BigDecimal("3.000"),reserved.available());

  WhatsAppDtos.OrderView cancelled=commerce.cancelOrder(order.id(),"test");
  assertEquals(WhatsAppOrderStatus.CANCELLED,cancelled.status());
  StockBalance released=balances.findByProductIdAndWarehouseId(product.getId(),warehouse.getId()).orElseThrow();
  assertEquals(new BigDecimal("0.000"),released.getReserved());
  assertEquals(new BigDecimal("5.000"),released.available());
 }
}
