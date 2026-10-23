package com.ecuapp.inventary.sales;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.warehouse.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SaleCancellationIntegrationTest {
 @Autowired CategoryRepository categories;
 @Autowired ProductRepository products;
 @Autowired WarehouseRepository warehouses;
 @Autowired StockBalanceRepository balances;
 @Autowired SaleService sales;

 @Test void cancellationRestoresStockAndCanBeRetried(){
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Category category=categories.save(new Category("Cancel category "+suffix,"cancel-category-"+suffix,null));
  Product product=products.save(new Product("CAN-"+suffix,"Cancel product",null,category,new BigDecimal("2.00"),new BigDecimal("5.00"),"USD",null));
  Warehouse warehouse=warehouses.save(new Warehouse("CAN-"+suffix,"Cancel warehouse",null));
  StockBalance stock=balances.save(new StockBalance(product,warehouse));stock.increase(new BigDecimal("5.000"));balances.saveAndFlush(stock);
  SaleDtos.CreateRequest request=new SaleDtos.CreateRequest(warehouse.getId(),"Test customer",null,SaleChannel.POS,List.of(new SaleDtos.LineRequest(product.getId(),new BigDecimal("2.000"))));
  SaleDtos.View sale=sales.create("sale-"+suffix,request,"test");
  assertEquals(new BigDecimal("3.000"),balances.findByProductIdAndWarehouseId(product.getId(),warehouse.getId()).orElseThrow().getOnHand());
  SaleDtos.View requested=sales.requestCancellation(sale.id(),"cancel-request-"+suffix,"Customer request","seller");
  assertEquals(SaleStatus.CANCELLATION_REQUESTED,requested.status());
  assertEquals(new BigDecimal("3.000"),balances.findByProductIdAndWarehouseId(product.getId(),warehouse.getId()).orElseThrow().getOnHand());
  SaleDtos.View cancelled=sales.approveCancellation(sale.id(),"cancel-approve-"+suffix,"manager");
  SaleDtos.View retry=sales.approveCancellation(sale.id(),"cancel-approve-"+suffix,"manager");
  assertEquals(SaleStatus.CANCELLED,cancelled.status());assertEquals(cancelled.id(),retry.id());
  assertEquals(new BigDecimal("5.000"),balances.findByProductIdAndWarehouseId(product.getId(),warehouse.getId()).orElseThrow().getOnHand());
 }
 @Test void requesterCannotApproveOwnCancellation(){
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Category category=categories.save(new Category("Segregation category "+suffix,"segregation-category-"+suffix,null));
  Product product=products.save(new Product("SEG-"+suffix,"Segregation product",null,category,new BigDecimal("2.00"),new BigDecimal("5.00"),"USD",null));
  Warehouse warehouse=warehouses.save(new Warehouse("SEG-"+suffix,"Segregation warehouse",null));
  StockBalance stock=balances.save(new StockBalance(product,warehouse));stock.increase(new BigDecimal("3.000"));balances.saveAndFlush(stock);
  SaleDtos.CreateRequest request=new SaleDtos.CreateRequest(warehouse.getId(),"Test customer",null,SaleChannel.POS,List.of(new SaleDtos.LineRequest(product.getId(),new BigDecimal("1.000"))));
  SaleDtos.View sale=sales.create("seg-sale-"+suffix,request,"seller");
  sales.requestCancellation(sale.id(),"seg-request-"+suffix,"Customer request","seller");
  assertThrows(com.ecuapp.inventary.common.ConflictException.class,()->sales.approveCancellation(sale.id(),"seg-approve-"+suffix,"seller"));
 }

}
