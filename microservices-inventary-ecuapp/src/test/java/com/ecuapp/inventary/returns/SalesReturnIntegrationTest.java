package com.ecuapp.inventary.returns;

import com.ecuapp.inventary.catalog.*;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.inventory.*;
import com.ecuapp.inventary.sales.*;
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
class SalesReturnIntegrationTest {
 @Autowired CategoryRepository categories;
 @Autowired ProductRepository products;
 @Autowired WarehouseRepository warehouses;
 @Autowired StockBalanceRepository balances;
 @Autowired SaleService sales;
 @Autowired SalesReturnService returns;

 @Test void returnCannotExceedSoldQuantityAndIsIdempotent(){
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Category category=categories.save(new Category("Return category "+suffix,"return-category-"+suffix,null));
  Product product=products.save(new Product("RET-"+suffix,"Return product",null,category,new BigDecimal("2.00"),new BigDecimal("5.00"),"USD",null));
  Warehouse warehouse=warehouses.save(new Warehouse("RET-"+suffix,"Return warehouse",null));
  StockBalance stock=balances.save(new StockBalance(product,warehouse));stock.increase(new BigDecimal("5.000"));balances.saveAndFlush(stock);
  SaleDtos.CreateRequest saleRequest=new SaleDtos.CreateRequest(warehouse.getId(),"Return test",null,SaleChannel.POS,List.of(new SaleDtos.LineRequest(product.getId(),new BigDecimal("2.000"))));
  SaleDtos.View sale=sales.create("return-sale-"+suffix,saleRequest,"test");
  UUID saleItemId=sale.items().get(0).id();

  SalesReturnDtos.CreateRequest returnRequest=new SalesReturnDtos.CreateRequest(sale.id(),"Customer returned one unit",
    List.of(new SalesReturnDtos.LineRequest(saleItemId,new BigDecimal("1.000"),ReturnDisposition.RESTOCK)));
  SalesReturnDtos.View first=returns.create("return-key-"+suffix,returnRequest,"test");
  SalesReturnDtos.View retry=returns.create("return-key-"+suffix,returnRequest,"test");
  assertEquals(first.id(),retry.id());
  assertEquals(ReturnStatus.POSTED,first.status());
  assertEquals(new BigDecimal("4.000"),balances.findByProductIdAndWarehouseId(product.getId(),warehouse.getId()).orElseThrow().getOnHand());

  SalesReturnDtos.CreateRequest excessive=new SalesReturnDtos.CreateRequest(sale.id(),"Excess quantity",
    List.of(new SalesReturnDtos.LineRequest(saleItemId,new BigDecimal("2.000"),ReturnDisposition.RESTOCK)));
  assertThrows(BusinessRuleException.class,()->returns.create("return-excess-"+suffix,excessive,"test"));
 }
}
